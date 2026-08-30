#!/usr/bin/env python3
"""Install the CommonEx host tool as an immutable, versioned package.

The command is deliberately dry-run by default.  Mutation requires ``--apply``
and, for the canonical host layout, an effective root user.  The public helper
functions accept an alternate layout so the complete lifecycle can be tested
without privileged host access.
"""

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Callable, Dict, Iterable, List, Optional
import uuid


GIT_SHA = re.compile(r"^[0-9a-f]{40}$")
INSTALL_MANIFEST = ".tool-install.json"


class InstallError(RuntimeError):
    """A fail-closed installation or rollback error."""


@dataclass(frozen=True)
class InstallLayout:
    base: Path
    versions: Path
    current: Path
    entrypoint: Path
    rollbacks: Path

    @classmethod
    def canonical(cls) -> "InstallLayout":
        base = Path("/opt/commonex/deploy")
        return cls(
            base=base,
            versions=base / "versions",
            current=base / "current",
            entrypoint=Path("/usr/local/sbin/commonex-deploy"),
            rollbacks=base / "rollbacks",
        )

    @classmethod
    def under(cls, root: Path) -> "InstallLayout":
        base = root / "opt" / "commonex" / "deploy"
        return cls(
            base=base,
            versions=base / "versions",
            current=base / "current",
            entrypoint=root / "usr" / "local" / "sbin" / "commonex-deploy",
            rollbacks=base / "rollbacks",
        )


def _require_valid_sha(tool_git_sha: str) -> None:
    if GIT_SHA.fullmatch(tool_git_sha) is None:
        raise InstallError("tool version must be a lowercase 40-character repository Git SHA")


def _require_root(require_root: bool) -> None:
    if not require_root:
        return
    if not hasattr(os, "geteuid") or os.geteuid() != 0:
        raise InstallError("applying the canonical installation requires root")


def _fsync_directory(path: Path) -> None:
    if os.name != "posix":
        return
    descriptor = os.open(str(path), os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


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


def _atomic_write(path: Path, content: bytes, mode: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".commonex-", dir=str(path.parent))
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


def _read_trusted_file(
    path: Path,
    *,
    enforce_root_ownership: bool,
    exact_mode: Optional[int] = None,
) -> tuple[bytes, int]:
    try:
        path_metadata = path.lstat()
    except OSError as error:
        raise InstallError("tool rollback input is not a trusted regular file") from error
    if not stat.S_ISREG(path_metadata.st_mode):
        raise InstallError("tool rollback input is not a trusted regular file")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(str(path), flags)
    try:
        metadata = os.fstat(descriptor)
        mode = stat.S_IMODE(metadata.st_mode)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or (metadata.st_dev, metadata.st_ino)
            != (path_metadata.st_dev, path_metadata.st_ino)
            or metadata.st_size > 1024 * 1024
            or (
                os.name == "posix"
                and (
                    mode & 0o022
                    or (exact_mode is not None and mode != exact_mode)
                    or (
                        enforce_root_ownership
                        and (metadata.st_uid != 0 or metadata.st_gid != 0)
                    )
                )
            )
        ):
            raise InstallError("tool rollback input is not a trusted regular file")
        with os.fdopen(descriptor, "rb") as stream:
            descriptor = -1
            return stream.read(), mode
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _assert_trusted_install_path(
    path: Path,
    *,
    directory: Optional[bool],
    enforce_root_ownership: bool,
) -> os.stat_result:
    try:
        metadata = path.lstat()
    except OSError as error:
        raise InstallError("installed tool path is not root-owned and immutable") from error
    is_directory = stat.S_ISDIR(metadata.st_mode)
    is_file = stat.S_ISREG(metadata.st_mode)
    if (
        (directory is True and not is_directory)
        or (directory is False and not is_file)
        or (directory is None and not (is_directory or is_file))
    ):
        raise InstallError("installed tool path is not root-owned and immutable")
    if os.name == "posix":
        if stat.S_IMODE(metadata.st_mode) & 0o022:
            raise InstallError("installed tool path is not root-owned and immutable")
        if enforce_root_ownership and (
            metadata.st_uid != 0 or metadata.st_gid != 0
        ):
            raise InstallError("installed tool path is not root-owned and immutable")
    return metadata


