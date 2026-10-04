#!/usr/bin/env python3
"""Record staging activation and Android validation for one main candidate."""

from __future__ import annotations

import argparse
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Optional, Sequence

try:
    from . import production_delivery as delivery
    from .release_image_catalog import (
        load_release_image_catalog, parse_image_references, validate_image_references,
    )
    from .verify_public_services import STAGING_ENDPOINTS, STAGING_GRPC_ENDPOINT, main as verify_public
except ImportError:  # Direct invocation from the workflow.
    import production_delivery as delivery
    from release_image_catalog import (
        load_release_image_catalog, parse_image_references, validate_image_references,
    )
    from verify_public_services import STAGING_ENDPOINTS, STAGING_GRPC_ENDPOINT, main as verify_public


def new_report(candidate: str) -> dict:
    delivery._validate_release_sha(candidate)
    return {
        "schema_version": 1,
        "kind": "staging-validation",
        "candidate_sha": candidate,
        "environment": "staging",
        "status": "failed",
        "promotion_eligible": False,
        "rehearsal": {"status": "not_run"},
        "checks": {},
        "diagnostics": [],
    }


def write_report(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_status(client: delivery.ForcedCommandClient) -> Optional[dict]:
    result = client.run(("release-status",))
    if result.returncode and result.stderr == delivery.BOOTSTRAP_DIAGNOSTIC:
        return None
    if result.returncode:
        delivery._command_failure("release-status", result, sys.stderr)
        raise ValueError("unable to observe Active Release")
    value = json.loads(result.stdout)
    sha = delivery._validate_release_sha(value["release_sha"])
    activation = delivery._validate_run_number(value["activation_number"])
    images = validate_image_references(value["images"])
    if type(value["healthy"]) is not bool or not isinstance(value["services"], list):
        raise ValueError("invalid service status")
    services = []
    for service in value["services"]:
        entry = {}
        for key in ("service", "state", "health"):
            field = service[key]
            if not isinstance(field, str) or not re.fullmatch(r"[a-zA-Z0-9_. -]{0,100}", field):
                raise ValueError("invalid service status field")
            entry[key] = field
        services.append(entry)
    return {
        "release_sha": sha,
        "activation_number": activation,
        "images": images,
        "healthy": value["healthy"],
        "services": services,
    }


def same_activation(observed: Optional[dict], report: dict) -> bool:
    return observed is not None and all((
        observed["release_sha"] == report["candidate_sha"],
        observed["activation_number"] == report.get("activation_number"),
        observed["images"] == report.get("expected_images"),
    ))


def activate_candidate(
    report: dict,
    workflow_run_id: int,
    compose: Path,
    environment: Path,
    client: delivery.ForcedCommandClient,
    *,
    image_resolver: delivery.ImageResolver = delivery.resolve_release_images,
    public_verifier: delivery.PublicVerifier = lambda: verify_public(("--environment", "staging")),
) -> int:
    """Keep the baseline before activation, then verify the exact running candidate."""
    baseline = read_status(client)
    report["baseline"] = baseline
    report["workflow_run_id"] = delivery._validate_run_number(workflow_run_id)
    # Use exactly the next host counter. An intervening activation then causes
    # the existing replay check to reject us instead of reporting a stale baseline.
    activation = baseline["activation_number"] + 1 if baseline else 1
    report["activation_number"] = activation
    report["checks"]["activation"] = False
    report["checks"]["public_services"] = False

    def resolve(changed: str, sha: str, current: Optional[str]) -> str:
        references = image_resolver(changed, sha, current)
        report["expected_images"] = parse_image_references(references)
        return references

    def verify() -> int:
        result = public_verifier()
        report["checks"]["public_services"] = result == 0
        return result

    services = json.dumps([image.service for image in load_release_image_catalog()])
    result = delivery.deploy_release(
        report["candidate_sha"], activation, services, compose, environment,
        client, image_resolver=resolve, public_verifier=verify,
        stderr=sys.stderr,
    )
    report["checks"]["activation"] = result == 0
    observed = read_status(client)
    report["after_activation"] = observed
    report["checks"]["release_identity"] = same_activation(observed, report)
    report["checks"]["service_health"] = bool(observed and observed["healthy"])
    report["public_endpoints"] = [*STAGING_ENDPOINTS, STAGING_GRPC_ENDPOINT]
    if result or not all(report["checks"].values()):
        return 1
    report["status"] = "awaiting_android"
    return 0


def finish_validation(
    report: dict,
    candidate: str,
    android_result: str,
    source_identity: Path,
    client: delivery.ForcedCommandClient,
) -> int:
    """Fail on missing Android evidence or any intervening activation."""
    if report["candidate_sha"] != delivery._validate_release_sha(candidate):
        raise ValueError("candidate report identity mismatch")
    activation_passed = (
        report["status"] == "awaiting_android"
        and bool(report["checks"])
        and all(report["checks"].values())
    )
    report["status"] = "failed"
    report["checks"]["android"] = android_result == "success"
    report["android_job_result"] = android_result
    observed = read_status(client)
    report["after_android"] = observed
    report["checks"]["final_release_identity"] = same_activation(observed, report)
    report["checks"]["final_service_health"] = bool(observed and observed["healthy"])
    try:
        source = json.loads(source_identity.read_text(encoding="utf-8"))
        if not isinstance(source, dict):
            raise ValueError("invalid Android source identity")
    except (OSError, ValueError):
        report["checks"]["android_source"] = False
        report["diagnostics"].append("Android source identity is missing or invalid")
        return 1
    report["checks"]["android_source"] = (
        source.get("candidate_sha") == candidate
        and source.get("checked_out_sha") == candidate
    )
    report["android_source"] = {
        key: source.get(key) if source.get(key) == candidate else "mismatch"
        for key in ("candidate_sha", "checked_out_sha")
    }
    if activation_passed and all(report["checks"].values()):
        report["status"] = "passed"
        return 0
    return 1


def append_summary(path: Path, report: dict) -> None:
    lines = ["## Staging validation", "",
             "Candidate: `{}`".format(report["candidate_sha"]),
             "Result: **{}**".format(report["status"]),
             "Activation: `{}`".format(report.get("activation_number", "unavailable")),
             "", "Upgrade/rollback rehearsal: not run (STG-004).",
             "Production promotion eligibility: **false** (STG-005).", ""]
    for name, passed in report["checks"].items():
        lines.append("- {}: {}".format(name, "pass" if passed else "fail"))
    lines.extend(["", "Image digests:", ""])
    for key, reference in report.get("expected_images", {}).items():
        lines.append("- `{}`: `{}`".format(key, reference))
    with path.open("a", encoding="utf-8", newline="\n") as output:
        output.write("\n".join(lines) + "\n")


def main(arguments: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--android-artifact", required=True)
    subparsers = parser.add_subparsers(dest="operation", required=True)
    deploy = subparsers.add_parser("deploy")
    deploy.add_argument("workflow_run_id", type=int)
    deploy.add_argument("compose", type=Path)
    deploy.add_argument("environment", type=Path)
    finish = subparsers.add_parser("finish")
    finish.add_argument("android_result", choices=("success", "failure", "cancelled", "skipped"))
    finish.add_argument("source_identity", type=Path)
    options = parser.parse_args(arguments)
    report = new_report(options.candidate)
    report["android_artifact"] = options.android_artifact
    client = delivery.SshForcedCommandClient(delivery.SSH_HOSTS["staging"])
    diagnostics = StringIO()
    result = 1
    try:
        with redirect_stdout(diagnostics), redirect_stderr(diagnostics):
            if options.operation == "deploy":
                result = activate_candidate(report, options.workflow_run_id, options.compose,
                                            options.environment, client)
            else:
                if options.report.exists():
                    loaded = json.loads(options.report.read_text(encoding="utf-8"))
                    if (not isinstance(loaded, dict)
                            or loaded.get("candidate_sha") != options.candidate
                            or not isinstance(loaded.get("checks"), dict)
                            or not isinstance(loaded.get("diagnostics"), list)):
                        raise ValueError("candidate report is invalid or mismatched")
                    report = loaded
                result = finish_validation(report, options.candidate, options.android_result,
                                           options.source_identity, client)
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError) as error:
        # Exception text may contain remote output or private configuration.
        report["status"] = "failed"
        report["diagnostics"].append("{} failed: {}".format(options.operation, type(error).__name__))
    if result:
        report["status"] = "failed"
    # Host diagnostics have already been filtered by the delivery adapter. Never
    # serialize the release archive, environment inputs, or arbitrary SSH output.
    report["diagnostics"].extend(diagnostics.getvalue().splitlines()[-100:])
    write_report(options.report, report)
    if options.summary:
        append_summary(options.summary, report)
    print("Staging validation: {} ({})".format(report["status"], options.candidate))
    return result


if __name__ == "__main__":
    raise SystemExit(main())
