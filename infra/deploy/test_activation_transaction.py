from __future__ import annotations

import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from infra.deploy.commonex_host.activation import (
    ActivationCommittedAuditError,
    ActivationRequest,
    ActivationTransaction,
    AmbiguousActivationCommitError,
    _ActivationDependencies,
)


RELEASE = "a" * 40
PREVIOUS = "b" * 40


class ActivationTransactionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.events: list[str] = []
        self.intent = {"candidate_release": RELEASE}

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def dependencies(self, *, fail: str = "") -> _ActivationDependencies:
        def event(name: str, result=None):
            def callback(*_args):
                self.events.append(name)
                if fail == name:
                    raise OSError(name)
                return result

            return callback

        return _ActivationDependencies(
            ensure_no_intent=event("ensure_no_intent"),
            read_state=event("read_state", (8, [RELEASE, PREVIOUS])),
            rollback_path=lambda release: self.root / f"rollback-{release}",
            validate_release=event("validate", self.root / RELEASE),
            pull_release=event("pull"),
            prepare_rollback=event("prepare"),
            backup_configuration=event("backup"),
            write_intent=event("write_intent", self.intent),
            install_release=event("install"),
            reconcile_active=event("reconcile"),
            write_state=event("write_state"),
            clear_intent=event("clear_intent"),
            restore_configuration=event("restore"),
            cleanup_releases=event("cleanup"),
            audit=event("audit"),
            audit_best_effort=event("audit_best_effort"),
        )

    def test_success_returns_small_receipt_and_preserves_transaction_order(self) -> None:
        receipt = ActivationTransaction(self.dependencies()).activate(
            ActivationRequest("deploy", RELEASE, 9)
        )

        self.assertEqual(receipt.release, RELEASE)
        self.assertEqual(receipt.history, (RELEASE, PREVIOUS))
        self.assertEqual(
            self.events,
            [
                "ensure_no_intent",
                "read_state",
                "audit",
                "validate",
                "pull",
                "prepare",
                "backup",
                "write_intent",
                "install",
                "reconcile",
                "write_state",
                "clear_intent",
                "cleanup",
                "audit",
            ],
        )

    def test_failure_after_install_restores_reconciles_and_clears_intent(self) -> None:
        reconcile_calls = 0

        def fail_candidate_reconcile() -> None:
            nonlocal reconcile_calls
            reconcile_calls += 1
            self.events.append("reconcile")
            if reconcile_calls == 1:
                raise OSError("reconcile")

        dependencies = replace(
            self.dependencies(),
            reconcile_active=fail_candidate_reconcile,
        )
        with self.assertRaisesRegex(OSError, "reconcile"):
            ActivationTransaction(dependencies).activate(
                ActivationRequest("deploy", RELEASE, 9)
            )

        self.assertEqual(self.events.count("reconcile"), 2)
        self.assertIn("restore", self.events)
        self.assertIn("clear_intent", self.events)
        self.assertEqual(self.events[-1], "audit")

    def test_intent_clear_failure_after_commit_is_ambiguous(self) -> None:
        with self.assertRaises(AmbiguousActivationCommitError):
            ActivationTransaction(self.dependencies(fail="clear_intent")).activate(
                ActivationRequest("deploy", RELEASE, 9)
            )
        self.assertEqual(self.events[-1], "audit_best_effort")
        self.assertNotIn("restore", self.events)

    def test_final_audit_failure_reports_committed_activation(self) -> None:
        dependencies = self.dependencies()
        audit_calls = 0

        def audit(_message: str) -> None:
            nonlocal audit_calls
            audit_calls += 1
            self.events.append("audit")
            if audit_calls == 2:
                raise OSError("audit")

        dependencies = replace(dependencies, audit=audit)
        with self.assertRaises(ActivationCommittedAuditError):
            ActivationTransaction(dependencies).activate(
                ActivationRequest("rollback", RELEASE, 9)
            )
        self.assertIn("write_state", self.events)
        self.assertIn("clear_intent", self.events)

    def test_invalid_operation_is_rejected_before_any_side_effect(self) -> None:
        with self.assertRaises(ValueError):
            ActivationTransaction(self.dependencies()).activate(
                ActivationRequest("resume", RELEASE, 9)
            )
        self.assertEqual(self.events, [])

    def test_stale_run_is_rejected_before_release_or_runtime_work(self) -> None:
        with self.assertRaisesRegex(ValueError, "older than or equal"):
            ActivationTransaction(self.dependencies()).activate(
                ActivationRequest("deploy", RELEASE, 8)
            )
        self.assertEqual(self.events, ["ensure_no_intent", "read_state", "audit"])

    def test_rollback_target_must_be_in_retained_history(self) -> None:
        unretained = "c" * 40
        with self.assertRaisesRegex(ValueError, "not retained"):
            ActivationTransaction(self.dependencies()).activate(
                ActivationRequest("rollback", unretained, 9)
            )
        self.assertEqual(self.events, ["ensure_no_intent", "read_state", "audit"])

    def test_each_pre_intent_fault_rejects_without_configuration_restore(self) -> None:
        for fault in ("validate", "pull", "prepare", "backup", "write_intent"):
            with self.subTest(fault=fault):
                self.events.clear()
                with self.assertRaisesRegex(OSError, fault):
                    ActivationTransaction(self.dependencies(fail=fault)).activate(
                        ActivationRequest("deploy", RELEASE, 9)
                    )
                self.assertNotIn("install", self.events)
                self.assertNotIn("restore", self.events)
                self.assertEqual(self.events[-1], "audit")

    def test_install_and_state_faults_restore_configuration_and_clear_intent(self) -> None:
        for fault in ("install", "write_state"):
            with self.subTest(fault=fault):
                self.events.clear()
                with self.assertRaisesRegex(OSError, fault):
                    ActivationTransaction(self.dependencies(fail=fault)).activate(
                        ActivationRequest("deploy", RELEASE, 9)
                    )
                self.assertIn("restore", self.events)
                self.assertIn("clear_intent", self.events)
                self.assertEqual(self.events[-1], "audit")

    def test_cleanup_fault_is_best_effort_after_commit(self) -> None:
        receipt = ActivationTransaction(self.dependencies(fail="cleanup")).activate(
            ActivationRequest("deploy", RELEASE, 9)
        )
        self.assertEqual(receipt.release, RELEASE)
        self.assertIn("audit_best_effort", self.events)
        self.assertEqual(self.events[-1], "audit")


if __name__ == "__main__":
    unittest.main()