def _validate_install_roots(
    layout: InstallLayout, *, enforce_root_ownership: bool
) -> None:
    directories = [layout.base, layout.versions]
    if enforce_root_ownership:
        directories = [*reversed(layout.base.parents), *directories]
    checked = set()
    for directory in directories:
        if directory in checked:
            continue
        checked.add(directory)
        _assert_trusted_install_path(
            directory,
            directory=True,
            enforce_root_ownership=enforce_root_ownership,
        )


def _validate_version_tree(
    version: Path,
    layout: InstallLayout,
    *,
    enforce_root_ownership: bool,
) -> None:
    if version.parent != layout.versions:
        raise InstallError("installed tool version is outside the versions directory")
    _validate_install_roots(
        layout, enforce_root_ownership=enforce_root_ownership
    )
    _assert_trusted_install_path(
        version,
        directory=True,
        enforce_root_ownership=enforce_root_ownership,
    )
    for path in sorted(version.rglob("*"), key=lambda item: item.as_posix()):
        _assert_trusted_install_path(
            path,
            directory=None,
            enforce_root_ownership=enforce_root_ownership,
        )


def _validate_rollback_root(
    layout: InstallLayout,
    *,
    enforce_root_ownership: bool,
) -> None:
    _validate_install_roots(
        layout,
        enforce_root_ownership=enforce_root_ownership,
    )
    metadata = _assert_trusted_install_path(
        layout.rollbacks,
        directory=True,
        enforce_root_ownership=enforce_root_ownership,
    )
    if os.name == "posix" and stat.S_IMODE(metadata.st_mode) != 0o700:
        raise InstallError("tool rollback directory is not trusted")


def _validate_rollback_directory(
    rollback_directory: Path,
    layout: InstallLayout,
    *,
    enforce_root_ownership: bool,
) -> None:
    if rollback_directory.parent != layout.rollbacks:
        raise InstallError("tool rollback directory is outside the trusted root")
    _validate_rollback_root(
        layout,
        enforce_root_ownership=enforce_root_ownership,
    )
    metadata = _assert_trusted_install_path(
        rollback_directory,
        directory=True,
        enforce_root_ownership=enforce_root_ownership,
    )
    if os.name == "posix" and stat.S_IMODE(metadata.st_mode) != 0o700:
        raise InstallError("tool rollback directory is not trusted")


def _assert_trusted_bundle_path(
    path: Path,
    label: str,
    *,
    directory: Optional[bool],
    enforce_root_ownership: bool,
) -> os.stat_result:
    try:
        metadata = path.lstat()
    except OSError as error:
        raise InstallError("tool bundle path is missing: " + label) from error
    is_directory = stat.S_ISDIR(metadata.st_mode)
    is_file = stat.S_ISREG(metadata.st_mode)
    if stat.S_ISLNK(metadata.st_mode):
        raise InstallError("tool bundle must not contain a symlink: " + label)
    if (
        (directory is True and not is_directory)
        or (directory is False and not is_file)
        or (directory is None and not (is_directory or is_file))
    ):
        raise InstallError("tool bundle contains a special file: " + label)
    if os.name == "posix":
        if stat.S_IMODE(metadata.st_mode) & 0o022:
            raise InstallError(
                "tool bundle contains a group/world-writable path: " + label
            )
        if enforce_root_ownership and (
            metadata.st_uid != 0 or metadata.st_gid != 0
        ):
            raise InstallError("tool bundle path is not root-owned: " + label)
    return metadata


def _validate_bundle_root(bundle: Path, *, enforce_root_ownership: bool) -> None:
    if enforce_root_ownership:
        for ancestor in reversed(bundle.parents):
            _assert_trusted_bundle_path(
                ancestor,
                str(ancestor),
                directory=True,
                enforce_root_ownership=True,
            )
    _assert_trusted_bundle_path(
        bundle,
        ".",
        directory=True,
        enforce_root_ownership=enforce_root_ownership,
    )


