#!/usr/bin/python3
"""Validated, auditable production release installer for CommonEx."""

from __future__ import annotations

import hashlib
import os
import re
import shlex
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import BinaryIO, Iterator, Optional, Sequence


FILES = {
    "docker-compose-prod.yml": 0o644,
    ".env": 0o600,
}
IMMUTABLE_IMAGE_REPOSITORIES = {
    "COMMONEX_BACKEND_IMAGE": "ruggedbl/commonex-nest-backend",
    "COMMONEX_FRONTEND_IMAGE": "ruggedbl/commonex-next-web",
    "COMMONEX_OTEL_COLLECTOR_IMAGE": "ruggedbl/opentelemetry-collector-custom",
    "COMMONEX_NGINX_IMAGE": "ruggedbl/nginx-http3",
}
REQUIRED_ENV_KEYS = {
    "POSTGRES_PORT",
    "POSTGRES_USER_NAME",
    "POSTGRES_PASSWORD",
    "POSTGRES_DATABASE",
    "POSTGRES_HOST",
    "POSTGRES_SCHEMA",
    "OPEN_EXCHANGE_RATES_API_ID",
    "DEVTOOLS_SECRET",
    "GF_SECURITY_ADMIN_USER",
    "GF_SECURITY_ADMIN_PASSWORD",
    *IMMUTABLE_IMAGE_REPOSITORIES,
}

MAX_ARCHIVE_BYTES = 10 * 1024 * 1024
READ_CHUNK_BYTES = 1024 * 1024
RELEASE_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RUN_NUMBER_PATTERN = re.compile(r"^[1-9][0-9]{0,19}$")
ENV_KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
IMMUTABLE_IMAGE_REFERENCE_PATTERN = re.compile(
    r"^(?P<repository>[^@]+)@sha256:[0-9a-f]{64}$"
)
MANIFEST_LINE_PATTERN = re.compile(r"^([0-9a-f]{64})  (.+)$")
COMMANDS = frozenset({"stage", "validate", "deploy"})
SAFE_ENVIRONMENT = {
    "HOME": "/root",
    "LANG": "C.UTF-8",
    "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
}


@dataclass(frozen=True)
class DeploymentConfig:
    """Filesystem and size boundaries for one deployment environment."""

    app_dir: Path = Path("/etc/commonex/app")
    release_root: Path = Path("/var/lib/commonex-releases")
    rollback_root: Path = Path("/etc/commonex/rollback")
    log_path: Path = Path("/var/log/commonex-deploy.log")
    lock_path: Path = Path("/run/lock/commonex-deploy.lock")
    max_archive_bytes: int = MAX_ARCHIVE_BYTES
    enforce_root_ownership: bool = True


DEFAULT_CONFIG = DeploymentConfig()


class ConfigurationRestoreError(RuntimeError):
    """Raised when both deployment and configuration restoration fail."""


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _open_flags(*flags: int) -> int:
    result = 0
    for flag in flags:
        result |= flag
    return result


def _no_follow_flag() -> int:
    return getattr(os, "O_NOFOLLOW", 0)


def _close_on_exec_flag() -> int:
    return getattr(os, "O_CLOEXEC", 0)


def _verify_owner(
    metadata: os.stat_result, path: Path, config: DeploymentConfig
) -> None:
    if config.enforce_root_ownership and (metadata.st_uid != 0 or metadata.st_gid != 0):
        raise PermissionError(f"path is not owned by root: {path}")


def _set_open_file_mode(descriptor: int, path: Path, mode: int) -> None:
    if hasattr(os, "fchmod"):
        os.fchmod(descriptor, mode)
    else:
        path.chmod(mode)


def ensure_directory(
    path: Path,
    config: DeploymentConfig,
    *,
    create_mode: int,
    exact_mode: Optional[int] = None,
) -> None:
    """Create and validate a trusted, non-writable directory path."""

    path.mkdir(mode=create_mode, parents=True, exist_ok=True)
    verify_directory(path, config, exact_mode=exact_mode)


