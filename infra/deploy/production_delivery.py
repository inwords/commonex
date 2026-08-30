#!/usr/bin/env python3
"""Orchestrate CommonEx production deploys through the forced-command seam."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import re
import subprocess
import sys
import tarfile
from typing import Callable, Optional, Protocol, Sequence, TextIO

try:
    from .resolve_release_images import resolve_release_images
    from .verify_public_services import main as verify_public_services
except ImportError:  # pragma: no cover - used when invoked as a script
    from resolve_release_images import resolve_release_images  # type: ignore
    from verify_public_services import main as verify_public_services  # type: ignore


SSH_HOST = "commonex-production"
BOOTSTRAP_DIAGNOSTIC = (
    b"commonex-deploy: no immutable activation history exists; bootstrap required\n"
)
GIT_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RUN_NUMBER_PATTERN = re.compile(r"^[1-9][0-9]{0,19}$")
ARCHIVE_FILES = (
    ("docker-compose-prod.yml", 0o644),
    (".env", 0o600),
)
MAX_HOST_DIAGNOSTIC_BYTES = 16 * 1024
MAX_HOST_DIAGNOSTIC_LINE_CHARS = 2048
HOST_DIAGNOSTIC_PREFIX = "commonex-deploy: "
ANSI_ESCAPE_PATTERN = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
SENSITIVE_DIAGNOSTIC_PATTERN = re.compile(
    r"password|secret|token|authorization|cookie|api[_-]?key|private[_-]?key",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class ForcedCommandResult:
    returncode: int
    stdout: bytes = b""
    stderr: bytes = b""


class ForcedCommandClient(Protocol):
    def run(
        self,
        command: Sequence[str],
        *,
        stdin: Optional[bytes] = None,
    ) -> ForcedCommandResult:
        ...


class SshForcedCommandClient:
    """Production adapter for the root-owned host forced command."""

    def __init__(self, host: str = SSH_HOST) -> None:
        self._host = host

    def run(
        self,
        command: Sequence[str],
        *,
        stdin: Optional[bytes] = None,
    ) -> ForcedCommandResult:
        completed = subprocess.run(
            ["ssh", self._host, " ".join(command)],
            input=stdin,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        return ForcedCommandResult(
            completed.returncode,
            completed.stdout,
            completed.stderr,
        )


ImageResolver = Callable[[str, str, Optional[str]], str]
PublicVerifier = Callable[[], int]


def _default_public_verifier() -> int:
    return verify_public_services(())


def _validate_release_sha(value: str) -> str:
    if not GIT_SHA_PATTERN.fullmatch(value):
        raise ValueError("release_sha must be a lowercase 40-character Git SHA")
    return value


def _validate_run_number(value: int) -> int:
    if type(value) is not int or not RUN_NUMBER_PATTERN.fullmatch(str(value)):
        raise ValueError("run_number must match [1-9][0-9]{0,19}")
    return value


def _archive_member(name: str, mode: int, content: bytes) -> tarfile.TarInfo:
    member = tarfile.TarInfo(name)
    member.size = len(content)
    member.mode = mode
    member.uid = 0
    member.gid = 0
    member.uname = "root"
    member.gname = "root"
    member.mtime = 0
    return member


def build_release_archive(compose: bytes, environment: bytes) -> bytes:
    """Build the exact two-member archive accepted by the host command."""

    output = BytesIO()
    with tarfile.open(fileobj=output, mode="w:gz", format=tarfile.PAX_FORMAT) as bundle:
        for (name, mode), content in zip(ARCHIVE_FILES, (compose, environment)):
            bundle.addfile(_archive_member(name, mode, content), BytesIO(content))
    return output.getvalue()


def _append_images(environment: bytes, images: str) -> bytes:
    images_bytes = images.encode("utf-8", errors="strict")
    separator = b"" if not environment or environment.endswith(b"\n") else b"\n"
    return environment + separator + images_bytes


def _same_image_set(expected: str, active: bytes) -> bool:
    try:
        active_text = active.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        return False
    return sorted(expected.splitlines()) == sorted(active_text.splitlines())


def _command_failure(
    action: str,
    result: ForcedCommandResult,
    stderr: TextIO,
) -> int:
    _write_host_diagnostics(result, stderr)
    print(
        "production-delivery: host command {} failed with status {}".format(
            action, result.returncode
        ),
        file=stderr,
    )
    return result.returncode or 1


def _write_host_diagnostics(result: ForcedCommandResult, stderr: TextIO) -> None:
    truncated = len(result.stderr) > MAX_HOST_DIAGNOSTIC_BYTES
    bounded = result.stderr[-MAX_HOST_DIAGNOSTIC_BYTES:]
    if truncated:
        first_line_end = bounded.find(b"\n")
        bounded = b"" if first_line_end < 0 else bounded[first_line_end + 1 :]
    forwarded = False
    for raw_bytes in bounded.split(b"\n"):
        raw_line = raw_bytes.decode("utf-8", errors="backslashreplace")
        without_ansi = ANSI_ESCAPE_PATTERN.sub("", raw_line)
        sanitized = "".join(
            character
            if character == "\t" or 0x20 <= ord(character) <= 0x7E
            else "\\u{:04x}".format(ord(character))
            for character in without_ansi
        ).strip()
        if not sanitized.startswith(HOST_DIAGNOSTIC_PREFIX):
            continue
        if SENSITIVE_DIAGNOSTIC_PATTERN.search(sanitized):
            sanitized = HOST_DIAGNOSTIC_PREFIX + "[redacted]"
        sanitized = sanitized[:MAX_HOST_DIAGNOSTIC_LINE_CHARS]
        print("production-delivery: host diagnostic: " + sanitized, file=stderr)
        forwarded = True
    if truncated:
        print("production-delivery: earlier host diagnostic omitted", file=stderr)
    if result.returncode == 3:
        print(
            "production-delivery: ambiguous activation result; do not retry; "
            "preserve the activation intent and manually reconcile the host",
            file=stderr,
        )
    elif result.stderr and not forwarded:
        print("production-delivery: untrusted host diagnostic omitted", file=stderr)


def deploy_release(
    release_sha: str,
    run_number: int,
    changed_services: str,
    compose_path: Path,
    environment_path: Path,
    client: ForcedCommandClient,
    *,
    image_resolver: ImageResolver = resolve_release_images,
    public_verifier: PublicVerifier = _default_public_verifier,
    stderr: TextIO = sys.stderr,
) -> int:
    """Resolve, stage, activate, and verify one production Release."""

    release_sha = _validate_release_sha(release_sha)
    run_number = _validate_run_number(run_number)

    current_result = client.run(("current-images",))
    if current_result.returncode == 0:
        try:
            current_images = current_result.stdout.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            print(
                "Unable to read the active immutable image references.",
                file=stderr,
            )
            return 1
    elif current_result.stderr == BOOTSTRAP_DIAGNOSTIC:
        current_images = None
    else:
        return _command_failure("current-images", current_result, stderr)

    try:
        expected_images = image_resolver(
            changed_services,
            release_sha,
            current_images,
        )
        environment = _append_images(environment_path.read_bytes(), expected_images)
        archive = build_release_archive(compose_path.read_bytes(), environment)
    except (OSError, subprocess.SubprocessError, UnicodeError, ValueError) as error:
        print(
            "production-delivery: unable to prepare the release: {}".format(
                type(error).__name__
            ),
            file=stderr,
        )
        return 1

    staged = client.run(("stage", release_sha), stdin=archive)
    if staged.returncode != 0:
        return _command_failure("stage", staged, stderr)

    validated = client.run(("validate", release_sha))
    if validated.returncode != 0:
        return _command_failure("validate", validated, stderr)

    activated = client.run(("deploy", release_sha, str(run_number)))
    if activated.returncode not in (0, 2):
        return _command_failure("deploy", activated, stderr)

    active = client.run(("current-images",))
    images_verified = active.returncode == 0 and _same_image_set(
        expected_images, active.stdout
    )
    if not images_verified:
        if active.returncode != 0:
            _write_host_diagnostics(active, stderr)
        print(
            "::error::Deployment committed, but active image references do not "
            "match the intended release.",
            file=stderr,
        )

    public_verified = public_verifier() == 0
    if activated.returncode == 2:
        print(
            "::error::Deployment committed, but its final audit record failed.",
            file=stderr,
        )
        return 2
    if not images_verified or not public_verified:
        return 1
    return 0


def rollback_release(
    release_sha: str,
    run_number: int,
    client: ForcedCommandClient,
    *,
    public_verifier: PublicVerifier = _default_public_verifier,
    stderr: TextIO = sys.stderr,
) -> int:
    """Activate and verify one retained production Release."""

    release_sha = _validate_release_sha(release_sha)
    run_number = _validate_run_number(run_number)

    activated = client.run(("rollback", release_sha, str(run_number)))
    if activated.returncode not in (0, 2):
        return _command_failure("rollback", activated, stderr)

    active = client.run(("current-images",))
    images_verified = active.returncode == 0
    if not images_verified:
        _write_host_diagnostics(active, stderr)
        print(
            "::error::Rollback committed, but active image references could not "
            "be read.",
            file=stderr,
        )

    public_verified = public_verifier() == 0
    if activated.returncode == 2:
        print(
            "::error::Rollback committed, but its final audit record failed.",
            file=stderr,
        )
        return 2
    if not images_verified or not public_verified:
        return 1
    return 0


def _positive_run_number(value: str) -> int:
    if not RUN_NUMBER_PATTERN.fullmatch(value):
        raise argparse.ArgumentTypeError("run_number must match [1-9][0-9]{0,19}")
    return int(value)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Orchestrate CommonEx production delivery."
    )
    subparsers = parser.add_subparsers(dest="operation", required=True)

    deploy = subparsers.add_parser("deploy")
    deploy.add_argument("release_sha")
    deploy.add_argument("run_number", type=_positive_run_number)
    deploy.add_argument("changed_services_json")
    deploy.add_argument("compose_path", type=Path)
    deploy.add_argument("environment_path", type=Path)

    rollback = subparsers.add_parser("rollback")
    rollback.add_argument("release_sha")
    rollback.add_argument("run_number", type=_positive_run_number)
    return parser


def main(
    arguments: Optional[Sequence[str]] = None,
    *,
    client: Optional[ForcedCommandClient] = None,
    stderr: TextIO = sys.stderr,
) -> int:
    options = build_parser().parse_args(arguments)
    forced_command = client if client is not None else SshForcedCommandClient()
    try:
        if options.operation == "deploy":
            return deploy_release(
                options.release_sha,
                options.run_number,
                options.changed_services_json,
                options.compose_path,
                options.environment_path,
                forced_command,
                stderr=stderr,
            )
        return rollback_release(
            options.release_sha,
            options.run_number,
            forced_command,
            stderr=stderr,
        )
    except ValueError as error:
        print(str(error), file=stderr)
        return 1
    except (OSError, subprocess.SubprocessError) as error:
        print("production-delivery: {}".format(error), file=stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
