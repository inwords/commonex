#!/usr/bin/python3
"""Collect a non-secret-bearing inventory of CommonEx production host paths."""

from __future__ import annotations

import hashlib
import base64
import json
import os
import platform
import re
import stat
import subprocess
import sys
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterator, Optional, Sequence


READ_CHUNK_BYTES = 1024 * 1024
RELEASE_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RUN_NUMBER_PATTERN = re.compile(r"^[1-9][0-9]{0,19}$")
ROLLBACK_BACKUP_PATTERN = re.compile(
    r"^deploy-[0-9a-f]{40}-[0-9]{8}T[0-9]{12}Z$"
)
ENV_KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")
MANIFEST_LINE_PATTERN = re.compile(r"^([0-9a-f]{64})  (.+)$")
IMMUTABLE_IMAGE_REFERENCE_PATTERN = re.compile(
    r"^(?P<repository>[^@]+)@sha256:[0-9a-f]{64}$"
)
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
RELEASE_FILES = {"docker-compose-prod.yml", ".env", "manifest.sha256"}
PUBLIC_KEY_PATTERN = re.compile(
    r"(?P<key_type>(?:ssh|ecdsa|sk)-[A-Za-z0-9@._+-]+) "
    r"(?P<key_data>[A-Za-z0-9+/]+={0,3})(?:[ \t]+.*)?$"
)
AUDIT_LINE_PATTERN = re.compile(
    r"^(?P<timestamp>[0-9]{4}-[0-9]{2}-[0-9]{2}T"
    r"[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?(?:Z|[+-][0-9]{2}:[0-9]{2})) "
    r"(?P<event>ACTION|RESULT) (?P<operation>[a-z-]+)(?: |$)"
)
EXPECTED_FORCED_COMMAND = "sudo -n /usr/local/sbin/commonex-deploy forced"
EXPECTED_AUTHORIZED_KEY_OPTIONS = (
    'restrict,command="' + EXPECTED_FORCED_COMMAND + '" '
)
EXPECTED_SUDO_COMMAND = "/usr/local/sbin/commonex-deploy forced"
EXPECTED_EFFECTIVE_SUDO_GRANT = "(root) NOPASSWD: " + EXPECTED_SUDO_COMMAND
SUDO_LIST_COMMAND = ("/usr/bin/sudo", "-n", "-l", "-U", "commonex-deploy")
MAX_SUDO_LIST_BYTES = 64 * 1024
SUDO_LIST_ENVIRONMENT = {
    "LANG": "C",
    "LC_ALL": "C",
    "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
}
SUDO_DEFAULTS_HEADER_PATTERN = re.compile(
    r"^Matching Defaults entries for commonex-deploy on [^\r\n:]+:$",
    re.MULTILINE,
)
SUDO_LIST_HEADER_PATTERN = re.compile(
    r"^User commonex-deploy may run the following commands on [^\r\n:]+:$",
    re.MULTILINE,
)
EXPECTED_SECURE_PATH = (
    'Defaults:commonex-deploy secure_path="'
    '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"'
)
EXPECTED_SUDO_POLICY = (
    "Defaults:commonex-deploy env_reset",
    EXPECTED_SECURE_PATH,
    'Defaults:commonex-deploy env_keep = "SSH_ORIGINAL_COMMAND"',
    "commonex-deploy ALL=(root) NOPASSWD: " + EXPECTED_SUDO_COMMAND,
)
EXPECTED_SUDO_POLICY_TEXT = "\n".join(EXPECTED_SUDO_POLICY) + "\n"
EXPECTED_LOGROTATE_POLICY = (
    "/var/log/commonex/deploy.log {",
    "daily",
    "rotate 90",
    "missingok",
    "notifempty",
    "compress",
    "delaycompress",
    "create 0600 root root",
    "su root root",
    "}",
)
EXPECTED_LOGROTATE_POLICY_TEXT = (
    EXPECTED_LOGROTATE_POLICY[0]
    + "\n"
    + "".join("    " + line + "\n" for line in EXPECTED_LOGROTATE_POLICY[1:-1])
    + EXPECTED_LOGROTATE_POLICY[-1]
    + "\n"
)


@dataclass(frozen=True)
class InventoryTarget:
    name: str
    path: Path
    recursive: bool = False


@dataclass(frozen=True)
class InventoryConfig:
    targets: Sequence[InventoryTarget]
    activation_state_paths: Sequence[Path]
    activation_intent_paths: Sequence[Path]
    legacy_run_paths: Sequence[Path]
    max_entries: int = 10_000
    max_hash_bytes: int = 50 * 1024 * 1024
    operation_lock_paths: Sequence[Path] = ()
    required_target_names: Sequence[str] = ()


def _deploy_account_home() -> Path:
    try:
        import pwd

        return Path(pwd.getpwnam("commonex-deploy").pw_dir)
    except (ImportError, KeyError):
        return Path("/home/commonex-deploy")


