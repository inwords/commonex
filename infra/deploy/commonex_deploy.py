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

try:
    from infra.deploy.commonex_host.activation import (
        ActivationCommittedAuditError,
        _ActivationDependencies,
        ActivationRequest,
        ActivationTransaction,
        AmbiguousActivationCommitError,
        ConfigurationRestoreError as _ConfigurationRestoreError,
    )
    from infra.deploy.commonex_host.trusted_files import (
        TrustedDurableFiles,
        _TrustedFileLocations,
    )
except ModuleNotFoundError as error:
    if error.name != "infra":
        raise
    from commonex_host.activation import (  # type: ignore[no-redef]
        ActivationCommittedAuditError,
        _ActivationDependencies,
        ActivationRequest,
        ActivationTransaction,
        AmbiguousActivationCommitError,
        ConfigurationRestoreError as _ConfigurationRestoreError,
    )
    from commonex_host.trusted_files import (  # type: ignore[no-redef]
        TrustedDurableFiles,
        _TrustedFileLocations,
    )


# Preserve the public exception name exposed by the pre-refactor module.
ConfigurationRestoreError = _ConfigurationRestoreError


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
COMMANDS = frozenset({"stage", "validate", "deploy", "rollback", "current-images"})
SAFE_ENVIRONMENT = {
    "HOME": "/root",
    "LANG": "C.UTF-8",
    "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
}


@dataclass(frozen=True)
class DeploymentConfig:
    """Filesystem and size boundaries for one deployment environment."""

    app_dir: Path = Path("/etc/commonex/app")
    release_root: Path = Path("/var/lib/commonex")
    rollback_root: Path = Path("/var/lib/commonex/rollback")
    log_path: Path = Path("/var/log/commonex/deploy.log")
    lock_path: Path = Path("/run/commonex/deploy.lock")
    max_archive_bytes: int = MAX_ARCHIVE_BYTES
    enforce_root_ownership: bool = True


DEFAULT_CONFIG = DeploymentConfig()


class UnresolvedActivationIntentError(RuntimeError):
    """Raised when a prior interrupted activation needs manual reconciliation."""


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _verify_owner(
    metadata: os.stat_result, path: Path, config: DeploymentConfig
) -> None:
    if config.enforce_root_ownership and (metadata.st_uid != 0 or metadata.st_gid != 0):
        raise PermissionError(f"path is not owned by root: {path}")


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


def _verify_lock_ancestors(config: DeploymentConfig) -> None:
    if not config.enforce_root_ownership:
        return
    for ancestor in config.lock_path.parent.parents:
        verify_directory(ancestor, config)


def _verify_lock_namespace(config: DeploymentConfig) -> None:
    _verify_lock_ancestors(config)
    verify_directory(config.lock_path.parent, config, exact_mode=0o755)


def ensure_release_root(config: DeploymentConfig) -> None:
    config.release_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    verify_directory(config.release_root, config, exact_mode=0o700)


def audit(message: str, config: DeploymentConfig = DEFAULT_CONFIG) -> None:
    """Append one trusted, newline-free event to the root-only audit log."""
    _trusted_files(config).append_audit(message)


def _audit_best_effort(message: str, config: DeploymentConfig) -> None:
    """Preserve an existing deployment outcome if its secondary audit fails."""

    try:
        audit(message, config)
    except Exception:
        # The caller is already handling or has committed the primary outcome.
        pass


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

    runtime = config.lock_path.parent
    _verify_lock_ancestors(config)
    runtime_missing = not runtime.exists() and not runtime.is_symlink()
    runtime.mkdir(mode=0o755, parents=True, exist_ok=True)
    if runtime_missing:
        runtime.chmod(0o755)
    _verify_lock_namespace(config)
    base_flags = (
        os.O_RDWR
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0)
    )
    created = False
    try:
        descriptor = os.open(
            config.lock_path,
            base_flags | os.O_CREAT | os.O_EXCL,
            0o600,
        )
        created = True
    except FileExistsError:
        descriptor = os.open(config.lock_path, base_flags)
    try:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or stat.S_IMODE(metadata.st_mode) != 0o600
        ):
            raise PermissionError(
                f"lock path is not a trusted regular file: {config.lock_path}"
            )
        _verify_owner(metadata, config.lock_path, config)
        if created:
            os.fchmod(descriptor, 0o600)
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        path_metadata = config.lock_path.lstat()
        if (
            not stat.S_ISREG(path_metadata.st_mode)
            or stat.S_IMODE(path_metadata.st_mode) != 0o600
            or (path_metadata.st_dev, path_metadata.st_ino)
            != (metadata.st_dev, metadata.st_ino)
        ):
            raise PermissionError(
                f"lock path changed while it was acquired: {config.lock_path}"
            )
        _verify_owner(path_metadata, config.lock_path, config)
        _verify_lock_namespace(config)
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
        _ensure_no_activation_intent(config)
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
            _trusted_files(config).write_release_manifest(temporary)
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


