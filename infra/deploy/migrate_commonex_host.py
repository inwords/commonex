#!/usr/bin/env python3
"""Plan, apply, or roll back the one-time CommonEx host-layout migration.

The migration consumes a complete report produced by
``inventory_commonex_host.py``.  It revalidates every copied input against that
report, stages and verifies data and tool code before changing the stable tool
entrypoint, and retains the old layout unchanged for rollback.  There are no
runtime fallback reads from the old layout.
"""

import argparse
from contextlib import contextmanager, ExitStack
from dataclasses import dataclass
from datetime import datetime, timezone
import errno
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
from typing import Dict, Iterator, List, Mapping, Optional, Sequence, Tuple
import uuid

try:
    from infra.deploy import install_commonex_deploy as installer
except ImportError:  # Supports direct execution from infra/deploy on the host.
    import install_commonex_deploy as installer  # type: ignore


RECEIPT_NAME = "host-layout-migration.json"


def _unique_json_object(pairs: List[Tuple[str, object]]) -> Dict[str, object]:
    value: Dict[str, object] = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("duplicate JSON object key")
        value[key] = item
    return value


class MigrationError(RuntimeError):
    """A fail-closed migration or rollback error."""


@dataclass(frozen=True)
class MigrationLayout:
    legacy_state: Path
    canonical_state: Path
    legacy_audit: Path
    canonical_audit: Path
    legacy_lock: Path
    canonical_lock: Path
    rollback_root: Path
    installer: installer.InstallLayout
    mixed_configuration_state: bool = False

    @classmethod
    def canonical(
        cls,
        source_state_root: Optional[Path] = None,
        source_audit: Optional[Path] = None,
        source_lock: Optional[Path] = None,
    ) -> "MigrationLayout":
        return cls(
            legacy_state=(
                source_state_root
                if source_state_root is not None
                else Path("/var/lib/commonex-releases")
            ),
            canonical_state=Path("/var/lib/commonex"),
            legacy_audit=(
                source_audit
                if source_audit is not None
                else Path("/var/log/commonex-deploy.log")
            ),
            canonical_audit=Path("/var/log/commonex/deploy.log"),
            legacy_lock=(
                source_lock
                if source_lock is not None
                else Path("/run/lock/commonex-deploy.lock")
            ),
            canonical_lock=Path("/run/commonex/deploy.lock"),
            rollback_root=Path("/var/lib/commonex-migration-rollbacks"),
            installer=installer.InstallLayout.canonical(),
            mixed_configuration_state=(source_state_root == Path("/etc/commonex")),
        )


def _require_root(require_root: bool) -> None:
    if not require_root:
        return
    if not hasattr(os, "geteuid") or os.geteuid() != 0:
        raise MigrationError("applying the canonical host migration requires root")