def _iter_bundle(
    bundle: Path,
    *,
    enforce_root_ownership: bool = False,
) -> Iterable[tuple[Path, os.stat_result]]:
    _validate_bundle_root(
        bundle,
        enforce_root_ownership=enforce_root_ownership,
    )
    required = bundle / "commonex_deploy.py"
    _assert_trusted_bundle_path(
        required,
        "commonex_deploy.py",
        directory=False,
        enforce_root_ownership=enforce_root_ownership,
    )
    for path in sorted(bundle.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(bundle)
        metadata = _assert_trusted_bundle_path(
            path,
            relative.as_posix(),
            directory=None,
            enforce_root_ownership=enforce_root_ownership,
        )
        if "__pycache__" in relative.parts or path.suffix in {".pyc", ".pyo"}:
            continue
        yield path, metadata


def _bundle_manifest(
    bundle: Path,
    *,
    enforce_root_ownership: bool = False,
) -> List[Dict[str, object]]:
    manifest: List[Dict[str, object]] = []
    for path, metadata in _iter_bundle(
        bundle,
        enforce_root_ownership=enforce_root_ownership,
    ):
        relative = path.relative_to(bundle).as_posix()
        if stat.S_ISDIR(metadata.st_mode):
            manifest.append({"path": relative, "kind": "directory"})
            continue
        content = path.read_bytes()
        if path.suffix == ".py":
            try:
                compile(content, relative, "exec")
            except (SyntaxError, ValueError) as error:
                raise InstallError("invalid Python module in tool bundle: " + relative) from error
        manifest.append(
            {
                "path": relative,
                "kind": "file",
                "mode": f"{stat.S_IMODE(metadata.st_mode):04o}",
                "size": len(content),
                "sha256": hashlib.sha256(content).hexdigest(),
            }
        )
    return manifest


def _manifest_digest(manifest: List[Dict[str, object]]) -> str:
    encoded = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _copy_bundle(bundle: Path, destination: Path, manifest: List[Dict[str, object]]) -> None:
    destination.mkdir(mode=0o755)
    directories = {destination}
    for record in manifest:
        relative = Path(str(record["path"]))
        source = bundle / relative
        target = destination / relative
        if record["kind"] == "directory":
            target.mkdir(mode=0o755, parents=True, exist_ok=True)
            directories.add(target)
        else:
            target.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
            directories.add(target.parent)
            shutil.copyfile(str(source), str(target), follow_symlinks=False)
            os.chmod(target, int(str(record["mode"]), 8))
            if os.name == "posix":
                descriptor = os.open(
                    str(target),
                    os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
                )
                try:
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
    for directory in sorted(directories, key=lambda path: len(path.parts), reverse=True):
        _fsync_directory(directory)


def _validate_importable_tool(version: Path) -> None:
    completed = subprocess.run(
        [
            sys.executable,
            "-I",
            "-c",
            "import sys; sys.path.insert(0, sys.argv[1]); import commonex_deploy",
            str(version),
        ],
        check=False,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env={"PATH": os.environ.get("PATH", "")},
    )
    if completed.returncode != 0:
        raise InstallError("staged deployment tool failed its import smoke test")


def _installed_manifest(version: Path) -> List[Dict[str, object]]:
    return [
        record
        for record in _bundle_manifest(version)
        if record.get("path") != INSTALL_MANIFEST
    ]


def _plan_from_manifest(
    bundle: Path,
    tool_git_sha: str,
    layout: InstallLayout,
    manifest: List[Dict[str, object]],
) -> Dict[str, object]:
    version = layout.versions / tool_git_sha
    return {
        "status": "planned",
        "tool_git_sha": tool_git_sha,
        "bundle": str(bundle),
        "version": str(version),
        "current": str(layout.current),
        "entrypoint": str(layout.entrypoint),
        "source_manifest_sha256": _manifest_digest(manifest),
        "actions": [
            "stage_and_verify_immutable_version",
            "retain_previous_entrypoint_and_current_target",
            "atomically_switch_current",
            "atomically_replace_stable_entrypoint",
        ],
    }


def plan_install(
    bundle: Path,
    tool_git_sha: str,
    layout: InstallLayout,
    *,
    enforce_root_ownership: bool = False,
) -> Dict[str, object]:
    _require_valid_sha(tool_git_sha)
    return _plan_from_manifest(
        bundle,
        tool_git_sha,
        layout,
        _bundle_manifest(
            bundle,
            enforce_root_ownership=enforce_root_ownership,
        ),
    )


def stage_version(
    bundle: Path,
    tool_git_sha: str,
    layout: InstallLayout,
    *,
    require_root: bool = True,
) -> Dict[str, object]:
    _require_root(require_root)
    _require_valid_sha(tool_git_sha)
    manifest = _bundle_manifest(
        bundle,
        enforce_root_ownership=require_root,
    )
    plan = _plan_from_manifest(bundle, tool_git_sha, layout, manifest)
    expected_digest = _manifest_digest(manifest)
    _ensure_durable_directory(layout.versions, mode=0o755)
    _validate_install_roots(
        layout, enforce_root_ownership=require_root
    )
    version = layout.versions / tool_git_sha
    if version.exists():
        if version.is_symlink() or not version.is_dir():
            raise InstallError("existing tool version is not a trusted directory")
        _validate_version_tree(
            version,
            layout,
            enforce_root_ownership=require_root,
        )
        metadata_path = version / INSTALL_MANIFEST
        try:
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        except (OSError, ValueError, TypeError) as error:
            raise InstallError("existing tool version has no valid installation manifest") from error
        if metadata.get("source_manifest_sha256") != expected_digest:
            raise InstallError("existing immutable tool version differs from the bundle")
        if metadata.get("files") != manifest or _installed_manifest(version) != manifest:
            raise InstallError("existing tool version failed content verification")
        _validate_importable_tool(version)
        return {**plan, "status": "already_staged"}

    staging = layout.versions / (".staging-" + tool_git_sha + "-" + uuid.uuid4().hex)
    try:
        _copy_bundle(bundle, staging, manifest)
        if _manifest_digest(_installed_manifest(staging)) != expected_digest:
            raise InstallError("staged tool version failed content verification")
        _validate_importable_tool(staging)
        install_metadata = {
            "schema_version": 1,
            "tool_git_sha": tool_git_sha,
            "source_manifest_sha256": expected_digest,
            "files": manifest,
        }
        _atomic_write(
            staging / INSTALL_MANIFEST,
            (json.dumps(install_metadata, indent=2, sort_keys=True) + "\n").encode("utf-8"),
            0o644,
        )
        _validate_version_tree(
            staging,
            layout,
            enforce_root_ownership=require_root,
        )
        os.replace(str(staging), str(version))
        _fsync_directory(layout.versions)
        _validate_version_tree(
            version,
            layout,
            enforce_root_ownership=require_root,
        )
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return {**plan, "status": "staged"}


def _launcher(layout: InstallLayout) -> bytes:
    module = layout.current / "commonex_deploy.py"
    command = shlex.quote(str(module))
    return ("#!/bin/sh\nexec /usr/bin/python3 " + command + ' "$@"\n').encode("utf-8")


def _new_rollback_directory(layout: InstallLayout) -> Path:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    return layout.rollbacks / (timestamp + "-" + uuid.uuid4().hex)


def _replace_current(
    layout: InstallLayout,
    target: str,
    *,
    on_replaced: Optional[Callable[[], None]] = None,
) -> None:
    layout.current.parent.mkdir(parents=True, exist_ok=True)
    temporary = layout.current.parent / (".current-" + uuid.uuid4().hex)
    try:
        os.symlink(target, temporary, target_is_directory=True)
        if os.name == "nt" and layout.current.is_symlink():
            # Windows cannot replace a directory symlink in one operation.  The
            # production path is POSIX, where os.replace remains atomic.
            layout.current.unlink()
        os.replace(str(temporary), str(layout.current))
        if on_replaced is not None:
            on_replaced()
        _fsync_directory(layout.current.parent)
    finally:
        if temporary.is_symlink():
            temporary.unlink()


def activate_version(
    tool_git_sha: str,
    layout: InstallLayout,
    *,
    require_root: bool = True,
) -> Dict[str, object]:
    _require_root(require_root)
    _require_valid_sha(tool_git_sha)
    version = layout.versions / tool_git_sha
    if not version.is_dir() or version.is_symlink():
        raise InstallError("tool version must be staged and verified before activation")
    _validate_version_tree(
        version,
        layout,
        enforce_root_ownership=require_root,
    )
    try:
        metadata = json.loads((version / INSTALL_MANIFEST).read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError) as error:
        raise InstallError("staged tool version has no valid installation manifest") from error
    if metadata.get("tool_git_sha") != tool_git_sha:
        raise InstallError("staged tool version identity is invalid")
    expected_files = metadata.get("files")
    if not isinstance(expected_files, list) or _installed_manifest(version) != expected_files:
        raise InstallError("staged tool version failed content verification")
    _validate_importable_tool(version)

    rollback = _new_rollback_directory(layout)
    previous_target: Optional[str] = None
    if layout.current.is_symlink():
        previous_target = os.readlink(layout.current)
        if (
            re.fullmatch(r"versions/[0-9a-f]{40}", previous_target) is None
            or not (layout.base / previous_target).is_dir()
        ):
            raise InstallError("current tool selector has an invalid target")
        _validate_version_tree(
            layout.base / previous_target,
            layout,
            enforce_root_ownership=require_root,
        )
    elif layout.current.exists():
        raise InstallError("current tool selector is not a symbolic link")
    entrypoint_existed = layout.entrypoint.exists()
    entrypoint_content = b""
    entrypoint_mode = 0
    if entrypoint_existed:
        if layout.entrypoint.is_symlink() or not layout.entrypoint.is_file():
            raise InstallError("stable entrypoint is not a trusted regular file")
        entrypoint_content, entrypoint_mode = _read_trusted_file(
            layout.entrypoint,
            enforce_root_ownership=require_root,
        )

    _ensure_durable_directory(layout.rollbacks, mode=0o700)
    _validate_rollback_root(
        layout,
        enforce_root_ownership=require_root,
    )
    _ensure_durable_directory(rollback, mode=0o700, exist_ok=False)
    _validate_rollback_directory(
        rollback,
        layout,
        enforce_root_ownership=require_root,
    )
    if entrypoint_existed:
        _atomic_write(
            rollback / "entrypoint",
            entrypoint_content,
            entrypoint_mode,
        )
    activation = {
        "schema_version": 1,
        "new_tool_git_sha": tool_git_sha,
        "previous_current_target": previous_target,
        "entrypoint_existed": entrypoint_existed,
    }
    _atomic_write(
        rollback / "activation.json",
        (json.dumps(activation, indent=2, sort_keys=True) + "\n").encode("utf-8"),
        0o600,
    )

    new_target = "versions/" + tool_git_sha
    switched_current = False

    def mark_current_switched() -> None:
        nonlocal switched_current
        switched_current = True

    try:
        _replace_current(
            layout,
            new_target,
            on_replaced=mark_current_switched,
        )
        _atomic_write(layout.entrypoint, _launcher(layout), 0o755)
    except BaseException as activation_error:
        try:
            if entrypoint_existed:
                _atomic_write(layout.entrypoint, entrypoint_content, entrypoint_mode)
            elif layout.entrypoint.exists():
                layout.entrypoint.unlink()
                _fsync_directory(layout.entrypoint.parent)
            if switched_current:
                if previous_target is None:
                    if layout.current.is_symlink():
                        layout.current.unlink()
                        _fsync_directory(layout.current.parent)
                else:
                    _replace_current(layout, previous_target)
        except BaseException as restore_error:
            raise InstallError(
                "tool activation failed and prior authority restoration failed"
            ) from restore_error
        raise activation_error
    return {
        "status": "activated",
        "tool_git_sha": tool_git_sha,
        "rollback_directory": str(rollback),
    }


def install_version(
    bundle: Path,
    tool_git_sha: str,
    layout: InstallLayout,
    *,
    apply: bool = False,
    require_root: bool = True,
) -> Dict[str, object]:
    if not apply:
        return plan_install(
            bundle,
            tool_git_sha,
            layout,
            enforce_root_ownership=require_root,
        )
    stage = stage_version(bundle, tool_git_sha, layout, require_root=require_root)
    activated = activate_version(tool_git_sha, layout, require_root=require_root)
    return {**stage, **activated, "status": "installed"}


def rollback_activation(
    rollback_directory: Path,
    layout: InstallLayout,
    *,
    apply: bool = False,
    require_root: bool = True,
) -> Dict[str, object]:
    _validate_rollback_directory(
        rollback_directory,
        layout,
        enforce_root_ownership=require_root,
    )
    try:
        activation_content, _ = _read_trusted_file(
            rollback_directory / "activation.json",
            enforce_root_ownership=require_root,
            exact_mode=0o600,
        )
        activation = json.loads(activation_content.decode("utf-8"))
    except (OSError, UnicodeError, ValueError, TypeError) as error:
        raise InstallError("rollback directory has no valid activation record") from error
    if (
        not isinstance(activation, dict)
        or type(activation.get("schema_version")) is not int
        or activation.get("schema_version") != 1
    ):
        raise InstallError("unsupported activation rollback record")
    new_tool_git_sha = activation.get("new_tool_git_sha")
    if not isinstance(new_tool_git_sha, str) or GIT_SHA.fullmatch(new_tool_git_sha) is None:
        raise InstallError("invalid new tool target in rollback record")
    previous_target = activation.get("previous_current_target")
    if previous_target is not None and (
        not isinstance(previous_target, str)
        or re.fullmatch(r"versions/[0-9a-f]{40}", previous_target) is None
    ):
        raise InstallError("invalid previous tool target in rollback record")
    entrypoint_existed = activation.get("entrypoint_existed")
    if not isinstance(entrypoint_existed, bool):
        raise InstallError("invalid entrypoint state in rollback record")
    backup_content: Optional[bytes] = None
    backup_mode = 0
    if entrypoint_existed:
        try:
            backup_content, backup_mode = _read_trusted_file(
                rollback_directory / "entrypoint",
                enforce_root_ownership=require_root,
            )
        except (OSError, InstallError) as error:
            raise InstallError("previous entrypoint backup is missing or untrusted") from error
    result = {
        "status": "planned_rollback",
        "rollback_directory": str(rollback_directory),
        "previous_current_target": activation.get("previous_current_target"),
    }
    if not apply:
        return result
    _require_root(require_root)
    expected_new = "versions/" + new_tool_git_sha
    if not layout.current.is_symlink() or os.readlink(layout.current) != expected_new:
        raise InstallError("current tool changed after installation; rollback refused")
    _validate_version_tree(
        layout.base / expected_new,
        layout,
        enforce_root_ownership=require_root,
    )
    if previous_target is not None and (
        not (layout.base / previous_target).is_dir()
    ):
        raise InstallError("invalid previous tool target in rollback record")
    if isinstance(previous_target, str):
        _validate_version_tree(
            layout.base / previous_target,
            layout,
            enforce_root_ownership=require_root,
        )
    try:
        current_entrypoint_content, current_entrypoint_mode = _read_trusted_file(
            layout.entrypoint,
            enforce_root_ownership=require_root,
        )
    except OSError as error:
        raise InstallError("current stable entrypoint is not trusted") from error

    selector_changed = False

    def mark_selector_changed() -> None:
        nonlocal selector_changed
        selector_changed = True

    try:
        if previous_target is None:
            layout.current.unlink()
            selector_changed = True
            _fsync_directory(layout.current.parent)
        else:
            _replace_current(
                layout,
                previous_target,
                on_replaced=mark_selector_changed,
            )
        if entrypoint_existed:
            assert backup_content is not None
            _atomic_write(layout.entrypoint, backup_content, backup_mode)
        elif layout.entrypoint.exists():
            layout.entrypoint.unlink()
            _fsync_directory(layout.entrypoint.parent)
    except BaseException as rollback_error:
        if selector_changed:
            try:
                _replace_current(layout, expected_new)
                _atomic_write(
                    layout.entrypoint,
                    current_entrypoint_content,
                    current_entrypoint_mode,
                )
            except BaseException as restore_error:
                raise InstallError(
                    "tool rollback failed and prior authority restoration failed"
                ) from restore_error
        raise rollback_error
    return {**result, "status": "rolled_back"}


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    install = subparsers.add_parser("install")
    install.add_argument("--bundle", required=True, type=Path)
    install.add_argument("--tool-git-sha", required=True)
    install.add_argument("--apply", action="store_true")
    rollback = subparsers.add_parser("rollback")
    rollback.add_argument("--rollback-directory", required=True, type=Path)
    rollback.add_argument("--apply", action="store_true")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    arguments = _parser().parse_args(argv)
    layout = InstallLayout.canonical()
    try:
        if arguments.command == "install":
            result = install_version(
                arguments.bundle,
                arguments.tool_git_sha,
                layout,
                apply=arguments.apply,
            )
        else:
            result = rollback_activation(
                arguments.rollback_directory,
                layout,
                apply=arguments.apply,
            )
    except (InstallError, OSError) as error:
        sys.stderr.write("install-commonex-deploy: " + str(error) + "\n")
        return 1
    sys.stdout.write(json.dumps(result, indent=2, sort_keys=True) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