def default_config() -> InventoryConfig:
    deploy_home = _deploy_account_home()
    return InventoryConfig(
        targets=(
            InventoryTarget("installed_command", Path("/usr/local/sbin/commonex-deploy")),
            InventoryTarget(
                "previous_installed_command",
                Path("/usr/local/sbin/commonex-deploy.previous"),
            ),
            InventoryTarget("tool_installation", Path("/opt/commonex/deploy"), True),
            InventoryTarget("configuration", Path("/etc/commonex"), True),
            InventoryTarget(
                "legacy_release_state", Path("/var/lib/commonex-releases"), True
            ),
            InventoryTarget("canonical_release_state", Path("/var/lib/commonex"), True),
            InventoryTarget("legacy_audit_log", Path("/var/log/commonex-deploy.log")),
            InventoryTarget("canonical_audit_logs", Path("/var/log/commonex"), True),
            InventoryTarget("canonical_audit_log", Path("/var/log/commonex/deploy.log")),
            InventoryTarget(
                "legacy_operation_lock", Path("/run/lock/commonex-deploy.lock")
            ),
            InventoryTarget("canonical_runtime", Path("/run/commonex"), True),
            InventoryTarget("sudo_policy", Path("/etc/sudoers.d/commonex-deploy")),
            InventoryTarget("logrotate_configuration", Path("/etc/logrotate.conf")),
            InventoryTarget(
                "commonex_logrotate_policy", Path("/etc/logrotate.d/commonex-deploy")
            ),
            InventoryTarget(
                "deploy_authorized_keys",
                deploy_home / ".ssh" / "authorized_keys",
            ),
            InventoryTarget(
                "deploy_sshd_policy",
                Path("/etc/ssh/sshd_config.d/60-commonex.conf"),
            ),
        ),
        activation_state_paths=(
            Path("/var/lib/commonex-releases/activation-state.json"),
            Path("/var/lib/commonex/activation-state.json"),
        ),
        activation_intent_paths=(
            Path("/var/lib/commonex-releases/activation-intent.json"),
            Path("/var/lib/commonex/activation-intent.json"),
        ),
        legacy_run_paths=(
            Path("/var/lib/commonex-releases/last-successful-run"),
            Path("/var/lib/commonex/last-successful-run"),
        ),
        operation_lock_paths=(
            Path("/etc/commonex/deploy.lock"),
            Path("/run/lock/commonex-deploy.lock"),
            Path("/run/commonex/deploy.lock"),
        ),
        required_target_names=(
            "installed_command",
            "configuration",
            "sudo_policy",
            "deploy_authorized_keys",
        ),
    )


DEFAULT_CONFIG = default_config()


def timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _kind(metadata: os.stat_result) -> str:
    if stat.S_ISREG(metadata.st_mode):
        return "file"
    if stat.S_ISDIR(metadata.st_mode):
        return "directory"
    if stat.S_ISLNK(metadata.st_mode):
        return "symlink"
    if stat.S_ISSOCK(metadata.st_mode):
        return "socket"
    if stat.S_ISFIFO(metadata.st_mode):
        return "fifo"
    if stat.S_ISCHR(metadata.st_mode):
        return "character_device"
    if stat.S_ISBLK(metadata.st_mode):
        return "block_device"
    return "unknown"


def _metadata(metadata: os.stat_result) -> dict[str, object]:
    result: dict[str, object] = {
        "kind": _kind(metadata),
        "mode": f"{stat.S_IMODE(metadata.st_mode):04o}",
        "uid": metadata.st_uid,
        "gid": metadata.st_gid,
        "device": metadata.st_dev,
        "inode": metadata.st_ino,
        "links": metadata.st_nlink,
        "size": metadata.st_size,
        "modified_ns": metadata.st_mtime_ns,
    }
    try:
        import grp
        import pwd

        result["owner"] = pwd.getpwuid(metadata.st_uid).pw_name
        result["group"] = grp.getgrgid(metadata.st_gid).gr_name
    except (ImportError, KeyError):
        # Numeric IDs remain authoritative when local name databases are unavailable.
        pass
    return result


def _open_readonly(path: Path) -> int:
    flags = os.O_RDONLY
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    return os.open(path, flags)


def _hash_file(
    path: Path, expected: os.stat_result, max_hash_bytes: int
) -> tuple[Optional[str], str]:
    if expected.st_size > max_hash_bytes:
        return None, "omitted_size_limit"
    descriptor = -1
    try:
        descriptor = _open_readonly(path)
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode):
            return None, "not_regular"
        if (opened.st_dev, opened.st_ino) != (expected.st_dev, expected.st_ino):
            return None, "changed_during_inventory"
        digest = hashlib.sha256()
        total = 0
        while chunk := os.read(descriptor, READ_CHUNK_BYTES):
            total += len(chunk)
            if total > max_hash_bytes:
                return None, "omitted_size_limit"
            digest.update(chunk)
        after = os.fstat(descriptor)
        if (after.st_size, after.st_mtime_ns, after.st_ctime_ns) != (
            opened.st_size,
            opened.st_mtime_ns,
            opened.st_ctime_ns,
        ):
            return None, "changed_during_inventory"
        return digest.hexdigest(), "hashed"
    except OSError:
        return None, "unreadable"
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _entry(path: Path, relative: str, max_hash_bytes: int) -> dict[str, object]:
    metadata = path.lstat()
    result: dict[str, object] = {"path": relative, **_metadata(metadata)}
    if stat.S_ISREG(metadata.st_mode):
        digest, hash_status = _hash_file(path, metadata, max_hash_bytes)
        result["hash_status"] = hash_status
        if digest is not None:
            result["sha256"] = digest
    elif stat.S_ISLNK(metadata.st_mode):
        try:
            result["link_target"] = os.readlink(path)
        except OSError:
            result["link_target_status"] = "unreadable"
    return result


def _walk_directory(
    root: Path, max_entries: int, max_hash_bytes: int
) -> tuple[list[dict[str, object]], bool, list[str]]:
    entries: list[dict[str, object]] = []
    warnings: list[str] = []
    pending = [root]
    truncated = False
    while pending:
        directory = pending.pop()
        try:
            with os.scandir(directory) as scan:
                children = sorted(scan, key=lambda item: item.name)
        except OSError:
            relative = directory.relative_to(root).as_posix() or "."
            warnings.append(f"unreadable_directory:{relative}")
            continue
        for child in children:
            if len(entries) >= max_entries:
                truncated = True
                return entries, truncated, warnings
            path = Path(child.path)
            relative = path.relative_to(root).as_posix()
            try:
                record = _entry(path, relative, max_hash_bytes)
            except OSError:
                warnings.append(f"unreadable_entry:{relative}")
                continue
            entries.append(record)
            if record["kind"] == "directory":
                pending.append(path)
    entries.sort(key=lambda item: str(item["path"]))
    return entries, truncated, warnings