def verify_directory(
    path: Path,
    config: DeploymentConfig,
    *,
    exact_mode: Optional[int] = None,
) -> None:
    """Validate an existing directory without creating missing state."""

    metadata = path.lstat()
    mode = stat.S_IMODE(metadata.st_mode)
    if not stat.S_ISDIR(metadata.st_mode):
        raise PermissionError(f"path is not a directory: {path}")
    _verify_owner(metadata, path, config)
    if exact_mode is not None and os.name == "posix" and mode != exact_mode:
        raise PermissionError(f"unsafe mode {mode:o} for directory: {path}")
    if exact_mode is None and os.name == "posix" and mode & 0o022:
        raise PermissionError(f"directory is group/world writable: {path}")


def ensure_release_root(config: DeploymentConfig) -> None:
    ensure_directory(
        config.release_root,
        config,
        create_mode=0o700,
        exact_mode=0o700,
    )


def audit(message: str, config: DeploymentConfig = DEFAULT_CONFIG) -> None:
    """Append one trusted, newline-free event to the root-only audit log."""

    if "\n" in message or "\r" in message:
        raise ValueError("audit message must contain exactly one line")

    ensure_directory(config.log_path.parent, config, create_mode=0o755)
    flags = _open_flags(
        os.O_APPEND,
        os.O_CREAT,
        os.O_WRONLY,
        _close_on_exec_flag(),
        _no_follow_flag(),
    )
    descriptor = os.open(config.log_path, flags, 0o600)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise PermissionError(
                f"audit path is not a regular file: {config.log_path}"
            )
        _verify_owner(metadata, config.log_path, config)
        _set_open_file_mode(descriptor, config.log_path, 0o600)
        with os.fdopen(descriptor, "a", encoding="utf-8", newline="\n") as stream:
            descriptor = -1
            stream.write(f"{timestamp()} {message}\n")
            stream.flush()
            os.fsync(stream.fileno())
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def release_id(value: str) -> str:
    if not RELEASE_PATTERN.fullmatch(value):
        raise ValueError("release id must be a lowercase 40-character Git SHA")
    return value


def deployment_run_number(value: str) -> int:
    if not RUN_NUMBER_PATTERN.fullmatch(value):
        raise ValueError("deployment run number must be a positive integer")
    return int(value)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(READ_CHUNK_BYTES), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fsync_directory(path: Path) -> None:
    """Persist directory-entry changes where the platform supports it."""

    if os.name != "posix":
        return
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


@contextmanager
def operation_lock(config: DeploymentConfig) -> Iterator[None]:
    """Serialize mutating operations on the production release state."""

    if os.name != "posix":
        yield
        return

    import fcntl

    config.lock_path.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
    flags = _open_flags(
        os.O_CREAT,
        os.O_RDWR,
        _close_on_exec_flag(),
        _no_follow_flag(),
    )
    descriptor = os.open(config.lock_path, flags, 0o600)
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise PermissionError(
                f"lock path is not a regular file: {config.lock_path}"
            )
        _verify_owner(metadata, config.lock_path, config)
        _set_open_file_mode(descriptor, config.lock_path, 0o600)
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        yield
    finally:
        os.close(descriptor)


