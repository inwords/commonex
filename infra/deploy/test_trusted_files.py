from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path

from infra.deploy.commonex_host.activation import AmbiguousActivationCommitError
from infra.deploy.commonex_host.trusted_files import (
    TrustedDurableFiles,
    _TrustedFileLocations,
)


RELEASE = "a" * 40


class TrustedDurableFilesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name) / "releases"
        self.synced: list[Path] = []
        self.store = self.make_store()

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def make_store(self, sync=None, *, enforce_root=False) -> TrustedDurableFiles:
        return TrustedDurableFiles(
            _TrustedFileLocations(
                self.root,
                enforce_root,
                app_dir=self.root.parent / "active",
                log_path=self.root.parent / "logs" / "deploy.log",
                rollback_root=self.root.parent / "rollback",
            ),
            sync_directory=sync or self.synced.append,
            clock=lambda: "2026-08-30T12:00:00+00:00",
        )

    @staticmethod
    def intent() -> dict[str, object]:
        return {
            "candidate_release": RELEASE,
            "operation": "deploy",
            "previous_release": None,
            "rollback_backup": f"deploy-{RELEASE}-20260830T120000000000Z",
            "run_number": 8,
        }

    def test_missing_state_uses_legacy_policy_and_missing_intent_is_a_value(self) -> None:
        self.root.mkdir(mode=0o700)
        legacy = self.root / "last-successful-run"
        legacy.write_bytes(b"7\n")
        legacy.chmod(0o600)
        self.assertEqual(self.store.read_activation_state(), (7, []))
        self.assertIsNone(self.store.read_activation_intent())

    def test_state_replace_is_canonical_root_only_and_directory_synced(self) -> None:
        self.store.write_activation_state(8, [RELEASE])

        state_path = self.root / "activation-state.json"
        self.assertEqual(
            state_path.read_text(encoding="utf-8"),
            json.dumps(
                {"history": [RELEASE], "last_successful_run": 8},
                separators=(",", ":"),
            )
            + "\n",
        )
        if os.name == "posix":
            self.assertEqual(state_path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.synced, [self.root])

    def test_reads_reject_duplicate_keys_oversize_wrong_mode_and_symlink(self) -> None:
        self.root.mkdir(mode=0o700)
        state_path = self.root / "activation-state.json"
        cases = [
            b'{"last_successful_run":8,"last_successful_run":9,"history":[]}\n',
            b"{" + b"x" * 8192,
        ]
        for content in cases:
            with self.subTest(size=len(content)):
                state_path.unlink(missing_ok=True)
                state_path.write_bytes(content)
                state_path.chmod(0o600)
                with self.assertRaises(ValueError):
                    self.store.read_activation_state()

        if os.name == "posix":
            state_path.write_text(
                json.dumps({"last_successful_run": 8, "history": [RELEASE]}),
                encoding="utf-8",
            )
            state_path.chmod(0o644)
            with self.assertRaisesRegex(PermissionError, "unsafe mode"):
                self.store.read_activation_state()

            state_path.unlink()
            outside = self.root.parent / "outside"
            outside.write_text("{}", encoding="utf-8")
            state_path.symlink_to(outside)
            with self.assertRaisesRegex(PermissionError, "symlink"):
                self.store.read_activation_state()

    def test_intent_removal_verifies_content_and_restores_after_sync_failure(self) -> None:
        intent = self.intent()
        self.store.persist_activation_intent(intent)
        with self.assertRaisesRegex(RuntimeError, "changed"):
            self.store.clear_activation_intent({**intent, "run_number": 9})

        failed = False

        def fail_once(path: Path) -> None:
            nonlocal failed
            intent_path = self.root / "activation-intent.json"
            if not intent_path.exists() and not failed:
                failed = True
                raise OSError("directory sync failed")
            self.synced.append(path)

        store = self.make_store(fail_once)
        with self.assertRaisesRegex(OSError, "directory sync failed"):
            store.clear_activation_intent(intent)
        self.assertTrue(failed)
        self.assertEqual(store.read_activation_intent(), intent)

    def test_state_sync_and_restoration_failure_is_explicitly_ambiguous(self) -> None:
        self.store.write_activation_state(8, [RELEASE])

        def always_fail(_path: Path) -> None:
            raise OSError("directory sync failed")

        store = self.make_store(always_fail)
        with self.assertRaises(AmbiguousActivationCommitError):
            store.write_activation_state(9, [RELEASE])

    def test_active_install_accepts_only_closed_configuration_definitions(self) -> None:
        source_dir = self.root.parent / "candidate"
        source_dir.mkdir()
        source = source_dir / ".env"
        source.write_text("KEY=value\n", encoding="utf-8")
        self.store._install_active_configuration_file(source, ".env")

        installed = self.root.parent / "active" / ".env"
        self.assertEqual(installed.read_bytes(), source.read_bytes())
        if os.name == "posix":
            self.assertEqual(installed.stat().st_mode & 0o777, 0o600)
        with self.assertRaisesRegex(ValueError, "not allowlisted"):
            self.store._install_active_configuration_file(source, "secret.txt")

    def test_release_manifest_validation_and_configuration_backup_are_semantic(self) -> None:
        self.root.mkdir(mode=0o700)
        release_dir = self.root / RELEASE
        release_dir.mkdir(mode=0o700)
        environment = release_dir / ".env"
        environment.write_text("KEY=value\n", encoding="utf-8")
        environment.chmod(0o600)
        compose = release_dir / "docker-compose-prod.yml"
        compose.write_text("services: {}\n", encoding="utf-8")
        compose.chmod(0o644)
        self.store.write_release_manifest(release_dir)
        self.assertEqual(self.store.validate_release_documents(RELEASE), release_dir)

        active = self.root.parent / "active"
        active.mkdir()
        (active / ".env").write_bytes(environment.read_bytes())
        (active / ".env").chmod(0o600)
        (active / "docker-compose-prod.yml").write_bytes(compose.read_bytes())
        (active / "docker-compose-prod.yml").chmod(0o644)
        rollback = self.root.parent / "rollback"
        rollback.mkdir(mode=0o700)
        slot = rollback / f"deploy-{RELEASE}-20260830T120000000000Z"
        slot.mkdir(mode=0o700)
        self.store.backup_active_configuration(slot)

        (active / ".env").write_text("changed=true\n", encoding="utf-8")
        self.store.restore_configuration(slot)
        self.assertEqual((active / ".env").read_bytes(), environment.read_bytes())

    def test_audit_append_rejects_newlines_and_creates_exact_mode(self) -> None:
        self.store.append_audit("RESULT deploy status=PASS")
        log_path = self.root.parent / "logs" / "deploy.log"
        self.assertEqual(
            log_path.read_text(encoding="utf-8"),
            "2026-08-30T12:00:00+00:00 RESULT deploy status=PASS\n",
        )
        if os.name == "posix":
            self.assertEqual(log_path.stat().st_mode & 0o777, 0o600)
        with self.assertRaisesRegex(ValueError, "exactly one line"):
            self.store.append_audit("safe\nforged")

    @unittest.skipUnless(
        os.name == "posix" and hasattr(os, "chown") and os.geteuid() == 0,
        "root POSIX process is required to test ownership",
    )
    def test_root_owned_policy_rejects_wrong_owner(self) -> None:
        self.root.mkdir(mode=0o700)
        state_path = self.root / "activation-state.json"
        state_path.write_text(
            json.dumps({"last_successful_run": 8, "history": [RELEASE]}),
            encoding="utf-8",
        )
        state_path.chmod(0o600)
        os.chown(state_path, 1, 1)
        with self.assertRaisesRegex(PermissionError, "not owned by root"):
            self.make_store(enforce_root=True).read_activation_state()


if __name__ == "__main__":
    unittest.main()