def _inventory_target(
    target: InventoryTarget, config: InventoryConfig
) -> dict[str, object]:
    result: dict[str, object] = {"name": target.name, "path": str(target.path)}
    try:
        metadata = target.path.lstat()
    except FileNotFoundError:
        return {**result, "exists": False}
    except OSError:
        return {**result, "exists": True, "readable": False}

    result.update({"exists": True, "readable": True, **_metadata(metadata)})
    if stat.S_ISREG(metadata.st_mode):
        digest, hash_status = _hash_file(
            target.path, metadata, config.max_hash_bytes
        )
        result["hash_status"] = hash_status
        if digest is not None:
            result["sha256"] = digest
    elif stat.S_ISLNK(metadata.st_mode):
        try:
            result["link_target"] = os.readlink(target.path)
        except OSError:
            result["link_target_status"] = "unreadable"
    if target.recursive and stat.S_ISDIR(metadata.st_mode):
        entries, truncated, warnings = _walk_directory(
            target.path, config.max_entries, config.max_hash_bytes
        )
        result["entries"] = entries
        result["truncated"] = truncated
        if warnings:
            result["warnings"] = warnings
    return result


def _unique_json_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def _read_bounded_text(path: Path, maximum_bytes: int) -> str:
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > maximum_bytes:
        raise ValueError("document is not a bounded regular file")
    descriptor = _open_readonly(path)
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode):
            raise ValueError("document is not a regular file")
        if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
            raise ValueError("document changed during inventory")
        chunks = []
        total = 0
        while chunk := os.read(descriptor, min(READ_CHUNK_BYTES, maximum_bytes + 1)):
            chunks.append(chunk)
            total += len(chunk)
            if total > maximum_bytes:
                raise ValueError("document is too large")
        after = os.fstat(descriptor)
        if (after.st_size, after.st_mtime_ns, after.st_ctime_ns) != (
            opened.st_size,
            opened.st_mtime_ns,
            opened.st_ctime_ns,
        ):
            raise ValueError("document changed during inventory")
    finally:
        os.close(descriptor)
    return b"".join(chunks).decode("utf-8")


def _missing_summary(path: Path) -> dict[str, object]:
    return {"path": str(path), "exists": False}


def _activation_state_summary(path: Path) -> dict[str, object]:
    try:
        serialized = _read_bounded_text(path, 8192)
    except FileNotFoundError:
        return _missing_summary(path)
    except (OSError, UnicodeError, ValueError):
        return {
            "path": str(path),
            "exists": True,
            "valid": False,
            "error": "invalid_activation_state",
        }
    try:
        value = json.loads(serialized, object_pairs_hook=_unique_json_object)
        if not isinstance(value, dict) or set(value) != {
            "last_successful_run",
            "history",
        }:
            raise ValueError("invalid keys")
        run_number = value["last_successful_run"]
        history = value["history"]
        if (
            isinstance(run_number, bool)
            or not isinstance(run_number, int)
            or RUN_NUMBER_PATTERN.fullmatch(str(run_number)) is None
        ):
            raise ValueError("invalid run number")
        if not isinstance(history, list) or len(history) > 3:
            raise ValueError("invalid state")
        if any(
            not isinstance(item, str) or RELEASE_PATTERN.fullmatch(item) is None
            for item in history
        ) or len(set(history)) != len(history):
            raise ValueError("invalid history")
    except (json.JSONDecodeError, ValueError):
        return {
            "path": str(path),
            "exists": True,
            "valid": False,
            "error": "invalid_activation_state",
        }
    return {
        "path": str(path),
        "exists": True,
        "valid": True,
        "last_successful_run": run_number,
        "history": history,
    }


def _activation_intent_summary(path: Path) -> dict[str, object]:
    try:
        serialized = _read_bounded_text(path, 4096)
    except FileNotFoundError:
        return _missing_summary(path)
    except (OSError, UnicodeError, ValueError):
        return {
            "path": str(path),
            "exists": True,
            "valid": False,
            "error": "invalid_activation_intent",
        }
    try:
        value = json.loads(serialized, object_pairs_hook=_unique_json_object)
        expected = {
            "candidate_release",
            "operation",
            "previous_release",
            "rollback_backup",
            "run_number",
        }
        if not isinstance(value, dict) or set(value) != expected:
            raise ValueError("invalid keys")
        candidate = value["candidate_release"]
        previous = value["previous_release"]
        operation = value["operation"]
        rollback_backup = value["rollback_backup"]
        run_number = value["run_number"]
        if not isinstance(candidate, str) or RELEASE_PATTERN.fullmatch(candidate) is None:
            raise ValueError("invalid candidate")
        if previous is not None and (
            not isinstance(previous, str) or RELEASE_PATTERN.fullmatch(previous) is None
        ):
            raise ValueError("invalid previous release")
        if operation not in {"deploy", "rollback"}:
            raise ValueError("invalid operation")
        if (
            not isinstance(rollback_backup, str)
            or ROLLBACK_BACKUP_PATTERN.fullmatch(rollback_backup) is None
        ):
            raise ValueError("invalid rollback backup")
        if (
            isinstance(run_number, bool)
            or not isinstance(run_number, int)
            or RUN_NUMBER_PATTERN.fullmatch(str(run_number)) is None
        ):
            raise ValueError("invalid run number")
    except (json.JSONDecodeError, ValueError):
        return {
            "path": str(path),
            "exists": True,
            "valid": False,
            "error": "invalid_activation_intent",
        }
    return {
        "path": str(path),
        "exists": True,
        "valid": True,
        "candidate_release": candidate,
        "operation": operation,
        "previous_release": previous,
        "rollback_backup": rollback_backup,
        "run_number": run_number,
    }