def read_archive(config: DeploymentConfig, input_stream: BinaryIO) -> Path:
    """Copy a bounded archive from standard input into the trusted release root."""

    ensure_release_root(config)
    descriptor, archive_name = tempfile.mkstemp(
        dir=config.release_root,
        prefix="incoming-",
        suffix=".tar.gz",
    )
    archive = Path(archive_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            descriptor = -1
            total = 0
            while chunk := input_stream.read(READ_CHUNK_BYTES):
                total += len(chunk)
                if total > config.max_archive_bytes:
                    raise ValueError(
                        f"release archive exceeds {config.max_archive_bytes} bytes"
                    )
                stream.write(chunk)
            stream.flush()
            os.fsync(stream.fileno())
        archive.chmod(0o600)
        return archive
    except Exception:
        archive.unlink(missing_ok=True)
        raise
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def write_manifest(directory: Path) -> None:
    manifest = directory / "manifest.sha256"
    with manifest.open("x", encoding="utf-8", newline="\n") as stream:
        for name in sorted(FILES):
            stream.write(f"{sha256(directory / name)}  {name}\n")
        stream.flush()
        os.fsync(stream.fileno())
    manifest.chmod(0o600)


def _archive_member_name(member: tarfile.TarInfo) -> str:
    name = member.name.removeprefix("./")
    if not member.isfile() or member.size < 0 or name not in FILES:
        raise ValueError(f"invalid release member: {member.name}")
    return name


def _extract_archive(
    archive: Path, destination: Path, config: DeploymentConfig
) -> None:
    seen: set[str] = set()
    extracted_bytes = 0
    with tarfile.open(archive, mode="r|gz") as bundle:
        for member in bundle:
            name = _archive_member_name(member)
            if name in seen:
                raise ValueError(f"duplicate release member: {name}")
            extracted_bytes += member.size
            if extracted_bytes > config.max_archive_bytes:
                raise ValueError(
                    f"extracted release exceeds {config.max_archive_bytes} bytes"
                )

            seen.add(name)
            target = destination / name
            target.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
            source = bundle.extractfile(member)
            if source is None:
                raise ValueError(f"cannot read release member: {name}")
            with source, target.open("xb") as output:
                shutil.copyfileobj(source, output, length=READ_CHUNK_BYTES)
                output.flush()
                os.fsync(output.fileno())
            target.chmod(FILES[name])

    missing = set(FILES) - seen
    if missing:
        raise ValueError(f"release is missing files: {sorted(missing)}")


def stage(
    value: str,
    config: DeploymentConfig = DEFAULT_CONFIG,
    input_stream: Optional[BinaryIO] = None,
) -> None:
    value = release_id(value)
    source_stream = input_stream if input_stream is not None else sys.stdin.buffer

    with operation_lock(config):
        audit(f"ACTION stage release={value} result=START", config)
        archive: Optional[Path] = None
        temporary: Optional[Path] = None
        try:
            destination = config.release_root / value
            if destination.exists() or destination.is_symlink():
                raise FileExistsError(f"release already staged: {value}")

            archive = read_archive(config, source_stream)
            temporary = Path(
                tempfile.mkdtemp(prefix=f".{value}-", dir=config.release_root)
            )
            _extract_archive(archive, temporary, config)
            write_manifest(temporary)
            temporary.rename(destination)
            temporary = None
            fsync_directory(config.release_root)
        except Exception as error:
            if temporary is not None:
                shutil.rmtree(temporary, ignore_errors=True)
            audit(
                f"RESULT stage release={value} status=FAILED "
                f"error={type(error).__name__}",
                config,
            )
            raise
        finally:
            if archive is not None:
                archive.unlink(missing_ok=True)

        audit(f"RESULT stage release={value} status=PASS", config)


def validate_env(path: Path) -> None:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw or raw.startswith("#"):
            continue
        if "\x00" in raw:
            raise ValueError(f"invalid environment entry at line {line_number}")
        key, separator, value = raw.partition("=")
        if separator != "=" or not ENV_KEY_PATTERN.fullmatch(key) or key in values:
            raise ValueError(f"invalid environment entry at line {line_number}")
        values[key] = value

    missing = REQUIRED_ENV_KEYS - values.keys()
    if missing:
        raise ValueError(f"environment is missing keys: {sorted(missing)}")

    for key, repository in IMMUTABLE_IMAGE_REPOSITORIES.items():
        match = IMMUTABLE_IMAGE_REFERENCE_PATTERN.fullmatch(values[key])
        if match is None or match.group("repository") != repository:
            raise ValueError(f"invalid immutable image reference: {key}")


def _expected_directories() -> set[str]:
    expected: set[str] = set()
    for name in FILES:
        parent = Path(name).parent
        while parent != Path():
            expected.add(parent.as_posix())
            parent = parent.parent
    return expected


def _verify_staged_file(
    path: Path,
    expected_mode: int,
    config: DeploymentConfig,
) -> None:
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode):
        raise ValueError(f"release entry is not a regular file: {path.name}")
    _verify_owner(metadata, path, config)
    actual_mode = stat.S_IMODE(metadata.st_mode)
    if os.name == "posix" and actual_mode != expected_mode:
        raise PermissionError(f"unsafe mode {actual_mode:o} for release file: {path}")