def _environment_values(path: Path) -> dict[str, str]:
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
    return values


def validate_env(path: Path) -> None:
    _environment_values(path)


def _validate_release_contents(
    value: str,
    config: DeploymentConfig,
) -> Path:
    directory = _trusted_files(config).validate_release_documents(value)

    validate_env(directory / ".env")
    run_command(compose_command(directory, "config", "--quiet"), directory)
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


def validate(value: str, config: DeploymentConfig = DEFAULT_CONFIG) -> Path:
    value = release_id(value)
    with operation_lock(config):
        _ensure_no_activation_intent(config)
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
    if destination.parent != config.app_dir or FILES.get(destination.name) != mode:
        raise ValueError("active configuration destination is not allowlisted")
    _trusted_files(config)._install_active_configuration_file(
        source,
        destination.name,
    )


def _verify_current_file(path: Path, config: DeploymentConfig) -> None:
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode):
        raise PermissionError(f"current configuration is not a regular file: {path}")
    _verify_owner(metadata, path, config)


def _rollback_path(value: str, config: DeploymentConfig) -> Path:
    suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    return config.rollback_root / f"deploy-{value}-{suffix}"


def _backup_configuration(rollback: Path, config: DeploymentConfig) -> None:
    _trusted_files(config).backup_active_configuration(rollback)


def _restore_configuration(rollback: Path, config: DeploymentConfig) -> None:
    _trusted_files(config).restore_configuration(rollback)


def _activation_intent_path(config: DeploymentConfig) -> Path:
    return config.release_root / "activation-intent.json"


def _read_activation_intent(
    config: DeploymentConfig,
) -> Optional[dict[str, object]]:
    return _trusted_files(config).read_activation_intent()


def _unresolved_activation_intent(config: DeploymentConfig) -> RuntimeError:
    return UnresolvedActivationIntentError(
        f"unresolved activation intent exists at {_activation_intent_path(config)}; "
        "manually reconcile the active configuration, runtime, and activation state, "
        "then remove the intent file as root"
    )


def _ensure_no_activation_intent(config: DeploymentConfig) -> None:
    try:
        intent = _read_activation_intent(config)
    except Exception as error:
        raise _unresolved_activation_intent(config) from error
    if intent is not None:
        raise _unresolved_activation_intent(config)


def _write_activation_intent(
    operation: str,
    value: str,
    run_number: int,
    previous_release: Optional[str],
    rollback_directory: Path,
    config: DeploymentConfig,
) -> dict[str, object]:
    _ensure_no_activation_intent(config)
    intent: dict[str, object] = {
        "candidate_release": value,
        "operation": operation,
        "previous_release": previous_release,
        "rollback_backup": rollback_directory.name,
        "run_number": run_number,
    }
    _trusted_files(config).persist_activation_intent(intent)
    return intent


def _clear_activation_intent(
    expected: dict[str, object], config: DeploymentConfig
) -> None:
    _trusted_files(config).clear_activation_intent(expected)


def _trusted_files(config: DeploymentConfig) -> TrustedDurableFiles:
    """Build the closed durable-file interface with compatibility callbacks."""

    return TrustedDurableFiles(
        _TrustedFileLocations(
            release_root=config.release_root,
            enforce_root_ownership=config.enforce_root_ownership,
            app_dir=config.app_dir,
            log_path=config.log_path,
            rollback_root=config.rollback_root,
            max_document_bytes=config.max_archive_bytes,
        ),
        sync_directory=fsync_directory,
        clock=timestamp,
    )


def _read_activation_state(config: DeploymentConfig) -> tuple[int, list[str]]:
    return _trusted_files(config).read_activation_state()


def _write_activation_state(
    run_number: int,
    history: list[str],
    config: DeploymentConfig,
) -> None:
    _trusted_files(config).write_activation_state(run_number, history)


def _reconcile_configuration(directory: Path) -> None:
    run_command(
        compose_command(
            directory,
            "up",
            "-d",
            "--pull",
            "always",
            "--remove-orphans",
            "--wait",
            "--wait-timeout",
            "120",
        ),
        directory,
    )