def _legacy_run_summary(path: Path) -> dict[str, object]:
    try:
        serialized = _read_bounded_text(path, 21)
        if not serialized.endswith("\n") or serialized.count("\n") != 1:
            raise ValueError("invalid legacy run")
        digits = serialized.removesuffix("\n")
        if RUN_NUMBER_PATTERN.fullmatch(digits) is None:
            raise ValueError("invalid legacy run")
        run_number = int(digits)
    except FileNotFoundError:
        return _missing_summary(path)
    except (OSError, UnicodeError, ValueError):
        return {
            "path": str(path),
            "exists": True,
            "valid": False,
            "error": "invalid_legacy_run",
        }
    return {
        "path": str(path),
        "exists": True,
        "valid": True,
        "run_number": run_number,
    }


def _environment_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path), "exists": True}
    try:
        serialized = _read_bounded_text(path, 1024 * 1024)
        values: dict[str, str] = {}
        for raw in serialized.splitlines():
            if not raw or raw.startswith("#"):
                continue
            key, separator, value = raw.partition("=")
            if (
                separator != "="
                or ENV_KEY_PATTERN.fullmatch(key) is None
                or key in values
                or "\x00" in value
            ):
                raise ValueError("invalid environment")
            values[key] = value
        if not REQUIRED_ENV_KEYS.issubset(values):
            raise ValueError("missing environment keys")
        images: dict[str, str] = {}
        for key, repository in IMMUTABLE_IMAGE_REPOSITORIES.items():
            value = values[key]
            match = IMMUTABLE_IMAGE_REFERENCE_PATTERN.fullmatch(value)
            if match is None or match.group("repository") != repository:
                raise ValueError("invalid image reference")
            images[key] = value
    except (OSError, UnicodeError, ValueError):
        return {**base, "valid": False, "error": "invalid_environment"}
    return {
        **base,
        "valid": True,
        "keys": sorted(values),
        "image_references": images,
    }


def _manifest_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path), "exists": True}
    try:
        serialized = _read_bounded_text(path, 4096)
        entries: dict[str, str] = {}
        for raw in serialized.splitlines():
            match = MANIFEST_LINE_PATTERN.fullmatch(raw)
            if match is None:
                raise ValueError("invalid manifest")
            digest, name = match.groups()
            if name not in {"docker-compose-prod.yml", ".env"} or name in entries:
                raise ValueError("invalid manifest member")
            entries[name] = digest
        if set(entries) != {"docker-compose-prod.yml", ".env"}:
            raise ValueError("incomplete manifest")
    except (OSError, UnicodeError, ValueError):
        return {**base, "valid": False, "error": "invalid_manifest"}
    return {**base, "valid": True, "entries": entries}


def _authorized_keys_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path)}
    try:
        serialized = _read_bounded_text(path, 1024 * 1024)
        entries = []
        for raw in serialized.splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if not line.startswith(EXPECTED_AUTHORIZED_KEY_OPTIONS):
                raise ValueError("invalid public key options")
            match = PUBLIC_KEY_PATTERN.fullmatch(
                line[len(EXPECTED_AUTHORIZED_KEY_OPTIONS) :]
            )
            if match is None:
                raise ValueError("invalid public key")
            key_data = base64.b64decode(match.group("key_data"), validate=True)
            fingerprint = base64.b64encode(hashlib.sha256(key_data).digest()).decode().rstrip("=")
            entries.append(
                {
                    "key_type": match.group("key_type"),
                    "fingerprint": f"SHA256:{fingerprint}",
                    "restrict": True,
                    "forced_command_matches": True,
                }
            )
        if not entries:
            raise ValueError("no keys")
    except (OSError, UnicodeError, ValueError):
        return {**base, "exists": path.exists() or path.is_symlink(), "valid": False}
    valid = all(
        entry["restrict"] and entry["forced_command_matches"] for entry in entries
    )
    return {**base, "exists": True, "valid": valid, "entries": entries}


def _sudo_policy_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path)}
    try:
        serialized = _read_bounded_text(path, 1024 * 1024)
        valid = serialized in {
            EXPECTED_SUDO_POLICY_TEXT,
            EXPECTED_SUDO_POLICY_TEXT[:-1],
        }
    except (OSError, UnicodeError, ValueError):
        return {**base, "exists": path.exists() or path.is_symlink(), "valid": False}
    return {
        **base,
        "exists": True,
        "valid": valid,
        "forced_command_matches": valid,
        "env_reset": valid,
        "secure_path": valid,
        "keeps_ssh_original_command": valid,
    }