def _parse_manifest(path: Path) -> dict[str, str]:
    manifest: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = MANIFEST_LINE_PATTERN.fullmatch(line)
        if match is None:
            raise ValueError("release manifest contains an invalid entry")
        digest, name = match.groups()
        if name not in FILES or name in manifest:
            raise ValueError(
                "release manifest contains an unexpected or duplicate file"
            )
        manifest[name] = digest

    if set(manifest) != set(FILES):
        raise ValueError("release manifest does not match the expected file set")
    return manifest


def _validate_release_contents(
    value: str,
    config: DeploymentConfig,
) -> Path:
    ensure_release_root(config)
    directory = config.release_root / value
    verify_directory(directory, config, exact_mode=0o700)
    if directory.is_symlink():
        raise ValueError(f"release is not staged safely: {value}")

    expected_files = set(FILES) | {"manifest.sha256"}
    actual_files: set[str] = set()
    actual_directories: set[str] = set()
    for path in directory.rglob("*"):
        relative = path.relative_to(directory).as_posix()
        metadata = path.lstat()
        if stat.S_ISLNK(metadata.st_mode):
            raise ValueError("release contains a symlink")
        if stat.S_ISREG(metadata.st_mode):
            actual_files.add(relative)
        elif stat.S_ISDIR(metadata.st_mode):
            actual_directories.add(relative)
        else:
            raise ValueError(f"release contains an unsupported entry: {relative}")

    if actual_files != expected_files or actual_directories != _expected_directories():
        raise ValueError("release contains missing or unexpected entries")

    for name, mode in FILES.items():
        _verify_staged_file(directory / name, mode, config)
    manifest_path = directory / "manifest.sha256"
    _verify_staged_file(manifest_path, 0o600, config)

    manifest = _parse_manifest(manifest_path)
    for name in FILES:
        if manifest[name] != sha256(directory / name):
            raise ValueError(f"release hash mismatch: {name}")

    validate_env(directory / ".env")
    run_compose_config(directory)
    return directory


def run_command(command: Sequence[str], cwd: Path) -> None:
    # All command tokens are assembled internally; shell execution remains disabled.
    subprocess.run(  # noqa: S603
        list(command),
        cwd=cwd,
        check=True,
        env=SAFE_ENVIRONMENT,
    )


def compose_command(root: Path, *arguments: str) -> list[str]:
    return [
        "docker",
        "compose",
        "--env-file",
        str(root / ".env"),
        "-f",
        str(root / "docker-compose-prod.yml"),
        *arguments,
    ]


def run_compose_config(directory: Path) -> None:
    run_command(compose_command(directory, "config", "--quiet"), directory)


def validate(value: str, config: DeploymentConfig = DEFAULT_CONFIG) -> Path:
    value = release_id(value)
    audit(f"ACTION validate release={value} result=START", config)
    try:
        directory = _validate_release_contents(value, config)
    except Exception as error:
        audit(
            f"RESULT validate release={value} status=FAILED "
            f"error={type(error).__name__}",
            config,
        )
        raise
    audit(f"RESULT validate release={value} status=PASS", config)
    return directory


