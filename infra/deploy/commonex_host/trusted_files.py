"""Trusted durable storage for CommonEx activation documents.

The public interface names semantic documents. Callers cannot select paths, modes,
size limits, serialization, or durability behavior.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import stat
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from .activation import AmbiguousActivationCommitError


JsonObject = dict[str, object]
DirectorySync = Callable[[Path], None]
RELEASE_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RUN_NUMBER_PATTERN = re.compile(r"^[1-9][0-9]{0,19}$")
ROLLBACK_BACKUP_PATTERN = re.compile(
    r"^deploy-[0-9a-f]{40}-[0-9]{8}T[0-9]{12}Z$"
)


@dataclass(frozen=True)
class _TrustedFileLocations:
    """The deployment-owned roots from which closed document paths are derived."""

    release_root: Path
    enforce_root_ownership: bool
    app_dir: Optional[Path] = None
    log_path: Optional[Path] = None
    rollback_root: Optional[Path] = None
    max_document_bytes: int = 10 * 1024 * 1024


class TrustedDurableFiles:
    """Read and durably mutate the closed activation-document set."""

    _INTENT_LIMIT = 4096
    _STATE_LIMIT = 8192
    _MODE = 0o600

    def __init__(
        self,
        locations: _TrustedFileLocations,
        *,
        sync_directory: DirectorySync,
        clock: Optional[Callable[[], str]] = None,
    ) -> None:
        self._locations = locations
        self._sync_directory = sync_directory
        self._clock = clock

    @property
    def _root(self) -> Path:
        return self._locations.release_root

    @property
    def _intent_path(self) -> Path:
        return self._root / "activation-intent.json"

    @property
    def _state_path(self) -> Path:
        return self._root / "activation-state.json"

    @property
    def _legacy_state_path(self) -> Path:
        return self._root / "last-successful-run"

    @staticmethod
    def _validate_run_number(value: object) -> int:
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError("activation state has an invalid run number")
        if RUN_NUMBER_PATTERN.fullmatch(str(value)) is None:
            raise ValueError("activation state has an invalid run number")
        return value

    @classmethod
    def _validate_state(
        cls,
        run_number: object,
        history: object,
    ) -> tuple[int, list[str]]:
        run = cls._validate_run_number(run_number)
        if not isinstance(history, list) or len(history) > 3:
            raise ValueError("activation state has an invalid history")
        if any(
            not isinstance(release, str)
            or RELEASE_PATTERN.fullmatch(release) is None
            for release in history
        ):
            raise ValueError("activation state has an invalid history")
        if len(set(history)) != len(history):
            raise ValueError("activation state history contains duplicates")
        return run, list(history)

    @classmethod
    def _validate_intent(cls, state: object) -> JsonObject:
        expected_keys = {
            "candidate_release",
            "operation",
            "previous_release",
            "rollback_backup",
            "run_number",
        }
        if not isinstance(state, dict) or set(state) != expected_keys:
            raise ValueError("activation intent is invalid")
        candidate = state["candidate_release"]
        previous = state["previous_release"]
        if (
            not isinstance(candidate, str)
            or RELEASE_PATTERN.fullmatch(candidate) is None
            or state["operation"] not in {"deploy", "rollback"}
            or (
                previous is not None
                and (
                    not isinstance(previous, str)
                    or RELEASE_PATTERN.fullmatch(previous) is None
                )
            )
            or not isinstance(state["rollback_backup"], str)
            or ROLLBACK_BACKUP_PATTERN.fullmatch(state["rollback_backup"]) is None
        ):
            raise ValueError("activation intent is invalid")
        cls._validate_run_number(state["run_number"])
        return state

    @staticmethod
    def _open_flags(*flags: int) -> int:
        result = 0
        for flag in flags:
            result |= flag
        return result

    @staticmethod
    def _unique_json_object(pairs: list[tuple[str, object]]) -> JsonObject:
        result: JsonObject = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("trusted document contains a duplicate JSON key")
            result[key] = value
        return result

    def _ensure_root(self) -> None:
        self._root.mkdir(mode=0o700, parents=True, exist_ok=True)
        metadata = self._root.lstat()
        if not stat.S_ISDIR(metadata.st_mode):
            raise PermissionError(f"path is not a directory: {self._root}")
        self._verify_owner(metadata, self._root)
        mode = stat.S_IMODE(metadata.st_mode)
        if os.name == "posix" and mode != 0o700:
            raise PermissionError(f"unsafe mode {mode:o} for directory: {self._root}")

    def _verify_owner(self, metadata: os.stat_result, path: Path) -> None:
        if self._locations.enforce_root_ownership and (
            metadata.st_uid != 0 or metadata.st_gid != 0
        ):
            raise PermissionError(f"path is not owned by root: {path}")

    def _ensure_directory(self, path: Path, *, create_mode: int) -> None:
        path.mkdir(mode=create_mode, parents=True, exist_ok=True)
        metadata = path.lstat()
        mode = stat.S_IMODE(metadata.st_mode)
        if not stat.S_ISDIR(metadata.st_mode):
            raise PermissionError(f"path is not a directory: {path}")
        self._verify_owner(metadata, path)
        if os.name == "posix" and mode & 0o022:
            raise PermissionError(f"directory is group/world writable: {path}")

    def _read_regular_file(
        self,
        path: Path,
        *,
        mode: int,
        limit: int,
        label: str,
    ) -> bytes:
        if path.is_symlink():
            raise PermissionError(f"{label} is a symlink: {path}")
        flags = self._open_flags(
            os.O_RDONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        descriptor = os.open(path, flags)
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(f"{label} is not a regular file: {path}")
            self._verify_owner(metadata, path)
            actual_mode = stat.S_IMODE(metadata.st_mode)
            if os.name == "posix" and actual_mode != mode:
                raise PermissionError(f"unsafe mode {actual_mode:o} for {label}: {path}")
            if metadata.st_size > limit:
                raise ValueError(f"{label} is too large")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = -1
                content = stream.read(limit + 1)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
        if len(content) > limit:
            raise ValueError(f"{label} is too large")
        return content

    def _read_json(
        self,
        path: Path,
        *,
        limit: int,
        label: str,
    ) -> Optional[object]:
        if path.is_symlink():
            raise PermissionError(f"{label} is a symlink: {path}")
        flags = self._open_flags(
            os.O_RDONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        try:
            descriptor = os.open(path, flags)
        except FileNotFoundError:
            return None

        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(f"{label} is not a regular file: {path}")
            self._verify_owner(metadata, path)
            mode = stat.S_IMODE(metadata.st_mode)
            if os.name == "posix" and mode != self._MODE:
                raise PermissionError(f"unsafe mode {mode:o} for {label}: {path}")
            if metadata.st_size > limit:
                raise ValueError(f"{label} is too large")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = -1
                serialized = stream.read(limit + 1)
        finally:
            if descriptor >= 0:
                os.close(descriptor)

        if len(serialized) > limit:
            raise ValueError(f"{label} is too large")
        try:
            text = serialized.decode("utf-8")
            return json.loads(text, object_pairs_hook=self._unique_json_object)
        except (json.JSONDecodeError, UnicodeError) as error:
            raise ValueError(f"{label} is invalid") from error

    def _read_existing_bytes(
        self,
        path: Path,
        *,
        label: str,
        limit: int,
    ) -> Optional[bytes]:
        if path.is_symlink():
            raise PermissionError(f"{label} is a symlink: {path}")
        flags = self._open_flags(
            os.O_RDONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        try:
            descriptor = os.open(path, flags)
        except FileNotFoundError:
            return None
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(f"{label} is not a regular file: {path}")
            self._verify_owner(metadata, path)
            mode = stat.S_IMODE(metadata.st_mode)
            if os.name == "posix" and mode != self._MODE:
                raise PermissionError(f"unsafe mode {mode:o} for {label}: {path}")
            if metadata.st_size > limit:
                raise ValueError(f"{label} is too large")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = -1
                content = stream.read(limit + 1)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
        if len(content) > limit:
            raise ValueError(f"{label} is too large")
        return content

    def _read_legacy_run(self) -> int:
        path = self._legacy_state_path
        if path.is_symlink():
            raise PermissionError(f"deployment state is a symlink: {path}")
        flags = self._open_flags(
            os.O_RDONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        try:
            descriptor = os.open(path, flags)
        except FileNotFoundError:
            return 0
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(f"deployment state is not a regular file: {path}")
            self._verify_owner(metadata, path)
            mode = stat.S_IMODE(metadata.st_mode)
            if os.name == "posix" and mode != self._MODE:
                raise PermissionError(f"unsafe mode {mode:o} for deployment state: {path}")
            with os.fdopen(descriptor, "rb") as stream:
                descriptor = -1
                serialized = stream.read(22)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
        if not serialized.endswith(b"\n") or serialized.count(b"\n") != 1:
            raise ValueError("deployment state is invalid")
        try:
            value = serialized[:-1].decode("ascii")
        except UnicodeError as error:
            raise ValueError("deployment state is invalid") from error
        if RUN_NUMBER_PATTERN.fullmatch(value) is None:
            raise ValueError("deployment state is invalid")
        return int(value)

    def _replace_json(
        self,
        path: Path,
        value: JsonObject,
        *,
        temporary_prefix: str,
        after_replace: Optional[Callable[[], None]] = None,
    ) -> None:
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=temporary_prefix,
            dir=self._root,
        )
        temporary = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
                descriptor = -1
                json.dump(value, stream, separators=(",", ":"), sort_keys=True)
                stream.write("\n")
                stream.flush()
                os.fsync(stream.fileno())
            if self._locations.enforce_root_ownership:
                os.chown(temporary, 0, 0)
            temporary.chmod(self._MODE)
            temporary.replace(path)
            if after_replace is not None:
                after_replace()
            self._sync_directory(self._root)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
            temporary.unlink(missing_ok=True)

    def read_activation_intent(self) -> Optional[JsonObject]:
        self._ensure_root()
        value = self._read_json(
            self._intent_path,
            limit=self._INTENT_LIMIT,
            label="activation intent",
        )
        if value is None:
            return None
        return self._validate_intent(value)

    def persist_activation_intent(self, intent: JsonObject) -> None:
        self._ensure_root()
        validated = self._validate_intent(intent)
        self._replace_json(
            self._intent_path,
            validated,
            temporary_prefix=".activation-intent.",
        )

    def clear_activation_intent(self, expected: JsonObject) -> None:
        actual = self.read_activation_intent()
        if actual != expected:
            raise RuntimeError("activation intent changed during activation")
        try:
            self._intent_path.unlink()
            self._sync_directory(self._root)
        except Exception as clear_error:
            try:
                if not self._intent_path.exists() and not self._intent_path.is_symlink():
                    self.persist_activation_intent(expected)
            except Exception as restore_error:
                raise AmbiguousActivationCommitError(
                    "activation intent removal and restoration could not be durably "
                    "confirmed"
                ) from restore_error
            raise clear_error

    def read_activation_state(self) -> tuple[int, list[str]]:
        self._ensure_root()
        value = self._read_json(
            self._state_path,
            limit=self._STATE_LIMIT,
            label="deployment state",
        )
        if value is None:
            return self._read_legacy_run(), []
        if not isinstance(value, dict) or set(value) != {
            "last_successful_run",
            "history",
        }:
            raise ValueError("activation state is invalid")
        return self._validate_state(value["last_successful_run"], value["history"])

    def write_activation_state(self, run_number: int, history: list[str]) -> None:
        run_number, history = self._validate_state(run_number, history)
        self._ensure_root()
        previous_state = self._read_existing_bytes(
            self._state_path,
            label="deployment state",
            limit=self._STATE_LIMIT,
        )
        replacement_complete = False

        def mark_replaced() -> None:
            nonlocal replacement_complete
            replacement_complete = True

        try:
            self._replace_json(
                self._state_path,
                {"last_successful_run": run_number, "history": history},
                temporary_prefix=".activation-state.",
                after_replace=mark_replaced,
            )
        except Exception as commit_error:
            if replacement_complete:
                try:
                    self.restore_activation_state(previous_state)
                except Exception as restore_error:
                    raise AmbiguousActivationCommitError(
                        "activation state commit is ambiguous because prior state "
                        "restoration could not be durably confirmed"
                    ) from restore_error
            raise commit_error

    def restore_activation_state(self, previous_state: Optional[bytes]) -> None:
        self._ensure_root()
        if previous_state is None:
            self._state_path.unlink()
            self._sync_directory(self._root)
            return

        descriptor, temporary_name = tempfile.mkstemp(
            prefix=".activation-state-restore.",
            dir=self._root,
        )
        temporary = Path(temporary_name)
        try:
            with os.fdopen(descriptor, "wb") as stream:
                descriptor = -1
                stream.write(previous_state)
                stream.flush()
                os.fsync(stream.fileno())
            if self._locations.enforce_root_ownership:
                os.chown(temporary, 0, 0)
            temporary.chmod(self._MODE)
            temporary.replace(self._state_path)
            self._sync_directory(self._root)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
            temporary.unlink(missing_ok=True)

    def write_release_manifest(self, directory: Path) -> None:
        """Write the fixed manifest for one trusted staging directory."""

        self._ensure_root()
        if directory.parent != self._root or directory.is_symlink():
            raise ValueError("release staging directory is outside the release root")
        self._ensure_directory(directory, create_mode=0o700)
        entries = []
        for name, mode in ((".env", 0o600), ("docker-compose-prod.yml", 0o644)):
            content = self._read_regular_file(
                directory / name,
                mode=mode,
                limit=self._locations.max_document_bytes,
                label="release file",
            )
            entries.append(f"{hashlib.sha256(content).hexdigest()}  {name}\n")
        manifest = directory / "manifest.sha256"
        flags = self._open_flags(
            os.O_CREAT,
            os.O_EXCL,
            os.O_WRONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        descriptor = os.open(manifest, flags, self._MODE)
        try:
            if self._locations.enforce_root_ownership:
                os.fchown(descriptor, 0, 0)
            if hasattr(os, "fchmod"):
                os.fchmod(descriptor, self._MODE)
            with os.fdopen(descriptor, "w", encoding="ascii", newline="\n") as stream:
                descriptor = -1
                stream.writelines(entries)
                stream.flush()
                os.fsync(stream.fileno())
        finally:
            if descriptor >= 0:
                os.close(descriptor)
        self._sync_directory(directory)

    def validate_release_documents(self, release: str) -> Path:
        """Validate the exact staged document set and its hashes."""

        if RELEASE_PATTERN.fullmatch(release) is None:
            raise ValueError("release id must be a lowercase 40-character Git SHA")
        self._ensure_root()
        directory = self._root / release
        if directory.is_symlink():
            raise ValueError(f"release is not staged safely: {release}")
        metadata = directory.lstat()
        if not stat.S_ISDIR(metadata.st_mode):
            raise PermissionError(f"path is not a directory: {directory}")
        self._verify_owner(metadata, directory)
        mode = stat.S_IMODE(metadata.st_mode)
        if os.name == "posix" and mode != 0o700:
            raise PermissionError(f"unsafe mode {mode:o} for directory: {directory}")
        entries = list(directory.iterdir())
        if any(entry.is_symlink() for entry in entries):
            raise ValueError("release contains a symlink")
        if {entry.name for entry in entries} != {
            ".env",
            "docker-compose-prod.yml",
            "manifest.sha256",
        }:
            raise ValueError("release contains missing or unexpected entries")
        environment = self._read_regular_file(
            directory / ".env",
            mode=0o600,
            limit=self._locations.max_document_bytes,
            label="release file",
        )
        compose = self._read_regular_file(
            directory / "docker-compose-prod.yml",
            mode=0o644,
            limit=self._locations.max_document_bytes,
            label="release file",
        )
        manifest_bytes = self._read_regular_file(
            directory / "manifest.sha256",
            mode=self._MODE,
            limit=4096,
            label="release manifest",
        )
        try:
            manifest_text = manifest_bytes.decode("ascii")
        except UnicodeError as error:
            raise ValueError("release manifest contains an invalid entry") from error
        expected = {
            ".env": hashlib.sha256(environment).hexdigest(),
            "docker-compose-prod.yml": hashlib.sha256(compose).hexdigest(),
        }
        actual: dict[str, str] = {}
        for line in manifest_text.splitlines():
            digest, separator, name = line.partition("  ")
            if (
                separator != "  "
                or re.fullmatch(r"[0-9a-f]{64}", digest) is None
                or name not in expected
                or name in actual
            ):
                raise ValueError("release manifest contains an invalid entry")
            actual[name] = digest
        if actual != expected:
            raise ValueError("release manifest does not match the expected file set")
        return directory

    def _validate_rollback_directory(self, rollback: Path) -> None:
        if (
            self._locations.rollback_root is None
            or rollback.parent != self._locations.rollback_root
            or ROLLBACK_BACKUP_PATTERN.fullmatch(rollback.name) is None
            or rollback.is_symlink()
        ):
            raise ValueError("configuration rollback directory is invalid")
        metadata = rollback.lstat()
        if not stat.S_ISDIR(metadata.st_mode):
            raise PermissionError(f"path is not a directory: {rollback}")
        self._verify_owner(metadata, rollback)
        mode = stat.S_IMODE(metadata.st_mode)
        if os.name == "posix" and mode != 0o700:
            raise PermissionError(f"unsafe mode {mode:o} for directory: {rollback}")

    def backup_active_configuration(self, rollback: Path) -> None:
        """Durably copy the fixed active configuration into one rollback slot."""

        if self._locations.app_dir is None:
            raise RuntimeError("active configuration location is unavailable")
        self._validate_rollback_directory(rollback)
        for name, active_mode in ((".env", 0o600), ("docker-compose-prod.yml", 0o644)):
            content = self._read_regular_file(
                self._locations.app_dir / name,
                mode=active_mode,
                limit=self._locations.max_document_bytes,
                label="current configuration",
            )
            destination = rollback / name
            flags = self._open_flags(
                os.O_CREAT,
                os.O_EXCL,
                os.O_WRONLY,
                getattr(os, "O_CLOEXEC", 0),
                getattr(os, "O_NOFOLLOW", 0),
            )
            descriptor = os.open(destination, flags, self._MODE)
            try:
                with os.fdopen(descriptor, "wb") as output:
                    descriptor = -1
                    output.write(content)
                    output.flush()
                    os.fsync(output.fileno())
                if self._locations.enforce_root_ownership:
                    os.chown(destination, 0, 0)
                destination.chmod(self._MODE)
            finally:
                if descriptor >= 0:
                    os.close(descriptor)
        self._sync_directory(rollback)

    def restore_configuration(self, rollback: Path) -> None:
        """Restore the fixed active configuration from a trusted rollback slot."""

        self._validate_rollback_directory(rollback)
        for name in ("docker-compose-prod.yml", ".env"):
            self._install_active_configuration_file(rollback / name, name)

    def install_release_configuration(self, release: str) -> None:
        """Install both active documents from one validated Release."""

        directory = self.validate_release_documents(release)
        for name in ("docker-compose-prod.yml", ".env"):
            self._install_active_configuration_file(directory / name, name)

    def _install_active_configuration_file(self, source: Path, name: str) -> None:
        """Install one allowlisted configuration file at its fixed destination."""

        modes = {"docker-compose-prod.yml": 0o644, ".env": 0o600}
        if name not in modes or source.name != name:
            raise ValueError("active configuration file is not allowlisted")
        if self._locations.app_dir is None:
            raise RuntimeError("active configuration location is unavailable")
        app_dir = self._locations.app_dir
        self._ensure_directory(app_dir, create_mode=0o755)
        flags = self._open_flags(
            os.O_RDONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        descriptor = os.open(source, flags)
        destination = app_dir / name
        temporary_descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{name}.",
            dir=app_dir,
        )
        temporary = Path(temporary_name)
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(
                    f"configuration source is not a regular file: {source}"
                )
            self._verify_owner(metadata, source)
            with os.fdopen(descriptor, "rb") as input_file:
                descriptor = -1
                with os.fdopen(temporary_descriptor, "wb") as output:
                    temporary_descriptor = -1
                    shutil.copyfileobj(input_file, output, length=1024 * 1024)
                    output.flush()
                    os.fsync(output.fileno())
            if self._locations.enforce_root_ownership:
                os.chown(temporary, 0, 0)
            temporary.chmod(modes[name])
            temporary.replace(destination)
            self._sync_directory(app_dir)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
            if temporary_descriptor >= 0:
                os.close(temporary_descriptor)
            temporary.unlink(missing_ok=True)

    def append_audit(self, message: str) -> None:
        """Append one root-owned, newline-free audit event and sync its contents."""

        if "\n" in message or "\r" in message:
            raise ValueError("audit message must contain exactly one line")
        if self._locations.log_path is None or self._clock is None:
            raise RuntimeError("audit location is unavailable")
        path = self._locations.log_path
        self._ensure_directory(path.parent, create_mode=0o755)
        existed = path.exists() or path.is_symlink()
        flags = self._open_flags(
            os.O_APPEND,
            os.O_CREAT,
            os.O_WRONLY,
            getattr(os, "O_CLOEXEC", 0),
            getattr(os, "O_NOFOLLOW", 0),
        )
        descriptor = os.open(path, flags, self._MODE)
        try:
            metadata = os.fstat(descriptor)
            if not stat.S_ISREG(metadata.st_mode):
                raise PermissionError(f"audit path is not a regular file: {path}")
            self._verify_owner(metadata, path)
            if hasattr(os, "fchmod"):
                os.fchmod(descriptor, self._MODE)
            else:
                path.chmod(self._MODE)
            with os.fdopen(descriptor, "a", encoding="utf-8", newline="\n") as stream:
                descriptor = -1
                stream.write(f"{self._clock()} {message}\n")
                stream.flush()
                os.fsync(stream.fileno())
            if not existed:
                self._sync_directory(path.parent)
        finally:
            if descriptor >= 0:
                os.close(descriptor)