def _effective_sudo_policy_summary(
    runner: Callable[..., subprocess.CompletedProcess[bytes]] = subprocess.run,
) -> dict[str, object]:
    base: dict[str, object] = {"account": "commonex-deploy"}
    try:
        completed = runner(
            SUDO_LIST_COMMAND,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=10,
            check=False,
            env=SUDO_LIST_ENVIRONMENT,
            cwd="/",
        )
        if completed.returncode != 0:
            return {**base, "valid": False, "query_succeeded": False}
        if len(completed.stdout) > MAX_SUDO_LIST_BYTES:
            raise ValueError("sudo list output is too large")
        serialized = completed.stdout.decode("utf-8")
        defaults_headers = list(SUDO_DEFAULTS_HEADER_PATTERN.finditer(serialized))
        headers = list(SUDO_LIST_HEADER_PATTERN.finditer(serialized))
        if (
            len(defaults_headers) != 1
            or len(headers) != 1
            or defaults_headers[0].end() >= headers[0].start()
        ):
            raise ValueError("invalid sudo list output")
        defaults_lines = []
        for line in serialized[defaults_headers[0].end() : headers[0].start()].splitlines():
            if not line.strip():
                continue
            if not line.startswith("    "):
                raise ValueError("invalid sudo defaults output")
            defaults_lines.append(line.strip())
        defaults = re.split(r"(?<!\\),", " ".join(defaults_lines))
        env_reset: Optional[bool] = None
        setenv = False
        secure_path: Optional[str] = None
        explicit_env_keep: set[str] = set()
        for raw_default in defaults:
            default = raw_default.strip()
            if default == "env_reset":
                env_reset = True
            elif default == "!env_reset":
                env_reset = False
            elif default == "setenv":
                setenv = True
            elif default == "!setenv":
                setenv = False
            elif default.startswith("secure_path="):
                match = re.fullmatch(
                    r'secure_path=(?:"(?P<quoted>(?:\\:|[-/A-Za-z0-9_.])+)"|'
                    r"(?P<plain>(?:\\:|[-/A-Za-z0-9_.])+))",
                    default,
                )
                if match is None:
                    raise ValueError("invalid secure_path output")
                secure_path = (match.group("quoted") or match.group("plain")).replace(
                    "\\:", ":"
                )
            elif default.startswith(("env_keep=", "env_keep+=", "env_keep-=")):
                match = re.fullmatch(
                    r'env_keep(?P<operation>\+?=|-=)(?:"'
                    r'(?P<quoted>[A-Z0-9_ ]*)"|(?P<plain>[A-Z0-9_]+))',
                    default,
                )
                if match is None:
                    raise ValueError("invalid env_keep output")
                values = set((match.group("quoted") or match.group("plain") or "").split())
                if match.group("operation") == "=":
                    explicit_env_keep = values
                elif match.group("operation") == "+=":
                    explicit_env_keep.update(values)
                else:
                    explicit_env_keep.difference_update(values)
            elif default.startswith(
                (
                    "env_reset",
                    "!env_reset",
                    "env_file",
                    "!env_file",
                    "restricted_env_file",
                    "!restricted_env_file",
                    "env_check",
                    "!env_check",
                    "setenv",
                    "!setenv",
                    "secure_path",
                    "env_keep",
                )
            ):
                raise ValueError("invalid security default output")
        grants = []
        for line in serialized[headers[0].end() :].splitlines():
            if not line.strip():
                continue
            if not line.startswith("    "):
                raise ValueError("invalid sudo grant output")
            grants.append(line.strip())
    except (OSError, subprocess.SubprocessError, UnicodeError, ValueError):
        return {**base, "valid": False, "query_succeeded": False}
    valid_defaults = (
        env_reset is True
        and secure_path == SUDO_LIST_ENVIRONMENT["PATH"]
        and explicit_env_keep == {"SSH_ORIGINAL_COMMAND"}
        and not setenv
    )
    return {
        **base,
        "valid": valid_defaults and grants == [EXPECTED_EFFECTIVE_SUDO_GRANT],
        "query_succeeded": True,
        "grant_count": len(grants),
        "expected_grant_present": EXPECTED_EFFECTIVE_SUDO_GRANT in grants,
        "env_reset": env_reset is True,
        "secure_path": secure_path == SUDO_LIST_ENVIRONMENT["PATH"],
        "keeps_only_ssh_original_command": explicit_env_keep
        == {"SSH_ORIGINAL_COMMAND"},
        "setenv_disabled": not setenv,
    }


def _logrotate_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path)}
    try:
        serialized = _read_bounded_text(path, 1024 * 1024)
        valid = serialized in {
            EXPECTED_LOGROTATE_POLICY_TEXT,
            EXPECTED_LOGROTATE_POLICY_TEXT[:-1],
        }
    except (OSError, UnicodeError, ValueError):
        return {**base, "exists": path.exists() or path.is_symlink(), "valid": False}
    return {
        **base,
        "exists": True,
        "valid": valid,
        "log_paths": ["/var/log/commonex/deploy.log"] if valid else [],
        "frequency": "daily" if valid else None,
        "rotate": 90 if valid else None,
        "create": "create 0600 root root" if valid else None,
    }


def _audit_summary(path: Path) -> dict[str, object]:
    base: dict[str, object] = {"path": str(path)}
    try:
        serialized = _read_bounded_text(path, 50 * 1024 * 1024)
        status_counts: dict[str, int] = {}
        recognized = 0
        latest_timestamp: Optional[str] = None
        lines = serialized.splitlines()
        for line in lines:
            match = AUDIT_LINE_PATTERN.match(line)
            if match is None:
                continue
            recognized += 1
            latest_timestamp = match.group("timestamp")
            status_match = re.search(
                r"(?:^| )status=(PASS|FAILED|REJECTED|AMBIGUOUS_COMMIT)(?: |$)",
                line,
            )
            if status_match is not None:
                status = status_match.group(1)
                status_counts[status] = status_counts.get(status, 0) + 1
    except (OSError, UnicodeError, ValueError):
        return {**base, "exists": path.exists() or path.is_symlink(), "valid": False}
    return {
        **base,
        "exists": True,
        "valid": True,
        "line_count": len(lines),
        "recognized_line_count": recognized,
        "unrecognized_line_count": len(lines) - recognized,
        "status_counts": status_counts,
        "latest_timestamp": latest_timestamp,
    }


def _absolute_records(
    targets: Sequence[dict[str, object]], config: InventoryConfig
) -> dict[Path, dict[str, object]]:
    roots = {target.name: target.path for target in config.targets}
    records: dict[Path, dict[str, object]] = {}
    for target in targets:
        root = roots[str(target["name"])]
        if target["exists"]:
            records[root] = target
        for entry in target.get("entries", []):
            records[root / str(entry["path"])] = entry
    return records