def _fsync_directory(path: Path) -> None:
    if os.name != "posix":
        return
    descriptor = os.open(str(path), os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _fsync_file(path: Path) -> None:
    if os.name != "posix":
        return
    descriptor = os.open(str(path), os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _fsync_tree(root: Path) -> None:
    if os.name != "posix":
        return
    for path in sorted(root.rglob("*"), reverse=True):
        if path.is_file() and not path.is_symlink():
            _fsync_file(path)
        elif path.is_dir() and not path.is_symlink():
            _fsync_directory(path)
    _fsync_directory(root)


def _ensure_durable_directory(
    path: Path,
    *,
    mode: int,
    exist_ok: bool = True,
) -> None:
    path.mkdir(parents=True, mode=mode, exist_ok=exist_ok)
    parent = path.parent
    while True:
        _fsync_directory(parent)
        if parent == parent.parent:
            break
        parent = parent.parent


def _durable_move(source: Path, destination: Path) -> None:
    source_is_directory = source.is_dir() and not source.is_symlink()
    if source_is_directory:
        _fsync_tree(source)
    else:
        _fsync_file(source)
    try:
        os.replace(str(source), str(destination))
    except OSError as error:
        if error.errno != errno.EXDEV:
            raise
        if source_is_directory:
            shutil.copytree(source, destination, copy_function=shutil.copy2)
            _fsync_tree(destination)
        else:
            shutil.copy2(source, destination, follow_symlinks=False)
            _fsync_file(destination)
        _fsync_directory(destination.parent)
        if source_is_directory:
            shutil.rmtree(source)
        else:
            source.unlink()
        _fsync_directory(source.parent)
        return
    _fsync_directory(destination.parent)
    if source.parent != destination.parent:
        _fsync_directory(source.parent)


def _atomic_write(path: Path, value: Dict[str, object], mode: int = 0o600) -> None:
    content = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".migration-", dir=str(path.parent))
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        os.replace(str(temporary), str(path))
        _fsync_directory(path.parent)
    finally:
        if temporary.exists():
            temporary.unlink()


def _load_report(
    path: Path,
    *,
    enforce_root_ownership: bool = True,
) -> Dict[str, object]:
    try:
        _verify_trusted_directory_ancestors(
            path,
            "inventory report",
            enforce_root_ownership=enforce_root_ownership,
        )
        path_metadata = path.lstat()
        if not stat.S_ISREG(path_metadata.st_mode):
            raise MigrationError("inventory report is not a trusted regular file")
        if os.name == "posix" and (
            stat.S_IMODE(path_metadata.st_mode) != 0o600
            or (
                enforce_root_ownership
                and (path_metadata.st_uid != 0 or path_metadata.st_gid != 0)
            )
        ):
            raise MigrationError("inventory report is not root-owned and immutable")
        flags = (
            os.O_RDONLY
            | getattr(os, "O_BINARY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0)
        )
        descriptor = os.open(str(path), flags)
        try:
            opened = os.fstat(descriptor)
            if (
                not stat.S_ISREG(opened.st_mode)
                or (opened.st_dev, opened.st_ino)
                != (path_metadata.st_dev, path_metadata.st_ino)
                or (
                    os.name == "posix"
                    and (
                        stat.S_IMODE(opened.st_mode) != 0o600
                        or (
                            enforce_root_ownership
                            and (opened.st_uid != 0 or opened.st_gid != 0)
                        )
                    )
                )
            ):
                raise MigrationError("inventory report is not root-owned and immutable")
            maximum_bytes = 100 * 1024 * 1024
            if opened.st_size > maximum_bytes:
                raise MigrationError("inventory report exceeds the supported size")
            chunks = []
            total = 0
            while chunk := os.read(descriptor, min(1024 * 1024, maximum_bytes + 1)):
                chunks.append(chunk)
                total += len(chunk)
                if total > maximum_bytes:
                    raise MigrationError("inventory report exceeds the supported size")
            after = os.fstat(descriptor)
            if (
                after.st_size,
                after.st_mtime_ns,
                after.st_ctime_ns,
            ) != (
                opened.st_size,
                opened.st_mtime_ns,
                opened.st_ctime_ns,
            ):
                raise MigrationError("inventory report changed while it was read")
        finally:
            os.close(descriptor)
        value = json.loads(
            b"".join(chunks).decode("utf-8"),
            object_pairs_hook=_unique_json_object,
        )
    except (OSError, UnicodeError, ValueError, TypeError) as error:
        raise MigrationError("unable to read a valid inventory report") from error
    if not isinstance(value, dict):
        raise MigrationError("inventory report must be a JSON object")
    return value


def _targets(report: Dict[str, object]) -> Dict[str, Dict[str, object]]:
    raw_targets = report.get("targets")
    if not isinstance(raw_targets, list):
        raise MigrationError("inventory report has no target records")
    result: Dict[str, Dict[str, object]] = {}
    for value in raw_targets:
        if not isinstance(value, dict) or not isinstance(value.get("name"), str):
            raise MigrationError("inventory report contains an invalid target record")
        name = str(value["name"])
        if name in result:
            raise MigrationError("inventory report contains duplicate target records")
        result[name] = value
    return result


def _intent_exists(report: Dict[str, object]) -> bool:
    intents = report.get("activation_intents")
    if not isinstance(intents, list):
        raise MigrationError("inventory report has no Activation Intent inventory")
    for intent in intents:
        if not isinstance(intent, dict):
            raise MigrationError("inventory report has an invalid Activation Intent record")
        if intent.get("exists") is True:
            return True
    return False


def _validate_report(report: Dict[str, object]) -> None:
    if report.get("schema_version") != 1:
        raise MigrationError("unsupported inventory report schema")
    if _intent_exists(report):
        raise MigrationError("Activation Intent exists; migration is forbidden")
    if (
        report.get("status") != "complete"
        or report.get("migration_blocked") is not False
        or report.get("blockers") != []
        or report.get("inventory_issues") != []
    ):
        raise MigrationError("migration requires a complete inventory with no blockers")
    states = report.get("activation_states")
    if not isinstance(states, list):
        raise MigrationError("inventory report has no activation-state inventory")
    existing_states = [state for state in states if isinstance(state, dict) and state.get("exists")]
    if existing_states:
        verification = report.get("active_release_verification")
        if not isinstance(verification, dict) or verification.get("status") != "verified":
            raise MigrationError("Active Release is not verified by the inventory")


def _target_for_path(
    targets: Dict[str, Dict[str, object]],
    preferred_name: str,
    path: Path,
) -> Dict[str, object]:
    preferred = targets.get(preferred_name)
    if preferred is not None and preferred.get("path") == str(path):
        return preferred
    matches = [target for target in targets.values() if target.get("path") == str(path)]
    for target in targets.values():
        try:
            relative = path.relative_to(Path(str(target.get("path")))).as_posix()
        except ValueError:
            continue
        entries = target.get("entries", [])
        if not isinstance(entries, list):
            continue
        matches.extend(
            {**entry, "exists": True, "readable": True}
            for entry in entries
            if isinstance(entry, dict) and entry.get("path") == relative
        )
    if len(matches) != 1:
        raise MigrationError("inventory does not contain the exact migration path: " + str(path))
    return matches[0]


def _kind(metadata: os.stat_result) -> str:
    if stat.S_ISREG(metadata.st_mode):
        return "file"
    if stat.S_ISDIR(metadata.st_mode):
        return "directory"
    if stat.S_ISLNK(metadata.st_mode):
        return "symlink"
    return "special"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    descriptor = os.open(
        str(path),
        os.O_RDONLY
        | getattr(os, "O_BINARY", 0)
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                return digest.hexdigest()
            digest.update(chunk)
    finally:
        os.close(descriptor)


def _sha256_prefix(path: Path, size: int) -> str:
    digest = hashlib.sha256()
    descriptor = os.open(
        str(path), os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        remaining = size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            if not chunk:
                raise MigrationError("canonical audit was truncated after migration")
            digest.update(chunk)
            remaining -= len(chunk)
        return digest.hexdigest()
    finally:
        os.close(descriptor)


def _audit_preserves_migrated_prefix(path: Path, receipt: Dict[str, object]) -> bool:
    expected_size = receipt.get("audit_initial_size")
    expected_hash = receipt.get("audit_sha256")
    if not isinstance(expected_size, int) or expected_size < 0 or not isinstance(expected_hash, str):
        raise MigrationError("migration receipt has invalid audit evidence")
    try:
        if path.stat().st_size < expected_size:
            return False
        return _sha256_prefix(path, expected_size) == expected_hash
    except OSError:
        return False


def _assert_metadata(path: Path, record: Dict[str, object], label: str) -> None:
    try:
        metadata = path.lstat()
        link_target = os.readlink(path) if stat.S_ISLNK(metadata.st_mode) else None
    except OSError as error:
        raise MigrationError(label + " changed since inventory") from error
    if _kind(metadata) != record.get("kind"):
        raise MigrationError(label + " changed since inventory")
    for key, actual in (
        ("mode", f"{stat.S_IMODE(metadata.st_mode):04o}"),
        ("uid", metadata.st_uid),
        ("gid", metadata.st_gid),
    ):
        if key in record and record.get(key) != actual:
            raise MigrationError(label + " changed since inventory")
    if stat.S_ISREG(metadata.st_mode):
        if record.get("hash_status") != "hashed" or record.get("sha256") != _sha256(path):
            raise MigrationError(label + " changed since inventory")
    if stat.S_ISLNK(metadata.st_mode) and record.get("link_target") != link_target:
        raise MigrationError(label + " changed since inventory")


def _verify_created_lock(
    path: Path,
    label: str,
    *,
    enforce_root_ownership: bool,
    locked_files: Mapping[Path, Tuple[int, int]],
) -> None:
    try:
        metadata = path.lstat()
    except OSError as error:
        raise MigrationError(label + " changed since inventory") from error
    if not stat.S_ISREG(metadata.st_mode):
        raise MigrationError(label + " changed since inventory")
    if locked_files.get(path) != (metadata.st_dev, metadata.st_ino):
        raise MigrationError(label + " changed since inventory")
    if os.name == "posix":
        if stat.S_IMODE(metadata.st_mode) != 0o600:
            raise MigrationError(label + " changed since inventory")
        if enforce_root_ownership and (
            metadata.st_uid != 0 or metadata.st_gid != 0
        ):
            raise MigrationError(label + " changed since inventory")


def _verify_created_lock_directory(
    path: Path, label: str, *, enforce_root_ownership: bool
) -> None:
    try:
        metadata = path.lstat()
    except OSError as error:
        raise MigrationError(label + " changed since inventory") from error
    if not stat.S_ISDIR(metadata.st_mode):
        raise MigrationError(label + " changed since inventory")
    if os.name == "posix":
        if stat.S_IMODE(metadata.st_mode) != 0o755:
            raise MigrationError(label + " changed since inventory")
        if enforce_root_ownership and (
            metadata.st_uid != 0 or metadata.st_gid != 0
        ):
            raise MigrationError(label + " changed since inventory")
    _verify_trusted_directory_ancestors(
        path,
        label,
        enforce_root_ownership=enforce_root_ownership,
    )


def _verify_trusted_directory_ancestors(
    path: Path,
    label: str,
    *,
    enforce_root_ownership: bool,
) -> None:
    if os.name != "posix" or not enforce_root_ownership:
        return
    for ancestor in path.parents:
        try:
            metadata = ancestor.lstat()
        except OSError as error:
            raise MigrationError(label + " changed since inventory") from error
        if (
            not stat.S_ISDIR(metadata.st_mode)
            or metadata.st_uid != 0
            or metadata.st_gid != 0
            or stat.S_IMODE(metadata.st_mode) & 0o022
        ):
            raise MigrationError(label + " changed since inventory")


def _verify_canonical_runtime(
    path: Path,
    *,
    enforce_root_ownership: bool,
) -> None:
    _verify_trusted_directory_ancestors(
        path,
        "canonical operation-lock directory",
        enforce_root_ownership=enforce_root_ownership,
    )
    if path.exists() or path.is_symlink():
        _verify_created_lock_directory(
            path,
            "canonical operation-lock directory",
            enforce_root_ownership=enforce_root_ownership,
        )


def _verify_lock_namespace(path: Path, *, enforce_root_ownership: bool) -> None:
    if os.name != "posix" or not enforce_root_ownership:
        return
    for directory in (path.parent, *path.parent.parents):
        try:
            metadata = directory.lstat()
        except OSError as error:
            raise MigrationError("operation-lock namespace is not trusted") from error
        if (
            not stat.S_ISDIR(metadata.st_mode)
            or metadata.st_uid != 0
            or metadata.st_gid != 0
            or stat.S_IMODE(metadata.st_mode) & 0o002
        ):
            raise MigrationError("operation-lock namespace is not trusted")


def _verify_existing_operation_lock(
    path: Path,
    *,
    enforce_root_ownership: bool,
) -> None:
    if not path.exists() and not path.is_symlink():
        return
    _verify_lock_namespace(
        path,
        enforce_root_ownership=enforce_root_ownership,
    )
    try:
        metadata = path.lstat()
    except OSError as error:
        raise MigrationError("operation-lock path is not trusted") from error
    if not stat.S_ISREG(metadata.st_mode) or (
        os.name == "posix"
        and (
            stat.S_IMODE(metadata.st_mode) != 0o600
            or (
                enforce_root_ownership
                and (metadata.st_uid != 0 or metadata.st_gid != 0)
            )
        )
    ):
        raise MigrationError("operation-lock path is not trusted")


def _allowed_created_entries(
    root: Path, locked_files: Mapping[Path, Tuple[int, int]]
) -> Dict[str, str]:
    entries: Dict[str, str] = {}
    for path in locked_files:
        try:
            relative = path.relative_to(root)
        except ValueError:
            continue
        if not relative.parts:
            entries[""] = "file"
            continue
        for index in range(1, len(relative.parts)):
            entries[Path(*relative.parts[:index]).as_posix()] = "directory"
        entries[relative.as_posix()] = "file"
    return entries


def _verify_snapshot(
    root: Path,
    target: Dict[str, object],
    label: str,
    *,
    allow_symlinks: bool = False,
    locked_files: Optional[Mapping[Path, Tuple[int, int]]] = None,
    enforce_root_ownership: bool = True,
) -> None:
    held_locks = locked_files if locked_files is not None else {}
    allowed_entries = _allowed_created_entries(root, held_locks)
    if target.get("exists") is not True:
        if not root.exists() and not root.is_symlink():
            if allowed_entries:
                raise MigrationError(label + " changed since inventory")
            return
        if allowed_entries.get("") == "file":
            _verify_created_lock(
                root,
                label,
                enforce_root_ownership=enforce_root_ownership,
                locked_files=held_locks,
            )
            return
        if allowed_entries:
            _verify_created_lock_directory(
                root,
                label,
                enforce_root_ownership=enforce_root_ownership,
            )
            actual = {
                path.relative_to(root).as_posix(): path
                for path in root.rglob("*")
            }
            if set(actual) != set(allowed_entries):
                raise MigrationError(label + " changed since inventory")
            for relative, kind in allowed_entries.items():
                path = actual[relative]
                if kind == "file":
                    _verify_created_lock(
                        path,
                        label,
                        enforce_root_ownership=enforce_root_ownership,
                        locked_files=held_locks,
                    )
                else:
                    _verify_created_lock_directory(
                        path,
                        label,
                        enforce_root_ownership=enforce_root_ownership,
                    )
            return
        raise MigrationError(label + " changed since inventory")
    if allowed_entries.get("") == "file":
        _verify_created_lock(
            root,
            label,
            enforce_root_ownership=enforce_root_ownership,
            locked_files=held_locks,
        )
        allowed_entries.pop("")
    _assert_metadata(root, target, label)
    if target.get("kind") != "directory":
        return
    raw_entries = target.get("entries")
    if not isinstance(raw_entries, list) or target.get("truncated") is True:
        raise MigrationError(label + " has no complete recursive inventory")
    expected: Dict[str, Dict[str, object]] = {}
    for entry in raw_entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            raise MigrationError(label + " has an invalid inventory entry")
        relative = str(entry["path"])
        if relative in expected or Path(relative).is_absolute() or ".." in Path(relative).parts:
            raise MigrationError(label + " has an unsafe inventory entry")
        expected[relative] = entry
    actual = {
        path.relative_to(root).as_posix(): path
        for path in root.rglob("*")
    }
    for relative, kind in allowed_entries.items():
        if relative in expected:
            if kind == "file" and relative in actual:
                _verify_created_lock(
                    actual[relative],
                    label,
                    enforce_root_ownership=enforce_root_ownership,
                    locked_files=held_locks,
                )
            continue
        path = actual.pop(relative, None)
        if path is None:
            raise MigrationError(label + " changed since inventory")
        if kind == "file":
            _verify_created_lock(
                path,
                label,
                enforce_root_ownership=enforce_root_ownership,
                locked_files=held_locks,
            )
        else:
            _verify_created_lock_directory(
                path,
                label,
                enforce_root_ownership=enforce_root_ownership,
            )
    if set(actual) != set(expected):
        raise MigrationError(label + " changed since inventory")
    for relative, record in expected.items():
        _assert_metadata(actual[relative], record, label)
        if record.get("kind") == "special" or (
            record.get("kind") == "symlink" and not allow_symlinks
        ):
            raise MigrationError(label + " contains an unsafe entry")


def _portable_manifest(root: Path, *, exclude_receipt: bool = False) -> List[Dict[str, object]]:
    result: List[Dict[str, object]] = []
    for path in sorted(root.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(root).as_posix()
        if exclude_receipt and relative == RECEIPT_NAME:
            continue
        metadata = path.lstat()
        kind = _kind(metadata)
        if kind not in {"file", "directory"}:
            raise MigrationError("state tree contains an unsafe entry: " + relative)
        record: Dict[str, object] = {
            "path": relative,
            "kind": kind,
            "mode": f"{stat.S_IMODE(metadata.st_mode):04o}",
        }
        if kind == "file":
            record["sha256"] = _sha256(path)
        result.append(record)
    return result


def _manifest_digest(manifest: Sequence[Dict[str, object]]) -> str:
    encoded = json.dumps(list(manifest), sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _copy_ownership(source: Path, destination: Path) -> None:
    if not hasattr(os, "chown"):
        return
    source_metadata = source.lstat()
    os.chown(destination, source_metadata.st_uid, source_metadata.st_gid, follow_symlinks=False)
    if source.is_dir():
        for source_child in source.rglob("*"):
            destination_child = destination / source_child.relative_to(source)
            child_metadata = source_child.lstat()
            os.chown(
                destination_child,
                child_metadata.st_uid,
                child_metadata.st_gid,
                follow_symlinks=False,
            )


def _copy_state(source: Path, destination: Path, preserve_ownership: bool) -> None:
    shutil.copytree(source, destination, copy_function=shutil.copy2)
    if preserve_ownership:
        _copy_ownership(source, destination)
    _fsync_tree(destination)


def _mixed_state_sources(
    report: Dict[str, object], source_root: Path
) -> List[Tuple[Path, Path]]:
    selected: Dict[str, Path] = {}
    activation_state: Optional[Dict[str, object]] = None
    for collection, destination_name in (
        ("activation_states", "activation-state.json"),
        ("activation_intents", "activation-intent.json"),
        ("legacy_runs", "last-successful-run"),
    ):
        values = report.get(collection, [])
        if not isinstance(values, list):
            raise MigrationError("inventory report has invalid state summaries")
        existing = [value for value in values if isinstance(value, dict) and value.get("exists")]
        if len(existing) > 1:
            raise MigrationError("mixed configuration has duplicate state documents")
        if existing:
            source = Path(str(existing[0].get("path")))
            if not source.is_relative_to(source_root):
                raise MigrationError("state document is outside the selected source root")
            selected[destination_name] = source
            if collection == "activation_states":
                activation_state = existing[0]
    if activation_state is None or activation_state.get("valid") is not True:
        raise MigrationError("mixed configuration has no valid activation state")
    history = activation_state.get("history")
    if (
        not isinstance(history, list)
        or not history
        or any(
            not isinstance(release_id, str)
            or installer.GIT_SHA.fullmatch(release_id) is None
            for release_id in history
        )
        or len(set(history)) != len(history)
    ):
        raise MigrationError("mixed configuration has invalid retained releases")
    retained_release_ids = set(history)
    retained_sources: Dict[str, List[Path]] = {
        release_id: [] for release_id in history
    }
    releases = report.get("releases", [])
    if not isinstance(releases, list):
        raise MigrationError("inventory report has invalid release summaries")
    for release in releases:
        if not isinstance(release, dict) or release.get("valid") is not True:
            continue
        release_id = release.get("release")
        source = Path(str(release.get("path")))
        if (
            not isinstance(release_id, str)
            or installer.GIT_SHA.fullmatch(release_id) is None
            or not source.is_relative_to(source_root)
        ):
            raise MigrationError("mixed configuration has an unsafe retained release")
        if release_id in retained_release_ids:
            retained_sources[release_id].append(source)
    for release_id in history:
        sources = retained_sources[release_id]
        if len(sources) != 1:
            raise MigrationError(
                "mixed configuration has no unique valid retained release: "
                + release_id
            )
        selected[release_id] = sources[0]
    rollback = source_root / "rollback"
    if rollback.exists():
        selected["rollback"] = rollback
    if "activation-state.json" not in selected:
        raise MigrationError("mixed configuration has no activation state")
    return [(source, Path(destination)) for destination, source in sorted(selected.items())]


def _copy_mixed_state(
    report: Dict[str, object],
    source_root: Path,
    destination: Path,
    preserve_ownership: bool,
) -> None:
    destination.mkdir(mode=0o700)
    for source, relative in _mixed_state_sources(report, source_root):
        target = destination / relative
        if source.is_symlink():
            raise MigrationError("mixed configuration state contains a symlink")
        if source.is_dir():
            shutil.copytree(source, target, copy_function=shutil.copy2)
        elif source.is_file():
            shutil.copy2(source, target, follow_symlinks=False)
        else:
            raise MigrationError("mixed configuration state contains a special file")
        if preserve_ownership:
            _copy_ownership(source, target)
        if source.is_dir():
            if _portable_manifest(source) != _portable_manifest(target):
                raise MigrationError("copied mixed-layout state failed verification")
        elif _sha256(source) != _sha256(target):
            raise MigrationError("copied mixed-layout state failed verification")
    _fsync_tree(destination)


def _copy_audit(source: Path, destination: Path, preserve_ownership: bool) -> None:
    destination.parent.mkdir(parents=True, mode=0o755, exist_ok=True)
    shutil.copy2(source, destination, follow_symlinks=False)
    if preserve_ownership:
        _copy_ownership(source, destination)
    _fsync_file(destination)
    _fsync_directory(destination.parent)


@contextmanager
def _exclusive_lock(
    path: Path,
    require_lock_support: bool,
    *,
    enforce_root_ownership: bool,
) -> Iterator[Tuple[int, int]]:
    path.parent.mkdir(parents=True, mode=0o755, exist_ok=True)
    flags = (
        os.O_RDWR
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0)
    )
    created = False
    try:
        descriptor = os.open(
            str(path),
            flags | os.O_CREAT | os.O_EXCL,
            0o600,
        )
        created = True
    except FileExistsError:
        try:
            descriptor = os.open(str(path), flags)
        except OSError as error:
            raise MigrationError("operation-lock path is not trusted") from error
    except OSError as error:
        raise MigrationError("operation-lock path is not trusted") from error
    try:
        try:
            import fcntl
        except ImportError:
            if require_lock_support:
                raise MigrationError("POSIX operation-lock support is unavailable")
        else:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or (
            os.name == "posix"
            and enforce_root_ownership
            and (metadata.st_uid != 0 or metadata.st_gid != 0)
        ):
            raise MigrationError("operation-lock path is not trusted")
        if created and os.name == "posix":
            os.fchmod(descriptor, 0o600)
            metadata = os.fstat(descriptor)
        elif os.name == "posix" and stat.S_IMODE(metadata.st_mode) != 0o600:
            raise MigrationError("operation-lock path is not trusted")
        try:
            path_metadata = path.lstat()
        except OSError as error:
            raise MigrationError("operation-lock path is not trusted") from error
        if (
            not stat.S_ISREG(path_metadata.st_mode)
            or (path_metadata.st_dev, path_metadata.st_ino)
            != (metadata.st_dev, metadata.st_ino)
        ):
            raise MigrationError("operation-lock path is not trusted")
        yield metadata.st_dev, metadata.st_ino
    except BlockingIOError as error:
        raise MigrationError("the deployment operation lock is busy") from error
    finally:
        os.close(descriptor)


@contextmanager
def _migration_locks(
    layout: MigrationLayout,
    require_lock_support: bool,
    *,
    enforce_root_ownership: bool,
) -> Iterator[Dict[Path, Tuple[int, int]]]:
    runtime = layout.canonical_lock.parent
    runtime_missing = not runtime.exists() and not runtime.is_symlink()
    runtime.mkdir(parents=True, mode=0o755, exist_ok=True)
    if runtime_missing and os.name == "posix":
        runtime.chmod(0o755)
    _verify_canonical_runtime(
        layout.canonical_lock.parent,
        enforce_root_ownership=enforce_root_ownership,
    )
    _verify_existing_operation_lock(
        layout.legacy_lock,
        enforce_root_ownership=enforce_root_ownership,
    )
    _verify_existing_operation_lock(
        layout.canonical_lock,
        enforce_root_ownership=enforce_root_ownership,
    )
    paths = [layout.legacy_lock]
    if layout.canonical_lock != layout.legacy_lock:
        paths.append(layout.canonical_lock)
    with ExitStack() as stack:
        locked_files: Dict[Path, Tuple[int, int]] = {}
        for path in sorted(paths, key=str):
            _verify_lock_namespace(
                path,
                enforce_root_ownership=enforce_root_ownership,
            )
            locked_files[path] = stack.enter_context(
                _exclusive_lock(
                    path,
                    require_lock_support,
                    enforce_root_ownership=enforce_root_ownership,
                )
            )
        yield locked_files


def _verify_inventory_snapshot(
    targets: Dict[str, Dict[str, object]],
    *,
    locked_files: Optional[Mapping[Path, Tuple[int, int]]] = None,
    enforce_root_ownership: bool = True,
) -> None:
    for name, target in targets.items():
        path = target.get("path")
        if not isinstance(path, str):
            raise MigrationError("inventory target has no valid path: " + name)
        _verify_snapshot(
            Path(path),
            target,
            "inventory target " + name,
            allow_symlinks=name == "configuration",
            locked_files=locked_files,
            enforce_root_ownership=enforce_root_ownership,
        )


def _validate_audit_metadata(
    audit: Dict[str, object], *, enforce_root_ownership: bool
) -> None:
    if os.name != "posix":
        return
    invalid_owner = enforce_root_ownership and (
        audit.get("uid") != 0 or audit.get("gid") != 0
    )
    if audit.get("mode") != "0600" or invalid_owner:
        raise MigrationError(
            "deployment audit log must be owned by root:root with mode 0600"
        )


def _verify_selected_legacy_lock(
    report: Dict[str, object],
    targets: Dict[str, Dict[str, object]],
    layout: MigrationLayout,
    *,
    locked_files: Optional[Mapping[Path, Tuple[int, int]]],
    enforce_root_ownership: bool,
) -> None:
    operation_locks = report.get("operation_locks")
    if not isinstance(operation_locks, list):
        raise MigrationError("inventory report has no operation-lock evidence")
    matches = [
        record
        for record in operation_locks
        if isinstance(record, dict)
        and record.get("path") == str(layout.legacy_lock)
    ]
    if (
        len(matches) != 1
        or matches[0].get("exists") is not True
        or matches[0].get("status") != "held_shared"
    ):
        raise MigrationError(
            "selected legacy lock was not held by the inventory"
        )
    _target_for_path(
        targets,
        "legacy_operation_lock",
        layout.legacy_lock,
    )


def _plan(
    report: Dict[str, object],
    bundle: Path,
    tool_git_sha: str,
    layout: MigrationLayout,
    *,
    enforce_root_ownership: bool = True,
    locked_files: Optional[Mapping[Path, Tuple[int, int]]] = None,
) -> Tuple[Dict[str, object], Dict[str, object], Dict[str, object], Dict[str, object], Dict[str, object]]:
    _validate_report(report)
    targets = _targets(report)
    _verify_inventory_snapshot(
        targets,
        locked_files=locked_files,
        enforce_root_ownership=enforce_root_ownership,
    )
    _target_for_path(
        targets,
        "canonical_runtime",
        layout.canonical_lock.parent,
    )
    _verify_canonical_runtime(
        layout.canonical_lock.parent,
        enforce_root_ownership=enforce_root_ownership,
    )
    _verify_existing_operation_lock(
        layout.legacy_lock,
        enforce_root_ownership=enforce_root_ownership,
    )
    _verify_existing_operation_lock(
        layout.canonical_lock,
        enforce_root_ownership=enforce_root_ownership,
    )
    _verify_selected_legacy_lock(
        report,
        targets,
        layout,
        locked_files=locked_files,
        enforce_root_ownership=enforce_root_ownership,
    )
    legacy_state = _target_for_path(targets, "legacy_release_state", layout.legacy_state)
    canonical_state = _target_for_path(
        targets, "canonical_release_state", layout.canonical_state
    )
    legacy_audit = _target_for_path(targets, "legacy_audit_log", layout.legacy_audit)
    canonical_audit = _target_for_path(
        targets, "canonical_audit_log", layout.canonical_audit
    )
    if legacy_state.get("exists") and canonical_state.get("exists"):
        raise MigrationError("both legacy and canonical state exist; manual reconciliation is required")
    if not legacy_state.get("exists") and not canonical_state.get("exists"):
        raise MigrationError("inventory contains no persistent state to make authoritative")
    if legacy_audit.get("exists") and canonical_audit.get("exists"):
        raise MigrationError("both legacy and canonical audit logs exist; manual reconciliation is required")
    if not legacy_audit.get("exists") and not canonical_audit.get("exists"):
        raise MigrationError("inventory contains no deployment audit log")
    authoritative_audit = legacy_audit if legacy_audit.get("exists") else canonical_audit
    _validate_audit_metadata(
        authoritative_audit, enforce_root_ownership=enforce_root_ownership
    )
    install_plan = installer.plan_install(
        bundle,
        tool_git_sha,
        layout.installer,
        enforce_root_ownership=enforce_root_ownership,
    )
    plan = {
        "status": "planned",
        "tool_git_sha": tool_git_sha,
        "source_state": str(layout.legacy_state),
        "canonical_state": str(layout.canonical_state),
        "source_audit": str(layout.legacy_audit),
        "canonical_audit": str(layout.canonical_audit),
        "retained_rollback_inputs": [str(layout.legacy_state), str(layout.legacy_audit)],
        "tool_install": install_plan,
        "actions": [
            "acquire_legacy_and_canonical_operation_locks",
            "revalidate_complete_inventory_snapshot",
            "copy_and_verify_state_without_removing_source",
            "copy_and_verify_audit_without_removing_source",
            "stage_and_verify_versioned_tool",
            "atomically_switch_tool_authority",
            "write_durable_migration_receipt",
        ],
    }
    if layout.mixed_configuration_state:
        plan["selected_state_paths"] = [
            str(source) for source, _ in _mixed_state_sources(report, layout.legacy_state)
        ]
    return plan, legacy_state, canonical_state, legacy_audit, canonical_audit


def execute_migration(
    report: Dict[str, object],
    bundle: Path,
    tool_git_sha: str,
    layout: MigrationLayout,
    *,
    apply: bool = False,
    require_root: bool = True,
) -> Dict[str, object]:
    plan, _, _, _, _ = _plan(
        report,
        bundle,
        tool_git_sha,
        layout,
        enforce_root_ownership=require_root,
    )
    if not apply:
        return plan
    _require_root(require_root)
    with _migration_locks(
        layout,
        require_lock_support=require_root,
        enforce_root_ownership=require_root,
    ) as locked_files:
        plan, legacy_state, canonical_state, legacy_audit, canonical_audit = _plan(
            report,
            bundle,
            tool_git_sha,
            layout,
            enforce_root_ownership=require_root,
            locked_files=locked_files,
        )
        created_state = bool(legacy_state.get("exists"))
        created_audit = bool(legacy_audit.get("exists"))
        state_staging: Optional[Path] = None
        audit_staging: Optional[Path] = None
        promoted_state = False
        promoted_audit = False
        rollback_directory: Optional[Path] = None
        try:
            if created_state:
                _ensure_durable_directory(
                    layout.canonical_state.parent,
                    mode=0o755,
                )
                state_staging = layout.canonical_state.parent / (
                    ".commonex-state-migration-" + uuid.uuid4().hex
                )
                if layout.mixed_configuration_state:
                    _copy_mixed_state(
                        report, layout.legacy_state, state_staging, require_root
                    )
                else:
                    _copy_state(layout.legacy_state, state_staging, require_root)
                    if _portable_manifest(state_staging) != _portable_manifest(layout.legacy_state):
                        raise MigrationError("copied state failed content and metadata verification")
            if created_audit:
                _ensure_durable_directory(
                    layout.canonical_audit.parent,
                    mode=0o755,
                )
                audit_staging = layout.canonical_audit.parent / (
                    ".deploy-log-migration-" + uuid.uuid4().hex
                )
                _copy_audit(layout.legacy_audit, audit_staging, require_root)
                _assert_metadata(audit_staging, legacy_audit, "copied audit log")

            installer.stage_version(
                bundle, tool_git_sha, layout.installer, require_root=require_root
            )
            if state_staging is not None:
                os.replace(str(state_staging), str(layout.canonical_state))
                promoted_state = True
                _fsync_directory(layout.canonical_state.parent)
            if audit_staging is not None:
                os.replace(str(audit_staging), str(layout.canonical_audit))
                promoted_audit = True
                _fsync_directory(layout.canonical_audit.parent)
            activation = installer.activate_version(
                tool_git_sha, layout.installer, require_root=require_root
            )
            rollback_directory = Path(str(activation["rollback_directory"]))
            migration_id = uuid.uuid4().hex
            receipt_path = layout.canonical_state / RECEIPT_NAME
            state_digest = _manifest_digest(
                _portable_manifest(layout.canonical_state, exclude_receipt=True)
            )
            receipt: Dict[str, object] = {
                "schema_version": 1,
                "migration_id": migration_id,
                "status": "applied",
                "applied_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "tool_git_sha": tool_git_sha,
                "installer_rollback_directory": str(rollback_directory),
                "created_canonical_state": created_state,
                "created_canonical_audit": created_audit,
                "state_manifest_sha256": state_digest,
                "audit_sha256": _sha256(layout.canonical_audit),
                "audit_initial_size": layout.canonical_audit.stat().st_size,
                "legacy_state": str(layout.legacy_state),
                "canonical_state": str(layout.canonical_state),
                "legacy_audit": str(layout.legacy_audit),
                "canonical_audit": str(layout.canonical_audit),
                "rollback_inputs_retained": True,
            }
            _atomic_write(receipt_path, receipt)
            return {
                **plan,
                "status": "applied",
                "receipt": str(receipt_path),
                "installer_rollback_directory": str(rollback_directory),
            }
        except BaseException as migration_error:
            if rollback_directory is not None:
                try:
                    installer.rollback_activation(
                        rollback_directory,
                        layout.installer,
                        apply=True,
                        require_root=require_root,
                    )
                except BaseException as rollback_error:
                    raise MigrationError(
                        "migration failed and tool authority rollback failed; "
                        "canonical data was retained for the authoritative tool"
                    ) from rollback_error
            failure_quarantine = layout.rollback_root / (
                "failed-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
                + "-" + uuid.uuid4().hex
            )
            if promoted_state or promoted_audit:
                _ensure_durable_directory(
                    layout.rollback_root,
                    mode=0o700,
                )
                _ensure_durable_directory(
                    failure_quarantine,
                    mode=0o700,
                    exist_ok=False,
                )
            if promoted_state and layout.canonical_state.exists():
                _durable_move(layout.canonical_state, failure_quarantine / "state")
            if promoted_audit and layout.canonical_audit.exists():
                _durable_move(
                    layout.canonical_audit,
                    failure_quarantine / "deploy.log",
                )
            raise migration_error
        finally:
            if state_staging is not None and state_staging.exists():
                shutil.rmtree(state_staging)
            if audit_staging is not None and audit_staging.exists():
                audit_staging.unlink()


def _read_receipt(
    path: Path,
    layout: MigrationLayout,
    *,
    enforce_root_ownership: bool,
) -> Dict[str, object]:
    expected = layout.canonical_state / RECEIPT_NAME
    if path != expected:
        raise MigrationError("rollback receipt is not at the canonical migration path")
    try:
        state_metadata = layout.canonical_state.lstat()
    except OSError as error:
        raise MigrationError("canonical migration state is not trusted") from error
    if not stat.S_ISDIR(state_metadata.st_mode) or (
        os.name == "posix"
        and (
            stat.S_IMODE(state_metadata.st_mode) & 0o022
            or (
                enforce_root_ownership
                and (state_metadata.st_uid != 0 or state_metadata.st_gid != 0)
            )
        )
    ):
        raise MigrationError("canonical migration state is not trusted")
    _verify_trusted_directory_ancestors(
        layout.canonical_state,
        "canonical migration state",
        enforce_root_ownership=enforce_root_ownership,
    )
    descriptor = -1
    try:
        path_metadata = path.lstat()
        if not stat.S_ISREG(path_metadata.st_mode):
            raise MigrationError("unable to read a valid migration receipt")
        descriptor = os.open(
            str(path),
            os.O_RDONLY
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or (metadata.st_dev, metadata.st_ino)
            != (path_metadata.st_dev, path_metadata.st_ino)
            or metadata.st_size > 1024 * 1024
            or (
                os.name == "posix"
                and (
                    stat.S_IMODE(metadata.st_mode) != 0o600
                    or (
                        enforce_root_ownership
                        and (metadata.st_uid != 0 or metadata.st_gid != 0)
                    )
                )
            )
        ):
            raise MigrationError("unable to read a valid migration receipt")
        with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
            descriptor = -1
            receipt = json.load(stream)
    except (OSError, UnicodeError, ValueError, TypeError) as error:
        raise MigrationError("unable to read a valid migration receipt") from error
    finally:
        if descriptor >= 0:
            os.close(descriptor)
    if (
        not isinstance(receipt, dict)
        or type(receipt.get("schema_version")) is not int
        or receipt.get("schema_version") != 1
    ):
        raise MigrationError("unsupported migration receipt")
    for key, expected_path in (
        ("legacy_state", layout.legacy_state),
        ("canonical_state", layout.canonical_state),
        ("legacy_audit", layout.legacy_audit),
        ("canonical_audit", layout.canonical_audit),
    ):
        if receipt.get(key) != str(expected_path):
            raise MigrationError("migration receipt does not match the selected layout")
    return receipt


def rollback_migration(
    receipt_path: Path,
    layout: MigrationLayout,
    *,
    apply: bool = False,
    require_root: bool = True,
) -> Dict[str, object]:
    receipt = _read_receipt(
        receipt_path,
        layout,
        enforce_root_ownership=require_root,
    )
    _verify_canonical_runtime(
        layout.canonical_lock.parent,
        enforce_root_ownership=require_root,
    )
    if layout.legacy_lock != layout.canonical_lock:
        _verify_lock_namespace(
            layout.legacy_lock,
            enforce_root_ownership=require_root,
        )
    _verify_existing_operation_lock(
        layout.legacy_lock,
        enforce_root_ownership=require_root,
    )
    _verify_existing_operation_lock(
        layout.canonical_lock,
        enforce_root_ownership=require_root,
    )
    current_state_digest = _manifest_digest(
        _portable_manifest(layout.canonical_state, exclude_receipt=True)
    )
    if current_state_digest != receipt.get("state_manifest_sha256"):
        raise MigrationError("canonical state changed after migration; rollback refused")
    if not _audit_preserves_migrated_prefix(layout.canonical_audit, receipt):
        raise MigrationError("canonical audit was rewritten after migration; rollback refused")
    rollback_directory = Path(str(receipt.get("installer_rollback_directory")))
    installer_plan = installer.rollback_activation(
        rollback_directory,
        layout.installer,
        apply=False,
        require_root=require_root,
    )
    result = {
        "status": "planned_rollback",
        "migration_id": receipt.get("migration_id"),
        "installer": installer_plan,
        "actions": [
            "verify_canonical_state_unchanged",
            "restore_previous_tool_authority",
            "quarantine_canonical_copies_without_deleting_them",
            "leave_legacy_rollback_inputs_untouched",
        ],
    }
    if not apply:
        return result
    _require_root(require_root)
    with _migration_locks(
        layout,
        require_lock_support=require_root,
        enforce_root_ownership=require_root,
    ):
        receipt = _read_receipt(
            receipt_path,
            layout,
            enforce_root_ownership=require_root,
        )
        if _manifest_digest(
            _portable_manifest(layout.canonical_state, exclude_receipt=True)
        ) != receipt.get("state_manifest_sha256"):
            raise MigrationError("canonical state changed after migration; rollback refused")
        if not _audit_preserves_migrated_prefix(layout.canonical_audit, receipt):
            raise MigrationError("canonical audit was rewritten after migration; rollback refused")
        quarantine = layout.rollback_root / (
            str(receipt.get("migration_id")) + "-" +
            datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        )
        _ensure_durable_directory(layout.rollback_root, mode=0o700)
        _ensure_durable_directory(
            quarantine,
            mode=0o700,
            exist_ok=False,
        )
        installer.rollback_activation(
            rollback_directory,
            layout.installer,
            apply=True,
            require_root=require_root,
        )
        if receipt.get("created_canonical_state") is True:
            _durable_move(layout.canonical_state, quarantine / "state")
        else:
            receipt_path.unlink()
            _fsync_directory(receipt_path.parent)
        if receipt.get("created_canonical_audit") is True:
            _durable_move(layout.canonical_audit, quarantine / "deploy.log")
        return {
            **result,
            "status": "rolled_back",
            "quarantine_directory": str(quarantine),
        }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-state-root",
        type=Path,
        default=Path("/var/lib/commonex-releases"),
        help="reviewed state root from the inventory; use /etc/commonex only for the mixed legacy layout",
    )
    parser.add_argument(
        "--source-audit",
        type=Path,
        default=Path("/var/log/commonex-deploy.log"),
        help="reviewed deployment audit path from the inventory",
    )
    parser.add_argument(
        "--source-lock",
        type=Path,
        default=Path("/run/lock/commonex-deploy.lock"),
        help="reviewed operation-lock path from the inventory",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    migrate = subparsers.add_parser("migrate")
    migrate.add_argument("--inventory", required=True, type=Path)
    migrate.add_argument("--tool-bundle", required=True, type=Path)
    migrate.add_argument("--tool-git-sha", required=True)
    migrate.add_argument("--apply", action="store_true")
    rollback = subparsers.add_parser("rollback")
    rollback.add_argument("--receipt", required=True, type=Path)
    rollback.add_argument("--apply", action="store_true")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    arguments = _parser().parse_args(argv)
    layout = MigrationLayout.canonical(
        arguments.source_state_root, arguments.source_audit, arguments.source_lock
    )
    try:
        if arguments.command == "migrate":
            result = execute_migration(
                _load_report(arguments.inventory),
                arguments.tool_bundle,
                arguments.tool_git_sha,
                layout,
                apply=arguments.apply,
            )
        else:
            result = rollback_migration(
                arguments.receipt, layout, apply=arguments.apply
            )
    except (MigrationError, installer.InstallError, OSError) as error:
        sys.stderr.write("migrate-commonex-host: " + str(error) + "\n")
        return 1
    sys.stdout.write(json.dumps(result, indent=2, sort_keys=True) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
