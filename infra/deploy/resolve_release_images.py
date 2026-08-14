#!/usr/bin/env python3
"""Resolve the four production image references to immutable digests."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Callable, Optional, Sequence


SERVICE_IMAGES = {
    "backend": (
        "COMMONEX_BACKEND_IMAGE",
        "ruggedbl/commonex-nest-backend",
    ),
    "frontend": (
        "COMMONEX_FRONTEND_IMAGE",
        "ruggedbl/commonex-next-web",
    ),
    "nginx": (
        "COMMONEX_NGINX_IMAGE",
        "ruggedbl/nginx-http3",
    ),
    "otel-collector": (
        "COMMONEX_OTEL_COLLECTOR_IMAGE",
        "ruggedbl/opentelemetry-collector-custom",
    ),
}
IMAGES_BY_KEY = {
    key: (service, repository)
    for service, (key, repository) in SERVICE_IMAGES.items()
}
GIT_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
DIGEST_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")

ManifestDigestResolver = Callable[[str], str]
ProcessRunner = Callable[..., subprocess.CompletedProcess]


def _unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("JSON contains a duplicate key")
        result[key] = value
    return result


def _changed_services(serialized: str) -> set[str]:
    try:
        value = json.loads(serialized, object_pairs_hook=_unique_json_object)
    except (json.JSONDecodeError, TypeError, ValueError) as error:
        raise ValueError("changed services are invalid") from error
    if (
        not isinstance(value, list)
        or any(not isinstance(service, str) for service in value)
        or len(value) != len(set(value))
        or any(service not in SERVICE_IMAGES for service in value)
    ):
        raise ValueError("changed services are invalid")
    return set(value)


def _validated_digest(value: object, message: str) -> str:
    if not isinstance(value, str) or not DIGEST_PATTERN.fullmatch(value):
        raise ValueError(message)
    return value


def _current_images(serialized: str) -> dict[str, str]:
    if not serialized.endswith("\n"):
        raise ValueError("current images are invalid")
    lines = serialized.splitlines()
    if len(lines) != len(IMAGES_BY_KEY):
        raise ValueError("current images are invalid")

    images = {}
    for line in lines:
        key, separator, reference = line.partition("=")
        if not separator or key in images or key not in IMAGES_BY_KEY:
            raise ValueError("current images are invalid")
        _, repository = IMAGES_BY_KEY[key]
        expected_prefix = f"{repository}@"
        if not reference.startswith(expected_prefix):
            raise ValueError("current images are invalid")
        _validated_digest(
            reference[len(expected_prefix) :], "current images are invalid"
        )
        images[key] = reference

    if list(images) != sorted(IMAGES_BY_KEY):
        raise ValueError("current images are invalid")
    return images


def resolve_manifest_digest(
    tag: str,
    runner: ProcessRunner = subprocess.run,
) -> str:
    completed = runner(
        [
            "docker",
            "buildx",
            "imagetools",
            "inspect",
            tag,
            "--format",
            "{{json .Manifest}}",
        ],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    try:
        manifest = json.loads(
            completed.stdout,
            object_pairs_hook=_unique_json_object,
        )
    except (json.JSONDecodeError, TypeError, ValueError) as error:
        raise ValueError("manifest response is invalid") from error
    if not isinstance(manifest, dict):
        raise ValueError("manifest response is invalid")
    return _validated_digest(manifest.get("digest"), "manifest response is invalid")


def resolve_release_images(
    changed_services_json: str,
    git_sha: str,
    current_images_text: Optional[str],
    manifest_digest_resolver: ManifestDigestResolver,
) -> str:
    if not GIT_SHA_PATTERN.fullmatch(git_sha):
        raise ValueError("Git SHA is invalid")
    changed_services = _changed_services(changed_services_json)
    current = (
        None if current_images_text is None else _current_images(current_images_text)
    )
    if current is None and changed_services != set(SERVICE_IMAGES):
        raise ValueError("bootstrap requires all services")

    resolved = {}
    for key in sorted(IMAGES_BY_KEY):
        service, repository = IMAGES_BY_KEY[key]
        if current is not None and service not in changed_services:
            resolved[key] = current[key]
            continue
        digest = _validated_digest(
            manifest_digest_resolver(f"{repository}:{git_sha}"),
            "resolved image digest is invalid",
        )
        resolved[key] = f"{repository}@{digest}"

    return "".join(f"{key}={resolved[key]}\n" for key in sorted(resolved))


def main(
    arguments: Optional[Sequence[str]] = None,
    manifest_digest_resolver: ManifestDigestResolver = resolve_manifest_digest,
) -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("changed_services_json")
    parser.add_argument("git_sha")
    parser.add_argument("current_images_file", nargs="?")
    options = parser.parse_args(arguments)
    current = None
    if options.current_images_file is not None:
        current = Path(options.current_images_file).read_text(encoding="utf-8")
    sys.stdout.write(
        resolve_release_images(
            options.changed_services_json,
            options.git_sha,
            current,
            manifest_digest_resolver,
        )
    )


def run_cli(
    arguments: Optional[Sequence[str]] = None,
    manifest_digest_resolver: ManifestDigestResolver = resolve_manifest_digest,
) -> int:
    try:
        main(arguments, manifest_digest_resolver)
    except (OSError, subprocess.CalledProcessError, ValueError):
        print("resolve-release-images: unable to resolve release images", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(run_cli())