def _release_summaries(
    records: dict[Path, dict[str, object]],
    environments: dict[Path, dict[str, object]],
    manifests: dict[Path, dict[str, object]],
    config: InventoryConfig,
) -> list[dict[str, object]]:
    summaries = []
    release_roots = [
        target.path
        for target in config.targets
        if target.name
        in {"configuration", "state", "legacy_release_state", "canonical_release_state"}
    ]

    def belongs_to_release_root(path: Path) -> bool:
        for root in release_roots:
            try:
                path.relative_to(root)
                return True
            except ValueError:
                continue
        return False

    release_directories = sorted(
        (
            path
            for path, record in records.items()
            if record.get("kind") == "directory"
            and RELEASE_PATTERN.fullmatch(path.name) is not None
            and belongs_to_release_root(path)
        ),
        key=str,
    )
    for directory in release_directories:
        members = {
            path.name: record
            for path, record in records.items()
            if path.parent == directory
        }
        member_names = set(members)
        manifest = manifests.get(directory / "manifest.sha256")
        environment = environments.get(directory / ".env")
        hashes_match = False
        if manifest is not None and manifest.get("valid"):
            entries = manifest["entries"]
            hashes_match = all(
                members.get(name, {}).get("sha256") == digest
                for name, digest in entries.items()
            )
        valid = (
            member_names == RELEASE_FILES
            and manifest is not None
            and manifest.get("valid") is True
            and environment is not None
            and environment.get("valid") is True
            and hashes_match
        )
        summaries.append(
            {
                "path": str(directory),
                "release": directory.name,
                "members": sorted(member_names),
                "member_set_valid": member_names == RELEASE_FILES,
                "manifest_valid": manifest is not None
                and manifest.get("valid") is True,
                "environment_valid": environment is not None
                and environment.get("valid") is True,
                "hashes_match": hashes_match,
                "valid": valid,
            }
        )
    return summaries


def _active_release_verification(
    records: dict[Path, dict[str, object]],
    activation_states: Sequence[dict[str, object]],
    releases: Sequence[dict[str, object]],
) -> dict[str, object]:
    valid_states = [state for state in activation_states if state.get("valid")]
    if not valid_states:
        return {"status": "not_applicable"}
    history = valid_states[0]["history"]
    if not history:
        return {"status": "not_applicable"}
    active_release = history[0]
    release_paths = [
        Path(str(release["path"]))
        for release in releases
        if release["release"] == active_release and release["valid"]
    ]
    active_directories = []
    for path, record in records.items():
        if record.get("kind") != "directory":
            continue
        if RELEASE_PATTERN.fullmatch(path.name) is not None or "rollback" in path.parts:
            continue
        if path / ".env" in records and path / "docker-compose-prod.yml" in records:
            active_directories.append(path)
    checks = []
    for active in sorted(active_directories, key=str):
        for release in sorted(release_paths, key=str):
            matches = all(
                records[active / name].get("sha256")
                == records[release / name].get("sha256")
                for name in (".env", "docker-compose-prod.yml")
            )
            checks.append(
                {
                    "active_path": str(active),
                    "release_path": str(release),
                    "matches": matches,
                }
            )
    matching = [check for check in checks if check["matches"]]
    status = "verified" if len(matching) == 1 else "mismatch"
    return {
        "status": status,
        "release": active_release,
        "checks": checks,
    }


@contextmanager
def _held_operation_locks(
    paths: Sequence[Path],
) -> Iterator[list[dict[str, object]]]:
    summaries: list[dict[str, object]] = []
    descriptors: list[int] = []
    try:
        for path in sorted(paths, key=str):
            try:
                metadata = path.lstat()
            except FileNotFoundError:
                summaries.append(
                    {"path": str(path), "exists": False, "status": "missing"}
                )
                continue
            except OSError:
                summaries.append(
                    {"path": str(path), "exists": True, "status": "unreadable"}
                )
                continue
            if not stat.S_ISREG(metadata.st_mode):
                summaries.append(
                    {"path": str(path), "exists": True, "status": "invalid"}
                )
                continue
            if os.name != "posix":
                summaries.append(
                    {"path": str(path), "exists": True, "status": "unsupported"}
                )
                continue
            try:
                import fcntl

                descriptor = _open_readonly(path)
                try:
                    fcntl.flock(descriptor, fcntl.LOCK_SH | fcntl.LOCK_NB)
                except BlockingIOError:
                    os.close(descriptor)
                    summaries.append(
                        {"path": str(path), "exists": True, "status": "busy"}
                    )
                    continue
                descriptors.append(descriptor)
                summaries.append(
                    {"path": str(path), "exists": True, "status": "held_shared"}
                )
            except OSError:
                summaries.append(
                    {"path": str(path), "exists": True, "status": "unreadable"}
                )
        yield summaries
    finally:
        for descriptor in descriptors:
            os.close(descriptor)


