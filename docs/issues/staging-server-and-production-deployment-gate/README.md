# Staging Server Implementation Tickets

These local issues implement the [agreed specification](../staging-server-and-production-deployment-gate.md). Each ticket is labelled `ready-for-agent`; its `blocked_by` field identifies prerequisite tickets rather than additional triage.

## Delivery Order

| Ticket | Outcome | Depends on | Phase |
| --- | --- | --- | --- |
| [STG-001](STG-001-bring-up-staging-stack.md) | Persistent full production stack with working staging endpoints | None | 1 |
| [STG-002](STG-002-run-android-ui-tests-on-staging.md) | Unchanged Android suite runs locally and in CI against staging | STG-001 | 1 |
| [STG-003](STG-003-automate-main-staging-validation.md) | Each main release automatically deploys and receives staging validation | STG-002 | 2 |
| [STG-004](STG-004-rehearse-upgrade-and-rollback.md) | Real upgrade, application rollback, and reactivation preserve usable data | STG-003 | 2 |
| [STG-005](STG-005-gate-production-promotion.md) | Passing exact release requires manual approval before production promotion | STG-004 | 2 |

Phase 1 delivers the initial goal: a live server and existing mobile UI tests. Phase 2 adds the full pre-deployment gate. Each ticket owns its final verification and applicable operational documentation.

## Shared Constraints

- Use `staging.commonex.ru`, `staging-grpc.commonex.ru`, and `staging-gf.commonex.ru` with the existing wildcard certificate.
- Preserve accumulating staging data and all existing Android test assertions and retry behavior.
- Configure the shared KMM networking host through Android's autotest build; normal Android and iOS endpoints stay unchanged.
- Test and promote the same immutable custom-image digests from `main`.
- Introduce no local-test reservations or deployment/test coordination; retain existing activation safety mechanisms.
- Ordinary application rollback preserves the migrated database. Database downgrade is separate explicit recovery, outside implementation of this gate.
- A fresh installation establishes a baseline; it does not count as a completed upgrade/rollback rehearsal.

DNS, certificate delivery and renewal, environment credentials, registry access, CI access, and Android execution capacity are operational prerequisites to verify during their owning tickets. No secret values belong in these issues or reports.