def atomic_install(
    source: Path,
    destination: Path,
    mode: int,
    config: DeploymentConfig,
) -> None:
    destination.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{destination.name}.",
        dir=destination.parent,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as output:
            descriptor = -1
            with source.open("rb") as input_file:
                shutil.copyfileobj(input_file, output, length=READ_CHUNK_BYTES)
            output.flush()
            os.fsync(output.fileno())
        if config.enforce_root_ownership:
            os.chown(temporary, 0, 0)
        temporary.chmod(mode)
        temporary.replace(destination)
        fsync_directory(destination.parent)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        temporary.unlink(missing_ok=True)


def _verify_current_file(path: Path, config: DeploymentConfig) -> None:
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode):
        raise PermissionError(f"current configuration is not a regular file: {path}")
    _verify_owner(metadata, path, config)


def _rollback_path(value: str, config: DeploymentConfig) -> Path:
    suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    return config.rollback_root / f"deploy-{value}-{suffix}"


def _backup_configuration(rollback: Path, config: DeploymentConfig) -> None:
    for name in FILES:
        current = config.app_dir / name
        _verify_current_file(current, config)
        backup = rollback / name
        backup.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with current.open("rb") as source, backup.open("xb") as output:
            shutil.copyfileobj(source, output, length=READ_CHUNK_BYTES)
            output.flush()
            os.fsync(output.fileno())
        if config.enforce_root_ownership:
            os.chown(backup, 0, 0)
        backup.chmod(0o600)
    fsync_directory(rollback)


def _restore_configuration(rollback: Path, config: DeploymentConfig) -> None:
    for name, mode in FILES.items():
        atomic_install(rollback / name, config.app_dir / name, mode, config)


def _last_successful_run_path(config: DeploymentConfig) -> Path:
    return config.release_root / "last-successful-run"


def _read_last_successful_run(config: DeploymentConfig) -> int:
    ensure_release_root(config)
    path = _last_successful_run_path(config)
    flags = _open_flags(os.O_RDONLY, _close_on_exec_flag(), _no_follow_flag())
    try:
        descriptor = os.open(path, flags)
    except FileNotFoundError:
        return 0

    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise PermissionError(f"deployment state is not a regular file: {path}")
        _verify_owner(metadata, path, config)
        mode = stat.S_IMODE(metadata.st_mode)
        if os.name == "posix" and mode != 0o600:
            raise PermissionError(f"unsafe mode {mode:o} for deployment state: {path}")
        with os.fdopen(descriptor, "r", encoding="ascii", newline="\n") as stream:
            descriptor = -1
            value = stream.read(22)
    finally:
        if descriptor >= 0:
            os.close(descriptor)

    if not value.endswith("\n") or value.count("\n") != 1:
        raise ValueError("deployment state is invalid")
    return deployment_run_number(value.removesuffix("\n"))


def _write_last_successful_run(run_number: int, config: DeploymentConfig) -> None:
    ensure_release_root(config)
    destination = _last_successful_run_path(config)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=".last-successful-run.", dir=config.release_root
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="ascii", newline="\n") as stream:
            descriptor = -1
            stream.write(f"{run_number}\n")
            stream.flush()
            os.fsync(stream.fileno())
        if config.enforce_root_ownership:
            os.chown(temporary, 0, 0)
        temporary.chmod(0o600)
        temporary.replace(destination)
        fsync_directory(config.release_root)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        temporary.unlink(missing_ok=True)