def _collect_inventory_locked(
    config: InventoryConfig,
    *,
    generated_at: Optional[str],
    lock_checks: Sequence[dict[str, object]],
    sudo_runner: Callable[..., subprocess.CompletedProcess[bytes]],
) -> dict[str, object]:
    if config.max_entries < 1 or config.max_hash_bytes < 1:
        raise ValueError("inventory limits must be positive")
    targets = [_inventory_target(target, config) for target in config.targets]
    records = _absolute_records(targets, config)

    discovered: dict[str, set[Path]] = {
        "activation-state.json": set(config.activation_state_paths),
        "activation-intent.json": set(config.activation_intent_paths),
        "last-successful-run": set(config.legacy_run_paths),
    }
    target_roots = {target.name: target.path for target in config.targets}
    for target in targets:
        root = target_roots[str(target["name"])]
        if root.name in discovered and target["exists"]:
            discovered[root.name].add(root)
        for entry in target.get("entries", []):
            relative = Path(str(entry["path"]))
            if relative.name in discovered:
                discovered[relative.name].add(root / relative)

    activation_states = [
        _activation_state_summary(path)
        for path in sorted(discovered["activation-state.json"], key=str)
    ]
    activation_intents = [
        _activation_intent_summary(path)
        for path in sorted(discovered["activation-intent.json"], key=str)
    ]
    legacy_runs = [
        _legacy_run_summary(path)
        for path in sorted(discovered["last-successful-run"], key=str)
    ]
    environment_files = {
        path: _environment_summary(path)
        for path, record in records.items()
        if path.name == ".env" and record.get("kind") == "file"
    }
    manifest_files = {
        path: _manifest_summary(path)
        for path, record in records.items()
        if path.name == "manifest.sha256" and record.get("kind") == "file"
    }
    releases = _release_summaries(records, environment_files, manifest_files, config)
    active_release_verification = _active_release_verification(
        records, activation_states, releases
    )
    targets_by_name = {target.name: target.path for target in config.targets}
    authorized_keys = (
        _authorized_keys_summary(targets_by_name["deploy_authorized_keys"])
        if "deploy_authorized_keys" in targets_by_name
        else None
    )
    sudo_policy = (
        _sudo_policy_summary(targets_by_name["sudo_policy"])
        if "sudo_policy" in targets_by_name
        else None
    )
    effective_sudo_policy = (
        _effective_sudo_policy_summary(sudo_runner)
        if "sudo_policy" in targets_by_name
        else None
    )
    logrotate_policy = (
        _logrotate_summary(targets_by_name["commonex_logrotate_policy"])
        if "commonex_logrotate_policy" in targets_by_name
        else None
    )
    retained_release_ids = {
        release_id
        for summary in activation_states
        if summary.get("valid") is True
        for release_id in summary.get("history", [])
        if isinstance(release_id, str)
    }
    retained_releases = [
        release
        for release in releases
        if release.get("release") in retained_release_ids
    ]
    audit_paths = {
        target.path
        for target in config.targets
        if target.name in {"legacy_audit_log", "canonical_audit_log"}
        and target.path in records
    }
    configuration_root = targets_by_name.get("configuration")
    if configuration_root is not None:
        consolidated_audit = configuration_root / "deploy.log"
        if consolidated_audit in records:
            audit_paths.add(consolidated_audit)
    audit_logs = [_audit_summary(path) for path in sorted(audit_paths)]
    blockers: list[str] = []
    issues: list[str] = []
    trusted_metadata_compatible = True

    def require_metadata(path: Path, kind: str, mode: str) -> None:
        nonlocal trusted_metadata_compatible
        record = records.get(path)
        if record is None:
            trusted_metadata_compatible = False
            return
        if (
            record.get("kind") != kind
            or (
                os.name == "posix"
                and (
                    record.get("mode") != mode
                    or record.get("uid") != 0
                    or record.get("gid") != 0
                )
            )
        ):
            trusted_metadata_compatible = False

    for summary in (*activation_states, *activation_intents, *legacy_runs):
        if summary["exists"]:
            require_metadata(Path(str(summary["path"])), "file", "0600")
    for release in retained_releases:
        release_path = Path(str(release["path"]))
        require_metadata(release_path, "directory", "0700")
        require_metadata(release_path / ".env", "file", "0600")
        require_metadata(
            release_path / "docker-compose-prod.yml", "file", "0644"
        )
        require_metadata(release_path / "manifest.sha256", "file", "0600")
    active_history_exists = any(
        summary["exists"] and summary.get("history")
        for summary in activation_states
    )
    serving_configuration_required = (
        "configuration" in config.required_target_names
        or any(
            summary["exists"]
            for summary in (*activation_states, *legacy_runs)
        )
    )
    if configuration_root is not None and serving_configuration_required:
        canonical_app = configuration_root / "app"
        require_metadata(canonical_app, "directory", "0755")
        for name, mode in ((".env", "0600"), ("docker-compose-prod.yml", "0644")):
            require_metadata(canonical_app / name, "file", mode)
        if active_history_exists:
            verified_checks = active_release_verification.get("checks", [])
            matching_paths = {
                check.get("active_path")
                for check in verified_checks
                if isinstance(check, dict) and check.get("matches") is True
            }
            if matching_paths != {str(canonical_app)}:
                blockers.append("active_configuration_not_canonical")
    for target_name in ("legacy_release_state", "canonical_release_state"):
        root = targets_by_name.get(target_name)
        if root is not None and root in records:
            require_metadata(root, "directory", "0700")
    for target_name, mode in (
        ("sudo_policy", "0440"),
        ("commonex_logrotate_policy", "0644"),
    ):
        policy_path = targets_by_name.get(target_name)
        if policy_path is not None and policy_path in records:
            require_metadata(policy_path, "file", mode)
    if not trusted_metadata_compatible:
        blockers.append("incompatible_tool_metadata")
    if any(summary["exists"] for summary in activation_intents):
        blockers.append("activation_intent_present")
    if any(
        summary["exists"] and not summary.get("valid", False)
        for summary in (*activation_states, *legacy_runs)
    ):
        blockers.append("invalid_activation_state")
    if any(
        summary["exists"] and not summary.get("valid", False)
        for summary in activation_intents
    ):
        blockers.append("invalid_activation_intent")
    existing_states = [summary for summary in activation_states if summary["exists"]]
    if len(existing_states) > 1:
        blockers.append("multiple_activation_states")
    existing_legacy_runs = [summary for summary in legacy_runs if summary["exists"]]
    if len(existing_legacy_runs) > 1:
        blockers.append("multiple_legacy_runs")
    if any(not release["valid"] for release in retained_releases):
        blockers.append("invalid_release")
    if active_release_verification["status"] == "mismatch":
        blockers.append("active_release_unverified")
    if any(
        summary is not None and summary["exists"] and not summary["valid"]
        for summary in (authorized_keys, sudo_policy)
    ) or (
        effective_sudo_policy is not None
        and not effective_sudo_policy["valid"]
    ):
        blockers.append("invalid_security_policy")
    for name, summary in (
        ("authorized_keys", authorized_keys),
        ("sudo_policy", sudo_policy),
    ):
        if summary is not None and not summary["exists"]:
            issues.append(f"missing_security_policy:{name}")
    if logrotate_policy is not None:
        if logrotate_policy["exists"] and not logrotate_policy["valid"]:
            blockers.append("invalid_logrotate_policy")
    audit_targets_configured = any(
        target.name in {"legacy_audit_log", "canonical_audit_log"}
        for target in config.targets
    )
    if existing_states and audit_targets_configured and not audit_logs:
        blockers.append("missing_audit_log")
    if any(not summary["valid"] for summary in audit_logs):
        blockers.append("invalid_audit_log")
    targets_by_report_name = {str(target["name"]): target for target in targets}
    for required_name in config.required_target_names:
        target = targets_by_report_name.get(required_name)
        if target is None or not target["exists"]:
            issues.append(f"missing_required_target:{required_name}")
    for target in targets:
        name = str(target["name"])
        if target.get("readable") is False:
            issues.append(f"unreadable_target:{name}")
        if target.get("truncated") is True:
            issues.append(f"truncated_target:{name}")
        for warning in target.get("warnings", []):
            issues.append(f"{name}:{warning}")
        records = [target, *target.get("entries", [])]
        for record in records:
            if record.get("kind") in {
                "socket",
                "fifo",
                "character_device",
                "block_device",
                "unknown",
            }:
                issues.append(
                    f"special_entry:{name}:{record.get('path', target['path'])}:"
                    f"{record['kind']}"
                )
            if record.get("kind") == "file" and int(record.get("links", 1)) > 1:
                issues.append(
                    f"hardlinked_file:{name}:{record.get('path', target['path'])}"
                )
            hash_status = record.get("hash_status")
            if hash_status is not None and hash_status != "hashed":
                issues.append(
                    f"incomplete_hash:{name}:{record.get('path', target['path'])}:"
                    f"{hash_status}"
                )
    if config.operation_lock_paths:
        held_locks = [check for check in lock_checks if check["status"] == "held_shared"]
        if not held_locks:
            issues.append("operation_lock:not_held")
        for check in lock_checks:
            if check["status"] in {"busy", "invalid", "unreadable", "unsupported"}:
                issues.append(
                    f"operation_lock:{check['path']}:{check['status']}"
                )
    status = "incomplete" if issues else "blocked" if blockers else "complete"
    detected_layouts = []
    existing_target_names = {
        str(target["name"]) for target in targets if target["exists"]
    }
    if existing_target_names & {
        "legacy_release_state",
        "legacy_audit_log",
        "legacy_operation_lock",
    }:
        detected_layouts.append("legacy_split")
    if existing_target_names & {
        "tool_installation",
        "canonical_release_state",
        "canonical_audit_logs",
        "canonical_runtime",
    }:
        detected_layouts.append("canonical_namespaced")
    if any(
        summary["exists"]
        and Path(str(summary["path"])).parts[:3] == ("/", "etc", "commonex")
        for summary in (*activation_states, *activation_intents, *legacy_runs)
    ):
        detected_layouts.append("etc_commonex_state")
    collector_path = Path(__file__)
    collector_metadata = collector_path.lstat()
    collector_hash, collector_hash_status = _hash_file(
        collector_path, collector_metadata, max(config.max_hash_bytes, collector_metadata.st_size)
    )
    return {
        "schema_version": 1,
        "generated_at": generated_at if generated_at is not None else timestamp(),
        "status": status,
        "migration_blocked": bool(blockers or issues),
        "blockers": blockers,
        "inventory_issues": issues,
        "detected_layouts": detected_layouts,
        "operation_locks": list(lock_checks),
        "collector": {
            "path": str(collector_path),
            "sha256": collector_hash,
            "hash_status": collector_hash_status,
            "python_version": platform.python_version(),
            "effective_uid": os.geteuid() if hasattr(os, "geteuid") else None,
        },
        "targets": targets,
        "activation_states": activation_states,
        "activation_intents": activation_intents,
        "legacy_runs": legacy_runs,
        "environment_files": [
            environment_files[path] for path in sorted(environment_files, key=str)
        ],
        "manifest_files": [
            manifest_files[path] for path in sorted(manifest_files, key=str)
        ],
        "releases": releases,
        "active_release_verification": active_release_verification,
        "security_policies": {
            "authorized_keys": authorized_keys,
            "sudo": sudo_policy,
            "effective_sudo": effective_sudo_policy,
        },
        "logrotate_policy": logrotate_policy,
        "audit_logs": audit_logs,
    }


