from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path


ANDROID_VERSION_CODE_RE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)(\s*)$", re.MULTILINE)
ANDROID_VERSION_NAME_RE = re.compile(r'^(\s*versionName\s*=\s*")([^"]+)(".*)$', re.MULTILINE)
ANDROID_APPLICATION_ID_RE = re.compile(r'^(\s*applicationId\s*=\s*")([^"]+)(".*)$', re.MULTILINE)
RELEASE_TAG_RE = re.compile(r"^release/(\d{4})-(\d{2})-(\d+)/(\d+)$")


def fail(message: str) -> None:
    raise SystemExit(message)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise SystemExit(f"File not found: {path}") from exc


def parse_android_release_identity(android_build_file: Path) -> tuple[str, str, str]:
    content = read_text(android_build_file)

    application_id_match = ANDROID_APPLICATION_ID_RE.search(content)
    version_name_match = ANDROID_VERSION_NAME_RE.search(content)
    version_code_match = ANDROID_VERSION_CODE_RE.search(content)

    if application_id_match is None:
        fail(f"Could not find applicationId in {android_build_file}")
    if version_name_match is None:
        fail(f"Could not find versionName in {android_build_file}")
    if version_code_match is None:
        fail(f"Could not find versionCode in {android_build_file}")

    application_id = application_id_match.group(2)
    version_name = version_name_match.group(2)
    version_code = version_code_match.group(2)
    return application_id, version_name, version_code


def parse_release_tag(tag_name: str) -> tuple[int, int, int, int]:
    match = RELEASE_TAG_RE.fullmatch(tag_name)
    if match is None:
        fail(f"Invalid release tag '{tag_name}'. Expected release/YYYY-MM-N/P.")
    return tuple(int(value) for value in match.groups())


def run_git(*args: str) -> str:
    try:
        result = subprocess.run(
            ["git", *args],
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        raise SystemExit("git is required for Sentry release automation.") from exc
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.strip() or exc.stdout.strip()
        raise SystemExit(f"git {' '.join(args)} failed: {stderr}") from exc
    return result.stdout.strip()


def resolve_tag_sha(tag_name: str) -> str:
    return run_git("rev-list", "-n", "1", tag_name)


def list_release_tags() -> list[str]:
    output = run_git("tag", "--list", "release/*")
    return [line.strip() for line in output.splitlines() if line.strip()]


def find_previous_release_tag(current_tag: str) -> str | None:
    current_parts = parse_release_tag(current_tag)
    candidates: list[tuple[tuple[int, int, int, int], str]] = []
    for tag_name in list_release_tags():
        if tag_name == current_tag:
            continue
        match = RELEASE_TAG_RE.fullmatch(tag_name)
        if match is None:
            continue
        parts = tuple(int(value) for value in match.groups())
        if parts < current_parts:
            candidates.append((parts, tag_name))
    if not candidates:
        return None
    candidates.sort(key=lambda item: item[0])
    return candidates[-1][1]


def run_sentry_cli(
    *,
    org: str,
    project: str,
    arguments: list[str],
) -> None:
    if not os.environ.get("SENTRY_AUTH_TOKEN"):
        raise SystemExit("SENTRY_AUTH_TOKEN must be set for Sentry release automation.")
    command = [
        "sentry-cli",
        "--org",
        org,
        "--project",
        project,
        *arguments,
    ]
    try:
        subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        raise SystemExit("sentry-cli is required for Sentry release automation.") from exc
    except subprocess.CalledProcessError as exc:
        details = (exc.stderr or "").strip() or (exc.stdout or "").strip() or "No output captured."
        raise SystemExit(
            "sentry-cli command failed: "
            f"{' '.join(command[:1] + command[3:])}\n{details}"
        ) from exc


def write_github_output(path: Path, values: dict[str, str]) -> None:
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Create and deploy a Sentry release from a mobile release tag.")
    parser.add_argument("--android-build-file", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--current-sha", required=True)
    parser.add_argument("--sentry-org", required=True)
    parser.add_argument("--sentry-project", required=True)
    parser.add_argument("--sentry-repo", required=True)
    parser.add_argument("--sentry-environment", required=True)
    parser.add_argument("--github-output")
    args = parser.parse_args()

    application_id, version_name, version_code = parse_android_release_identity(Path(args.android_build_file))
    sentry_version = f"{application_id}@{version_name}+{version_code}"
    previous_tag = find_previous_release_tag(args.tag)
    current_sha = args.current_sha

    run_sentry_cli(
        org=args.sentry_org,
        project=args.sentry_project,
        arguments=["releases", "new", "--finalize", sentry_version],
    )

    if previous_tag is None:
        commit_argument = f"{args.sentry_repo}@{current_sha}"
    else:
        previous_sha = resolve_tag_sha(previous_tag)
        commit_argument = f"{args.sentry_repo}@{previous_sha}..{current_sha}"

    run_sentry_cli(
        org=args.sentry_org,
        project=args.sentry_project,
        arguments=["releases", "set-commits", sentry_version, "--commit", commit_argument],
    )

    run_sentry_cli(
        org=args.sentry_org,
        project=args.sentry_project,
        arguments=["deploys", "new", "--release", sentry_version, "-e", args.sentry_environment],
    )

    output = {
        "sentry_version": sentry_version,
        "previous_release_tag": previous_tag or "",
        "commit_range": commit_argument,
    }
    if args.github_output:
        write_github_output(Path(args.github_output), output)
    else:
        for key, value in output.items():
            print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