def _cleanup_staged_releases(
    history: list[str],
    config: DeploymentConfig,
) -> None:
    retained = set(history)
    for path in sorted(config.release_root.iterdir(), key=lambda entry: entry.name):
        if not RELEASE_PATTERN.fullmatch(path.name) or path.name in retained:
            continue
        try:
            metadata = path.lstat()
            if not stat.S_ISDIR(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode):
                raise PermissionError(f"staged release is not a trusted directory: {path}")
            verify_directory(path, config, exact_mode=0o700)
            shutil.rmtree(path)
            fsync_directory(config.release_root)
        except Exception as error:
            _audit_best_effort(
                f"RESULT cleanup status=FAILED error={type(error).__name__} "
                f"release={path.name}",
                config,
            )


def _activate(
    operation: str,
    value: str,
    run_number: int,
    config: DeploymentConfig,
) -> None:
    def pull_release(directory: Path) -> None:
        run_command(compose_command(directory, "pull"), directory)

    def prepare_rollback(rollback_directory: Path) -> None:
        verify_directory(config.app_dir, config)
        verify_directory(config.rollback_root, config)
        rollback_directory.mkdir(mode=0o700)
        if config.enforce_root_ownership:
            os.chown(rollback_directory, 0, 0)
        fsync_directory(config.rollback_root)

    def install_release(directory: Path) -> None:
        for name, mode in FILES.items():
            atomic_install(directory / name, config.app_dir / name, mode, config)

    dependencies = _ActivationDependencies(
        ensure_no_intent=lambda: _ensure_no_activation_intent(config),
        read_state=lambda: _read_activation_state(config),
        rollback_path=lambda release: _rollback_path(release, config),
        validate_release=lambda release: _validate_release_contents(release, config),
        pull_release=pull_release,
        prepare_rollback=prepare_rollback,
        backup_configuration=lambda path: _backup_configuration(path, config),
        write_intent=lambda op, release, run, previous, path: (
            _write_activation_intent(
                op,
                release,
                run,
                previous,
                path,
                config,
            )
        ),
        install_release=install_release,
        reconcile_active=lambda: _reconcile_configuration(config.app_dir),
        write_state=lambda run, next_history: _write_activation_state(
            run,
            next_history,
            config,
        ),
        clear_intent=lambda intent: _clear_activation_intent(intent, config),
        restore_configuration=lambda path: _restore_configuration(path, config),
        cleanup_releases=lambda next_history: _cleanup_staged_releases(
            next_history,
            config,
        ),
        audit=lambda message: audit(message, config),
        audit_best_effort=lambda message: _audit_best_effort(message, config),
    )
    ActivationTransaction(dependencies).activate(
        ActivationRequest(
            operation=operation,
            release=value,
            run_number=run_number,
        )
    )


def deploy(
    value: str,
    run_number: int,
    config: DeploymentConfig = DEFAULT_CONFIG,
) -> None:
    value = release_id(value)
    run_number = deployment_run_number(str(run_number))
    with operation_lock(config):
        _activate("deploy", value, run_number, config)


def rollback(
    value: str,
    run_number: int,
    config: DeploymentConfig = DEFAULT_CONFIG,
) -> None:
    value = release_id(value)
    run_number = deployment_run_number(str(run_number))
    with operation_lock(config):
        _activate("rollback", value, run_number, config)


def current_images(config: DeploymentConfig = DEFAULT_CONFIG) -> None:
    with operation_lock(config):
        _ensure_no_activation_intent(config)
        _, history = _read_activation_state(config)
        if not history:
            raise ValueError("no immutable activation history exists; bootstrap required")
        directory = _validate_release_contents(history[0], config)
        verify_directory(config.app_dir, config)
        for name in FILES:
            active = config.app_dir / name
            _verify_current_file(active, config)
            if sha256(active) != sha256(directory / name):
                raise RuntimeError(
                    f"active configuration does not match current release: {name}"
                )
        values = _environment_values(config.app_dir / ".env")
        for key in sorted(IMMUTABLE_IMAGE_REPOSITORIES):
            print(f"{key}={values[key]}")


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
    if command_name in {"deploy", "rollback"}:
        if len(command) != 3:
            raise ValueError("command is not allowed")
        return command_name, release_id(command[1]), deployment_run_number(command[2])
    if command_name == "current-images":
        if len(command) != 1:
            raise ValueError("command is not allowed")
        return command_name, "", None
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
    elif command == "rollback":
        if run_number is None:
            raise ValueError("deployment run number is required")
        rollback(value, run_number, config)
    elif command == "current-images":
        current_images(config)
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
    except ActivationCommittedAuditError as error:
        print(f"commonex-deploy: {error}", file=sys.stderr)
        return 2
    except AmbiguousActivationCommitError as error:
        print(f"commonex-deploy: {error}", file=sys.stderr)
        return 3
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