def deploy(
    value: str,
    run_number: int,
    config: DeploymentConfig = DEFAULT_CONFIG,
) -> None:
    value = release_id(value)
    run_number = deployment_run_number(str(run_number))
    with operation_lock(config):
        last_successful_run = _read_last_successful_run(config)
        if run_number <= last_successful_run:
            audit(
                f"RESULT deploy release={value} run={run_number} status=REJECTED "
                f"last_successful_run={last_successful_run}",
                config,
            )
            raise ValueError(
                "deployment run is older than or equal to the last success"
            )
        directory = _validate_release_contents(value, config)
        verify_directory(config.app_dir, config)
        verify_directory(config.rollback_root, config)
        rollback = _rollback_path(value, config)
        audit(
            f"ACTION deploy release={value} "
            "consequence=replace_allowlisted_config_and_reconcile_compose "
            f"rollback={rollback} result=START",
            config,
        )

        rollback.mkdir(mode=0o700)
        if config.enforce_root_ownership:
            os.chown(rollback, 0, 0)
        fsync_directory(config.rollback_root)

        backups_ready = False
        installation_started = False
        compose_started = False
        try:
            _backup_configuration(rollback, config)
            backups_ready = True
            installation_started = True
            for name, mode in FILES.items():
                atomic_install(directory / name, config.app_dir / name, mode, config)

            compose_started = True
            run_command(
                compose_command(config.app_dir, "up", "-d", "--pull", "always"),
                config.app_dir,
            )
            _write_last_successful_run(run_number, config)
        except Exception as deployment_error:
            if backups_ready and installation_started:
                try:
                    _restore_configuration(rollback, config)
                except Exception as restore_error:
                    audit(
                        f"RESULT deploy release={value} status=FAILED "
                        "configuration_restored=FAILED "
                        f"runtime_may_have_changed={str(compose_started).lower()} "
                        f"deployment_error={type(deployment_error).__name__} "
                        f"restore_error={type(restore_error).__name__}",
                        config,
                    )
                    raise ConfigurationRestoreError(
                        "deployment failed and configuration restoration also failed"
                    ) from restore_error
                restoration = "PASS"
            else:
                restoration = "NOT_NEEDED"

            audit(
                f"RESULT deploy release={value} status=FAILED "
                f"configuration_restored={restoration} "
                f"runtime_may_have_changed={str(compose_started).lower()} "
                f"error={type(deployment_error).__name__}",
                config,
            )
            raise

        audit(
            f"RESULT deploy release={value} run={run_number} "
            f"rollback={rollback} status=PASS",
            config,
        )


def parse_invocation(
    arguments: Sequence[str],
    original_command: str,
) -> tuple[str, str, Optional[int]]:
    if list(arguments) == ["forced"]:
        command = shlex.split(original_command)
    else:
        command = list(arguments)

    if not command:
        raise ValueError("command is not allowed")
    command_name = command[0]
    if command_name not in COMMANDS:
        raise ValueError("command is not allowed")
    if command_name == "deploy":
        if len(command) != 3:
            raise ValueError("command is not allowed")
        return command_name, release_id(command[1]), deployment_run_number(command[2])
    if len(command) != 2:
        raise ValueError("command is not allowed")
    return command_name, release_id(command[1]), None


def execute(
    command: str,
    value: str,
    run_number: Optional[int],
    config: DeploymentConfig,
    input_stream: BinaryIO,
) -> None:
    if command == "stage":
        stage(value, config, input_stream)
    elif command == "validate":
        validate(value, config)
    elif command == "deploy":
        if run_number is None:
            raise ValueError("deployment run number is required")
        deploy(value, run_number, config)
    else:
        raise ValueError("command is not allowed")


def main(
    arguments: Optional[Sequence[str]] = None,
    config: DeploymentConfig = DEFAULT_CONFIG,
) -> None:
    if not hasattr(os, "geteuid") or os.geteuid() != 0:
        raise PermissionError("commonex-deploy must run as root")

    supplied_arguments = sys.argv[1:] if arguments is None else arguments
    command, value, run_number = parse_invocation(
        supplied_arguments,
        os.environ.get("SSH_ORIGINAL_COMMAND", ""),
    )
    execute(command, value, run_number, config, sys.stdin.buffer)


def run_cli() -> int:
    try:
        main()
    except Exception as error:
        try:
            audit(f"RESULT command status=FAILED error={type(error).__name__}")
        except Exception as audit_error:
            print(
                f"commonex-deploy: audit failure: {type(audit_error).__name__}",
                file=sys.stderr,
            )
        print(f"commonex-deploy: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(run_cli())