def collect_inventory(
    config: InventoryConfig = DEFAULT_CONFIG,
    *,
    generated_at: Optional[str] = None,
    sudo_runner: Callable[..., subprocess.CompletedProcess[bytes]] = subprocess.run,
) -> dict[str, object]:
    with _held_operation_locks(config.operation_lock_paths) as lock_checks:
        return _collect_inventory_locked(
            config,
            generated_at=generated_at,
            lock_checks=lock_checks,
            sudo_runner=sudo_runner,
        )


def serialize_report(report: dict[str, object]) -> str:
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def _effective_uid() -> int:
    if not hasattr(os, "geteuid"):
        raise OSError("effective user ID is unavailable")
    return os.geteuid()


def main(
    config: InventoryConfig = DEFAULT_CONFIG,
    *,
    geteuid: Callable[[], int] = _effective_uid,
    generated_at: Callable[[], str] = timestamp,
) -> int:
    if geteuid() != 0:
        raise PermissionError("commonex host inventory must run as root")
    report = collect_inventory(config, generated_at=generated_at())
    sys.stdout.write(serialize_report(report))
    return {"complete": 0, "blocked": 2, "incomplete": 1}[str(report["status"])]


def run_cli(
    config: InventoryConfig = DEFAULT_CONFIG,
    *,
    geteuid: Callable[[], int] = _effective_uid,
    generated_at: Callable[[], str] = timestamp,
) -> int:
    try:
        return main(config, geteuid=geteuid, generated_at=generated_at)
    except Exception:
        print("commonex-host-inventory: unable to collect inventory", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(run_cli())
