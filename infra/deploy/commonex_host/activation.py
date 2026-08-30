"""Activation transaction with one interface and explicit outcome semantics."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional


class ConfigurationRestoreError(RuntimeError):
    """Both activation and configuration restoration failed."""


class AmbiguousActivationCommitError(RuntimeError):
    """The authoritative activation outcome cannot be confirmed."""


class ActivationCommittedAuditError(RuntimeError):
    """Activation committed but its final audit record failed."""


@dataclass(frozen=True)
class ActivationRequest:
    operation: str
    release: str
    run_number: int


@dataclass(frozen=True)
class CommittedActivation:
    operation: str
    release: str
    run_number: int
    history: tuple[str, ...]
    rollback_directory: Path


@dataclass(frozen=True)
class _ActivationDependencies:
    ensure_no_intent: Callable[[], None]
    read_state: Callable[[], tuple[int, list[str]]]
    rollback_path: Callable[[str], Path]
    validate_release: Callable[[str], Path]
    pull_release: Callable[[Path], None]
    prepare_rollback: Callable[[Path], None]
    backup_configuration: Callable[[Path], None]
    write_intent: Callable[
        [str, str, int, Optional[str], Path], dict[str, object]
    ]
    install_release: Callable[[Path], None]
    reconcile_active: Callable[[], None]
    write_state: Callable[[int, list[str]], None]
    clear_intent: Callable[[dict[str, object]], None]
    restore_configuration: Callable[[Path], None]
    cleanup_releases: Callable[[list[str]], None]
    audit: Callable[[str], None]
    audit_best_effort: Callable[[str], None]


class ActivationTransaction:
    """Make one Release authoritative or establish a precise failure outcome."""

    def __init__(self, dependencies: _ActivationDependencies) -> None:
        self._dependencies = dependencies

    @staticmethod
    def _identifier(operation: str, release: str) -> str:
        if operation == "rollback":
            return f"target={release}"
        return f"release={release}"

    def activate(
        self,
        request: ActivationRequest,
    ) -> CommittedActivation:
        operation = request.operation
        release = request.release
        run_number = request.run_number
        if operation not in {"deploy", "rollback"}:
            raise ValueError("activation operation is invalid")
        if (
            len(release) != 40
            or any(character not in "0123456789abcdef" for character in release)
        ):
            raise ValueError("release id must be a lowercase 40-character Git SHA")
        if (
            isinstance(run_number, bool)
            or not isinstance(run_number, int)
            or run_number < 1
        ):
            raise ValueError("deployment run number must be a positive integer")
        dependencies = self._dependencies
        identifier = self._identifier(operation, release)
        dependencies.ensure_no_intent()
        last_successful_run, history = dependencies.read_state()
        if run_number <= last_successful_run:
            dependencies.audit(
                f"RESULT {operation} {identifier} run={run_number} status=REJECTED "
                f"last_successful_run={last_successful_run}"
            )
            raise ValueError(
                "activation run is older than or equal to the last success"
            )
        if operation == "rollback" and release not in history:
            dependencies.audit(
                f"RESULT rollback target={release} run={run_number} "
                "status=REJECTED reason=target_not_retained"
            )
            raise ValueError("rollback target is not retained in activation history")
        rollback_directory = dependencies.rollback_path(release)
        dependencies.audit(
            f"ACTION {operation} {identifier} run={run_number} "
            "consequence=replace_allowlisted_config_and_reconcile_compose "
            f"rollback={rollback_directory} result=START"
        )

        backups_ready = False
        installation_started = False
        runtime_started = False
        activation_intent: Optional[dict[str, object]] = None
        next_history = [release, *(item for item in history if item != release)][:3]
        try:
            directory = dependencies.validate_release(release)
            dependencies.pull_release(directory)
            dependencies.prepare_rollback(rollback_directory)
            dependencies.backup_configuration(rollback_directory)
            backups_ready = True
            activation_intent = dependencies.write_intent(
                operation,
                release,
                run_number,
                history[0] if history else None,
                rollback_directory,
            )
            installation_started = True
            dependencies.install_release(directory)
            runtime_started = True
            dependencies.reconcile_active()
            dependencies.write_state(run_number, next_history)
            try:
                dependencies.clear_intent(activation_intent)
            except Exception as clear_error:
                raise AmbiguousActivationCommitError(
                    "activation committed but its intent could not be durably cleared; "
                    "manual reconciliation is required"
                ) from clear_error
        except AmbiguousActivationCommitError as activation_error:
            dependencies.audit_best_effort(
                f"RESULT {operation} {identifier} run={run_number} "
                "status=AMBIGUOUS_COMMIT configuration_restored=NOT_ATTEMPTED "
                f"error={type(activation_error).__name__}"
            )
            raise
        except Exception as activation_error:
            if backups_ready and installation_started:
                try:
                    dependencies.restore_configuration(rollback_directory)
                    dependencies.reconcile_active()
                except Exception as restore_error:
                    failure_prefix = (
                        f"RESULT rollback {identifier} run={run_number} status=FAILED"
                        if operation == "rollback"
                        else f"RESULT deploy {identifier} status=FAILED run={run_number}"
                    )
                    dependencies.audit(
                        f"{failure_prefix} configuration_restored=FAILED "
                        f"runtime_may_have_changed={str(runtime_started).lower()} "
                        f"activation_error={type(activation_error).__name__} "
                        f"restore_error={type(restore_error).__name__}"
                    )
                    raise ConfigurationRestoreError(
                        "activation failed and configuration restoration also failed"
                    ) from restore_error
                restoration = "PASS"
            else:
                restoration = "NOT_NEEDED"

            if activation_intent is not None:
                try:
                    dependencies.clear_intent(activation_intent)
                except Exception as clear_error:
                    dependencies.audit_best_effort(
                        f"RESULT {operation} {identifier} run={run_number} "
                        "status=AMBIGUOUS_COMMIT configuration_restored="
                        f"{restoration} error={type(clear_error).__name__}"
                    )
                    raise AmbiguousActivationCommitError(
                        "configuration was restored but its activation intent could "
                        "not be durably cleared; manual reconciliation is required"
                    ) from clear_error

            failure_prefix = (
                f"RESULT rollback {identifier} run={run_number} status=FAILED"
                if operation == "rollback"
                else f"RESULT deploy {identifier} status=FAILED run={run_number}"
            )
            dependencies.audit(
                f"{failure_prefix} configuration_restored={restoration} "
                f"runtime_may_have_changed={str(runtime_started).lower()} "
                f"error={type(activation_error).__name__}"
            )
            raise

        try:
            dependencies.cleanup_releases(next_history)
        except Exception as cleanup_error:
            dependencies.audit_best_effort(
                f"RESULT cleanup status=FAILED error={type(cleanup_error).__name__}"
            )
        try:
            if operation == "rollback":
                dependencies.audit(
                    f"RESULT rollback {identifier} run={run_number} "
                    "configuration_restored=NOT_NEEDED status=PASS "
                    f"rollback={rollback_directory}"
                )
            else:
                dependencies.audit(
                    f"RESULT deploy {identifier} run={run_number} "
                    f"rollback={rollback_directory} status=PASS"
                )
        except Exception as audit_error:
            raise ActivationCommittedAuditError(
                f"activation committed but final audit failed for {operation} "
                f"{identifier} run={run_number}"
            ) from audit_error

        return CommittedActivation(
            operation=operation,
            release=release,
            run_number=run_number,
            history=tuple(next_history),
            rollback_directory=rollback_directory,
        )
