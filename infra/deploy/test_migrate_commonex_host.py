import contextlib
import errno
import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile
from typing import Iterator
import unittest
import unittest.mock as mock

from infra.deploy import install_commonex_deploy as installer
from infra.deploy import migrate_commonex_host as migration


TOOL_SHA = "0123456789abcdef0123456789abcdef01234567"


def file_record(path: Path, relative: str = "") -> dict:
    metadata = path.lstat()
    record = {
        "path": relative or str(path),
        "kind": "file",
        "mode": f"{stat.S_IMODE(metadata.st_mode):04o}",
        "uid": metadata.st_uid,
        "gid": metadata.st_gid,
        "size": metadata.st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "hash_status": "hashed",
    }
    return record


def directory_target(name: str, path: Path) -> dict:
    metadata = path.lstat()
    entries = []
    for child in sorted(path.rglob("*")):
        relative = child.relative_to(path).as_posix()
        child_metadata = child.lstat()
        if child.is_dir():
            entries.append(
                {
                    "path": relative,
                    "kind": "directory",
                    "mode": f"{stat.S_IMODE(child_metadata.st_mode):04o}",
                    "uid": child_metadata.st_uid,
                    "gid": child_metadata.st_gid,
                    "size": child_metadata.st_size,
                }
            )
        else:
            entries.append(file_record(child, relative))
    return {
        "name": name,
        "path": str(path),
        "exists": True,
        "readable": True,
        "kind": "directory",
        "mode": f"{stat.S_IMODE(metadata.st_mode):04o}",
        "uid": metadata.st_uid,
        "gid": metadata.st_gid,
        "size": metadata.st_size,
        "entries": entries,
        "truncated": False,
    }


class MigrateCommonExHostTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.legacy_state = self.root / "legacy-state"
        self.legacy_state.mkdir()
        (self.legacy_state / "activation-state.json").write_text(
            json.dumps({"last_successful_run": 7, "history": ["a" * 40]}) + "\n",
            encoding="utf-8",
        )
        release = self.legacy_state / ("a" * 40)
        release.mkdir()
        (release / "docker-compose-prod.yml").write_text("services: {}\n")
        self.legacy_audit = self.root / "legacy-deploy.log"
        self.legacy_audit.write_text("status=PASS\n", encoding="utf-8")
        os.chmod(self.legacy_audit, 0o600)
        self.legacy_lock = self.root / "legacy.lock"
        self.legacy_lock.write_bytes(b"")
        os.chmod(self.legacy_lock, 0o600)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        (self.bundle / "commonex_deploy.py").write_text(
            "#!/usr/bin/env python3\nprint('tool')\n", encoding="utf-8"
        )
        install_layout = installer.InstallLayout.under(self.root / "host")
        self.layout = migration.MigrationLayout(
            legacy_state=self.legacy_state,
            canonical_state=self.root / "canonical-state",
            legacy_audit=self.legacy_audit,
            canonical_audit=self.root / "canonical-logs" / "deploy.log",
            legacy_lock=self.legacy_lock,
            canonical_lock=self.root / "runtime" / "deploy.lock",
            rollback_root=self.root / "migration-rollbacks",
            installer=install_layout,
        )

    def test_load_report_reads_a_trusted_regular_file(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text('{"schema_version": 1}\n', encoding="utf-8")
        report_path.chmod(0o600)

        report = migration._load_report(
            report_path,
            enforce_root_ownership=False,
        )

        self.assertEqual(report, {"schema_version": 1})

    def test_load_report_rejects_duplicate_json_keys(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text(
            '{"status": "complete", "status": "blocked"}\n',
            encoding="utf-8",
        )
        report_path.chmod(0o600)

        with self.assertRaisesRegex(migration.MigrationError, "valid inventory"):
            migration._load_report(
                report_path,
                enforce_root_ownership=False,
            )

    def test_load_report_rejects_a_symlink(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text("{}\n", encoding="utf-8")
        report_path.chmod(0o600)
        linked_report = self.root / "linked-inventory.json"
        try:
            linked_report.symlink_to(report_path)
        except OSError as error:
            self.skipTest(f"file symlinks are unavailable: {error}")

        with self.assertRaisesRegex(migration.MigrationError, "trusted regular file"):
            migration._load_report(
                linked_report,
                enforce_root_ownership=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_load_report_requires_exact_mode(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text("{}\n", encoding="utf-8")
        report_path.chmod(0o644)

        with self.assertRaisesRegex(migration.MigrationError, "root-owned and immutable"):
            migration._load_report(
                report_path,
                enforce_root_ownership=False,
            )

    def test_load_report_rejects_an_inode_swap_before_open(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text("{}\n", encoding="utf-8")
        report_path.chmod(0o600)
        replacement = self.root / "replacement.json"
        replacement.write_text('{"status": "fabricated"}\n', encoding="utf-8")
        replacement.chmod(0o600)
        real_open = migration.os.open

        def replace_before_open(path: str, flags: int) -> int:
            os.replace(replacement, report_path)
            return real_open(path, flags)

        with (
            mock.patch.object(migration.os, "open", side_effect=replace_before_open),
            self.assertRaisesRegex(migration.MigrationError, "root-owned and immutable"),
        ):
            migration._load_report(
                report_path,
                enforce_root_ownership=False,
            )

    def test_load_report_validates_ancestors_before_opening(self) -> None:
        report_path = self.root / "inventory.json"
        report_path.write_text("{}\n", encoding="utf-8")
        report_path.chmod(0o600)

        with (
            mock.patch.object(
                migration,
                "_verify_trusted_directory_ancestors",
                side_effect=migration.MigrationError(
                    "inventory report namespace is not trusted"
                ),
            ) as verify_ancestors,
            mock.patch.object(migration.os, "open") as open_report,
            self.assertRaisesRegex(migration.MigrationError, "namespace is not trusted"),
        ):
            migration._load_report(report_path, enforce_root_ownership=True)

        verify_ancestors.assert_called_once_with(
            report_path,
            "inventory report",
            enforce_root_ownership=True,
        )
        open_report.assert_not_called()

    def report(self) -> dict:
        return {
            "schema_version": 1,
            "status": "complete",
            "migration_blocked": False,
            "blockers": [],
            "inventory_issues": [],
            "operation_locks": [
                {
                    "path": str(self.legacy_lock),
                    "exists": True,
                    "status": "held_shared",
                }
            ],
            "targets": [
                directory_target("legacy_release_state", self.legacy_state),
                {
                    "name": "canonical_release_state",
                    "path": str(self.layout.canonical_state),
                    "exists": False,
                },
                {
                    "name": "legacy_audit_log",
                    "path": str(self.legacy_audit),
                    "exists": True,
                    "readable": True,
                    **file_record(self.legacy_audit),
                },
                {
                    "name": "canonical_audit_log",
                    "path": str(self.layout.canonical_audit),
                    "exists": False,
                },
                {
                    "name": "legacy_operation_lock",
                    "path": str(self.legacy_lock),
                    "exists": True,
                    "readable": True,
                    **file_record(self.legacy_lock),
                },
                (
                    directory_target(
                        "canonical_runtime",
                        self.layout.canonical_lock.parent,
                    )
                    if self.layout.canonical_lock.parent.exists()
                    else {
                        "name": "canonical_runtime",
                        "path": str(self.layout.canonical_lock.parent),
                        "exists": False,
                    }
                ),
            ],
            "activation_states": [
                {
                    "path": str(self.legacy_state / "activation-state.json"),
                    "exists": True,
                    "valid": True,
                    "last_successful_run": 7,
                    "history": ["a" * 40],
                }
            ],
            "activation_intents": [
                {
                    "path": str(self.legacy_state / "activation-intent.json"),
                    "exists": False,
                }
            ],
            "active_release_verification": {
                "status": "verified",
                "release": "a" * 40,
            },
        }

    def test_canonical_layout_accepts_reviewed_legacy_paths_from_inventory(self) -> None:
        layout = migration.MigrationLayout.canonical(
            Path("/etc/commonex"),
            Path("/etc/commonex/deploy.log"),
            Path("/etc/commonex/deploy.lock"),
        )

        self.assertTrue(layout.mixed_configuration_state)
        self.assertEqual(layout.canonical_state, Path("/var/lib/commonex"))
        self.assertEqual(layout.legacy_audit, Path("/etc/commonex/deploy.log"))
        self.assertEqual(layout.legacy_lock, Path("/etc/commonex/deploy.lock"))

    def test_mistyped_legacy_lock_is_rejected_before_lock_creation(self) -> None:
        mistyped_lock = self.root / "mistyped.lock"
        layout = migration.MigrationLayout(
            legacy_state=self.layout.legacy_state,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=mistyped_lock,
            canonical_lock=self.layout.canonical_lock,
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
        )

        with (
            mock.patch.object(migration, "_migration_locks") as migration_locks,
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "not held by the inventory"
            ),
        ):
            migration.execute_migration(
                self.report(),
                self.bundle,
                TOOL_SHA,
                layout,
                apply=True,
                require_root=False,
            )

        migration_locks.assert_not_called()
        stage_version.assert_not_called()
        self.assertFalse(mistyped_lock.exists())

    def test_incomplete_inventory_fails_closed(self) -> None:
        report = self.report()
        report["status"] = "incomplete"
        report["migration_blocked"] = True
        report["inventory_issues"] = ["unreadable_target:legacy_release_state"]

        with self.assertRaisesRegex(migration.MigrationError, "complete inventory"):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_activation_intent_fails_closed_even_if_report_status_is_wrong(self) -> None:
        report = self.report()
        report["activation_intents"][0]["exists"] = True

        with self.assertRaisesRegex(migration.MigrationError, "Activation Intent"):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_dry_run_verifies_inputs_but_does_not_create_canonical_paths(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=False,
            require_root=False,
        )

        self.assertEqual(result["status"], "planned")
        self.assertFalse(self.layout.canonical_state.exists())
        self.assertFalse(self.layout.canonical_audit.exists())
        self.assertFalse(self.layout.installer.base.exists())

    def test_changed_source_after_inventory_is_rejected(self) -> None:
        report = self.report()
        (self.legacy_state / "activation-state.json").write_text("changed\n")

        with self.assertRaisesRegex(
            migration.MigrationError, "changed since inventory"
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_apply_revalidates_complete_inventory_after_acquiring_locks(self) -> None:
        report = self.report()
        sudo_policy = self.root / "sudo-policy"
        sudo_policy.write_text("original\n", encoding="utf-8")
        report["targets"].append(
            {
                "name": "sudo_policy",
                "path": str(sudo_policy),
                "exists": True,
                "readable": True,
                **file_record(sudo_policy),
            }
        )

        @contextlib.contextmanager
        def mutate_after_lock(
            *_args: object, **_kwargs: object
        ) -> Iterator[dict[Path, tuple[int, int]]]:
            sudo_policy.write_text("changed\n", encoding="utf-8")
            yield {}

        with (
            mock.patch.object(migration, "_migration_locks", mutate_after_lock),
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "changed since inventory"
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_changed_non_migration_inventory_target_is_rejected(self) -> None:
        report = self.report()
        sudo_policy = self.root / "sudo-policy"
        sudo_policy.write_text("original\n", encoding="utf-8")
        report["targets"].append(
            {
                "name": "sudo_policy",
                "path": str(sudo_policy),
                "exists": True,
                "readable": True,
                **file_record(sudo_policy),
            }
        )
        sudo_policy.write_text("changed\n", encoding="utf-8")

        with self.assertRaisesRegex(
            migration.MigrationError, "changed since inventory"
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_apply_allows_executor_created_lock_artifacts(self) -> None:
        runtime = self.root / "runtime"
        self.layout = migration.MigrationLayout(
            legacy_state=self.layout.legacy_state,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.layout.legacy_lock,
            canonical_lock=runtime / "deploy.lock",
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
        )
        report = self.report()

        result = migration.execute_migration(
            report,
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )

        self.assertEqual(result["status"], "applied")
        self.assertTrue(self.layout.canonical_lock.is_file())

    @unittest.skipUnless(os.name == "posix", "POSIX umask semantics")
    def test_lock_creation_normalizes_runtime_mode_under_restrictive_umask(self) -> None:
        previous_umask = os.umask(0o077)
        try:
            with migration._migration_locks(
                self.layout,
                require_lock_support=True,
                enforce_root_ownership=False,
            ):
                self.assertEqual(
                    stat.S_IMODE(self.layout.canonical_lock.parent.stat().st_mode),
                    0o755,
                )
        finally:
            os.umask(previous_umask)

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_plan_rejects_a_mutable_preexisting_canonical_runtime(self) -> None:
        runtime = self.layout.canonical_lock.parent
        runtime.mkdir()
        runtime.chmod(0o777)
        self.addCleanup(runtime.chmod, 0o755)
        report = self.report()

        with (
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError,
                "canonical operation-lock directory",
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_apply_rejects_lock_replaced_after_acquisition(self) -> None:
        runtime = self.root / "runtime"
        runtime.mkdir()
        self.layout = migration.MigrationLayout(
            legacy_state=self.layout.legacy_state,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.layout.legacy_lock,
            canonical_lock=runtime / "deploy.lock",
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
        )
        self.layout.canonical_lock.write_bytes(b"")
        os.chmod(self.layout.canonical_lock, 0o600)
        report = self.report()

        @contextlib.contextmanager
        def replace_lock(
            *_args: object, **_kwargs: object
        ) -> Iterator[dict[Path, tuple[int, int]]]:
            held_lock = self.layout.canonical_lock.with_suffix(".held")
            os.link(self.layout.canonical_lock, held_lock)
            try:
                metadata = self.layout.canonical_lock.stat()
                self.layout.canonical_lock.unlink()
                self.layout.canonical_lock.write_bytes(b"")
                os.chmod(self.layout.canonical_lock, 0o600)
                yield {
                    self.layout.canonical_lock: (metadata.st_dev, metadata.st_ino)
                }
            finally:
                held_lock.unlink()

        with (
            mock.patch.object(migration, "_migration_locks", replace_lock),
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "changed since inventory"
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        stage_version.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_existing_lock_with_unsafe_mode_is_rejected_without_mutation(self) -> None:
        lock = self.root / "unsafe.lock"
        lock.write_bytes(b"")
        lock.chmod(0o644)

        with self.assertRaisesRegex(
            migration.MigrationError,
            "operation-lock path is not trusted",
        ):
            with migration._exclusive_lock(
                lock,
                require_lock_support=True,
                enforce_root_ownership=False,
            ):
                self.fail("unsafe lock was acquired")

        self.assertEqual(stat.S_IMODE(lock.stat().st_mode), 0o644)

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_plan_rejects_unsafe_existing_lock_without_mutation(self) -> None:
        self.legacy_lock.chmod(0o644)
        report = self.report()

        with (
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError,
                "operation-lock path is not trusted",
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

        stage_version.assert_not_called()
        self.assertEqual(stat.S_IMODE(self.legacy_lock.stat().st_mode), 0o644)

    def test_apply_rejects_symlinked_executor_created_lock_root(self) -> None:
        runtime = self.root / "runtime"
        real_runtime = self.root / "real-runtime"
        self.layout = migration.MigrationLayout(
            legacy_state=self.layout.legacy_state,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.layout.legacy_lock,
            canonical_lock=runtime / "deploy.lock",
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
        )
        report = self.report()

        @contextlib.contextmanager
        def symlink_lock_root(
            *_args: object, **_kwargs: object
        ) -> Iterator[dict[Path, tuple[int, int]]]:
            real_runtime.mkdir()
            try:
                runtime.symlink_to(real_runtime, target_is_directory=True)
            except OSError as error:
                self.skipTest(f"directory symlinks are unavailable: {error}")
            self.layout.canonical_lock.write_bytes(b"")
            os.chmod(self.layout.canonical_lock, 0o600)
            metadata = self.layout.canonical_lock.stat()
            yield {
                self.layout.canonical_lock: (metadata.st_dev, metadata.st_ino)
            }

        with (
            mock.patch.object(migration, "_migration_locks", symlink_lock_root),
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "changed since inventory"
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_apply_rejects_removed_executor_created_lock_root(self) -> None:
        runtime = self.root / "runtime"
        self.layout = migration.MigrationLayout(
            legacy_state=self.layout.legacy_state,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.layout.legacy_lock,
            canonical_lock=runtime / "deploy.lock",
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
        )
        report = self.report()

        @contextlib.contextmanager
        def removed_lock_root(
            *_args: object, **_kwargs: object
        ) -> Iterator[dict[Path, tuple[int, int]]]:
            runtime.mkdir()
            self.layout.canonical_lock.write_bytes(b"")
            metadata = self.layout.canonical_lock.stat()
            self.layout.canonical_lock.unlink()
            runtime.rmdir()
            yield {
                self.layout.canonical_lock: (metadata.st_dev, metadata.st_ino)
            }

        with (
            mock.patch.object(migration, "_migration_locks", removed_lock_root),
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "changed since inventory"
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_non_root_group_audit_is_rejected(self) -> None:
        with (
            mock.patch.object(migration.os, "name", "posix"),
            self.assertRaisesRegex(migration.MigrationError, "root:root"),
        ):
            migration._validate_audit_metadata(
                {"kind": "file", "mode": "0600", "uid": 0, "gid": 1},
                enforce_root_ownership=True,
            )

    def test_apply_verifies_copied_audit_metadata_before_tool_staging(self) -> None:
        original_assert_metadata = migration._assert_metadata

        def assert_metadata(path: Path, record: dict, label: str) -> None:
            if label == "copied audit log":
                raise migration.MigrationError("copied audit metadata rejected")
            original_assert_metadata(path, record, label)

        with (
            mock.patch.object(
                migration, "_assert_metadata", side_effect=assert_metadata
            ),
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "copied audit metadata"
            ),
        ):
            migration.execute_migration(
                self.report(),
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_apply_copies_and_verifies_before_switching_tool_authority(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )

        self.assertEqual(result["status"], "applied")
        self.assertEqual(
            (self.layout.canonical_state / "activation-state.json").read_bytes(),
            (self.legacy_state / "activation-state.json").read_bytes(),
        )
        self.assertEqual(
            self.layout.canonical_audit.read_bytes(), self.legacy_audit.read_bytes()
        )
        self.assertTrue(self.legacy_state.exists())
        self.assertTrue(self.legacy_audit.exists())
        self.assertEqual(
            self.layout.installer.current.resolve(),
            self.layout.installer.versions / TOOL_SHA,
        )
        receipt = json.loads(
            (self.layout.canonical_state / "host-layout-migration.json").read_text()
        )
        self.assertEqual(receipt["tool_git_sha"], TOOL_SHA)
        self.assertEqual(receipt["status"], "applied")

    def test_existing_canonical_state_requires_manual_reconciliation(self) -> None:
        self.layout.canonical_state.mkdir()
        report = self.report()
        report["targets"][1] = directory_target(
            "canonical_release_state", self.layout.canonical_state
        )

        with self.assertRaisesRegex(migration.MigrationError, "both legacy and canonical"):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_failed_tool_rollback_retains_canonical_data_for_new_authority(self) -> None:
        with (
            mock.patch.object(
                migration,
                "_atomic_write",
                side_effect=OSError("receipt write failed"),
            ),
            mock.patch.object(
                installer,
                "rollback_activation",
                side_effect=installer.InstallError("rollback failed"),
            ),
            self.assertRaisesRegex(
                migration.MigrationError,
                "canonical data was retained",
            ),
        ):
            migration.execute_migration(
                self.report(),
                self.bundle,
                TOOL_SHA,
                self.layout,
                apply=True,
                require_root=False,
            )

        self.assertTrue(self.layout.canonical_state.exists())
        self.assertTrue(self.layout.canonical_audit.exists())

    def test_mixed_etc_layout_moves_only_persistent_state(self) -> None:
        mixed = self.root / "etc-commonex"
        mixed.mkdir()
        (mixed / "app").mkdir()
        (mixed / "app" / "docker-compose-prod.yml").write_text("active: true\n")
        (mixed / "activation-state.json").write_text(
            json.dumps({"last_successful_run": 7, "history": ["a" * 40]}) + "\n"
        )
        release = mixed / ("a" * 40)
        release.mkdir()
        (release / "docker-compose-prod.yml").write_text("release: true\n")
        abandoned_release = mixed / ("b" * 40)
        abandoned_release.mkdir()
        (abandoned_release / "docker-compose-prod.yml").write_text(
            "abandoned: true\n"
        )
        rollback = mixed / "rollback"
        rollback.mkdir()
        (rollback / "saved.env").write_text("safe=true\n")
        mixed_audit = mixed / "deploy.log"
        mixed_audit.write_text("status=PASS\n")
        os.chmod(mixed_audit, 0o600)
        layout = migration.MigrationLayout(
            legacy_state=mixed,
            canonical_state=self.layout.canonical_state,
            legacy_audit=mixed_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.legacy_lock,
            canonical_lock=self.layout.canonical_lock,
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
            mixed_configuration_state=True,
        )
        report = self.report()
        report["targets"][0] = directory_target("configuration", mixed)
        report["activation_states"] = [
            {
                "path": str(mixed / "activation-state.json"),
                "exists": True,
                "valid": True,
                "last_successful_run": 7,
                "history": ["a" * 40],
            }
        ]
        report["activation_intents"] = [
            {"path": str(mixed / "activation-intent.json"), "exists": False}
        ]
        report["legacy_runs"] = []
        report["releases"] = [
            {"path": str(release), "release": "a" * 40, "valid": True},
            {
                "path": str(abandoned_release),
                "release": "b" * 40,
                "valid": True,
            },
        ]

        migration.execute_migration(
            report,
            self.bundle,
            TOOL_SHA,
            layout,
            apply=True,
            require_root=False,
        )

        self.assertTrue((layout.canonical_state / "activation-state.json").is_file())
        self.assertTrue((layout.canonical_state / ("a" * 40)).is_dir())
        self.assertFalse((layout.canonical_state / ("b" * 40)).exists())
        self.assertTrue(abandoned_release.is_dir())
        self.assertTrue((layout.canonical_state / "rollback" / "saved.env").is_file())
        self.assertFalse((layout.canonical_state / "app").exists())
        self.assertTrue((mixed / "app" / "docker-compose-prod.yml").is_file())
        self.assertEqual(layout.canonical_audit.read_bytes(), mixed_audit.read_bytes())

    def test_mixed_layout_rejects_a_missing_retained_release(self) -> None:
        mixed = self.root / "etc-commonex"
        mixed.mkdir()
        (mixed / "activation-state.json").write_text(
            json.dumps(
                {
                    "last_successful_run": 7,
                    "history": ["a" * 40, "b" * 40],
                }
            )
            + "\n"
        )
        release = mixed / ("a" * 40)
        release.mkdir()
        (release / "docker-compose-prod.yml").write_text("release: true\n")
        layout = migration.MigrationLayout(
            legacy_state=mixed,
            canonical_state=self.layout.canonical_state,
            legacy_audit=self.layout.legacy_audit,
            canonical_audit=self.layout.canonical_audit,
            legacy_lock=self.layout.legacy_lock,
            canonical_lock=self.layout.canonical_lock,
            rollback_root=self.layout.rollback_root,
            installer=self.layout.installer,
            mixed_configuration_state=True,
        )
        report = self.report()
        report["targets"][0] = directory_target("configuration", mixed)
        report["activation_states"] = [
            {
                "path": str(mixed / "activation-state.json"),
                "exists": True,
                "valid": True,
                "last_successful_run": 7,
                "history": ["a" * 40, "b" * 40],
            }
        ]
        report["activation_intents"] = [
            {"path": str(mixed / "activation-intent.json"), "exists": False}
        ]
        report["legacy_runs"] = []
        report["releases"] = [
            {"path": str(release), "release": "a" * 40, "valid": True}
        ]

        with (
            mock.patch.object(installer, "stage_version") as stage_version,
            self.assertRaisesRegex(
                migration.MigrationError, "unique valid retained release"
            ),
        ):
            migration.execute_migration(
                report,
                self.bundle,
                TOOL_SHA,
                layout,
                apply=False,
                require_root=False,
            )

        stage_version.assert_not_called()

    def test_rollback_restores_tool_and_quarantines_canonical_copies(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        receipt = Path(result["receipt"])

        rollback = migration.rollback_migration(
            receipt, self.layout, apply=True, require_root=False
        )

        self.assertEqual(rollback["status"], "rolled_back")
        self.assertFalse(self.layout.canonical_state.exists())
        self.assertFalse(self.layout.canonical_audit.exists())
        self.assertTrue(Path(rollback["quarantine_directory"]).is_dir())
        self.assertTrue(self.legacy_state.exists())
        self.assertTrue(self.legacy_audit.exists())

    def test_rollback_validates_a_missing_legacy_lock_namespace(self) -> None:
        self.layout.legacy_lock.unlink(missing_ok=True)
        self.assertFalse(self.layout.legacy_lock.exists())
        with (
            mock.patch.object(migration, "_read_receipt", return_value={}),
            mock.patch.object(migration, "_verify_canonical_runtime"),
            mock.patch.object(
                migration,
                "_verify_lock_namespace",
                side_effect=migration.MigrationError(
                    "operation-lock namespace is not trusted"
                ),
            ) as verify_namespace,
            self.assertRaisesRegex(
                migration.MigrationError,
                "operation-lock namespace is not trusted",
            ),
        ):
            migration.rollback_migration(
                self.layout.rollback_root / "receipt.json",
                self.layout,
                apply=False,
                require_root=True,
            )

        verify_namespace.assert_called_once_with(
            self.layout.legacy_lock,
            enforce_root_ownership=True,
        )

    def test_rollback_allows_append_only_audit_growth(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        receipt = Path(result["receipt"])
        with self.layout.canonical_audit.open("a", encoding="utf-8") as stream:
            stream.write("status=PASS validation=true\n")

        rollback = migration.rollback_migration(
            receipt, self.layout, apply=True, require_root=False
        )

        quarantined = Path(rollback["quarantine_directory"]) / "deploy.log"
        self.assertIn("validation=true", quarantined.read_text(encoding="utf-8"))

    def test_rollback_rejects_a_symlinked_migration_receipt(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        receipt = Path(result["receipt"])
        external_receipt = self.root / "external-receipt.json"
        external_receipt.write_bytes(receipt.read_bytes())
        receipt.unlink()
        try:
            receipt.symlink_to(external_receipt)
        except OSError as error:
            self.skipTest(f"file symlinks are unavailable: {error}")

        with self.assertRaisesRegex(
            migration.MigrationError,
            "valid migration receipt",
        ):
            migration.rollback_migration(
                receipt,
                self.layout,
                apply=False,
                require_root=False,
            )

    @unittest.skipUnless(os.name == "posix", "POSIX permission semantics")
    def test_rollback_requires_exact_migration_receipt_mode(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        receipt = Path(result["receipt"])
        receipt.chmod(0o644)
        self.addCleanup(receipt.chmod, 0o600)

        with self.assertRaisesRegex(
            migration.MigrationError,
            "valid migration receipt",
        ):
            migration.rollback_migration(
                receipt,
                self.layout,
                apply=False,
                require_root=False,
            )

    def test_cross_filesystem_quarantine_keeps_source_until_copy_is_durable(
        self,
    ) -> None:
        source = self.root / "source.log"
        destination_directory = self.root / "quarantine"
        destination_directory.mkdir()
        destination = destination_directory / "deploy.log"
        source.write_text("status=PASS validation=true\n", encoding="utf-8")

        def fail_destination_sync(path: Path) -> None:
            if path == destination:
                raise OSError("destination fsync failure")

        with (
            mock.patch.object(
                migration.os,
                "replace",
                side_effect=OSError(errno.EXDEV, "cross-device move"),
            ),
            mock.patch.object(
                migration,
                "_fsync_file",
                side_effect=fail_destination_sync,
            ),
            self.assertRaisesRegex(OSError, "destination fsync failure"),
        ):
            migration._durable_move(source, destination)

        self.assertEqual(
            source.read_text(encoding="utf-8"),
            "status=PASS validation=true\n",
        )

    def test_cross_filesystem_quarantine_syncs_both_parent_directories(
        self,
    ) -> None:
        source_directory = self.root / "source"
        source_directory.mkdir()
        source = source_directory / "deploy.log"
        destination_directory = self.root / "quarantine"
        destination_directory.mkdir()
        destination = destination_directory / "deploy.log"
        source.write_text("audit\n", encoding="utf-8")

        with (
            mock.patch.object(
                migration.os,
                "replace",
                side_effect=OSError(errno.EXDEV, "cross-device move"),
            ),
            mock.patch.object(
                migration,
                "_fsync_directory",
                wraps=migration._fsync_directory,
            ) as fsync_directory,
        ):
            migration._durable_move(source, destination)

        self.assertFalse(source.exists())
        self.assertEqual(destination.read_text(encoding="utf-8"), "audit\n")
        fsync_directory.assert_any_call(destination_directory)
        fsync_directory.assert_any_call(source_directory)

    def test_rollback_refuses_if_canonical_state_changed_after_migration(self) -> None:
        result = migration.execute_migration(
            self.report(),
            self.bundle,
            TOOL_SHA,
            self.layout,
            apply=True,
            require_root=False,
        )
        receipt = Path(result["receipt"])
        (self.layout.canonical_state / "activation-state.json").write_text("changed\n")

        with self.assertRaisesRegex(migration.MigrationError, "changed after migration"):
            migration.rollback_migration(
                receipt, self.layout, apply=True, require_root=False
            )


if __name__ == "__main__":
    unittest.main()
