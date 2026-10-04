---
title: Persistent staging server and production deployment gate
labels:
  - ready-for-agent
---

## Problem Statement

CommonEx needs a persistent staging server running the full production Docker Compose stack so developers can run existing Android UI tests against a dedicated environment and validate releases before production deployment. Current delivery targets production, mobile builds share a fixed API hostname, and existing checks do not rehearse a real container activation and rollback with a migrated database.

The team is small and wants a straightforward environment with accumulating test data, unchanged Android tests, and no additional coordination between developers and CI.

## Solution

Bring up the full production stack on a dedicated staging host, using access details supplied through private operator configuration. Expose web and HTTP API at `staging.commonex.ru`, gRPC at `staging-grpc.commonex.ru`, and Grafana at `staging-gf.commonex.ru`. Reuse the existing wildcard certificate covering these hostnames; do not issue an additional staging certificate.

Deliver this in two phases. First, establish the persistent server and run the existing Android suite against it locally and in GitHub CI. Then automatically validate each release from `main` on staging, including upgrade and rollback rehearsals, before allowing manual production promotion of the same immutable images.

Start with an empty database, initialize the application, and provision the one fixture required by the existing Android suite. Preserve the database and subsequent test data across runs and releases.

## User Stories

1. As a developer, I want a persistent staging server, so that I can test against a predictable shared environment.
2. As a developer, I want the full production Compose stack on staging, so that tests exercise the deployed service topology.
3. As a developer, I want staging web and API endpoints distinct from production, so that test traffic reaches the intended environment.
4. As a developer, I want a staging gRPC endpoint, so that I can validate that transport through the public reverse proxy.
5. As a developer, I want a staging Grafana endpoint, so that I can inspect the environment during testing.
6. As an operator, I want to reuse the existing wildcard certificate, so that staging does not require another certificate to be issued.
7. As a developer, I want the database to start empty, so that initial setup does not depend on production data.
8. As a developer, I want the existing Android fixture provisioned, so that the suite can run without changes.
9. As a developer, I want test data to accumulate, so that manual investigation and later deployments can use persistent state.
10. As an Android developer, I want to run the existing UI suite locally against staging, so that I can validate mobile flows before pushing changes.
11. As an Android developer, I want CI to run the same suite against staging, so that releases receive repeatable mobile validation.
12. As an Android developer, I want test assertions and retry behavior preserved, so that infrastructure work does not redefine the suite.
13. As a release owner, I want each applicable `main` release deployed and tested automatically on staging, so that validation happens before production promotion.
14. As a release owner, I want validation associated with immutable image digests and a Git SHA, so that I know which release passed.
15. As a release owner, I want migrations exercised during a real upgrade, so that schema changes are checked before production activation.
16. As a release owner, I want the previous application release tested against the migrated database, so that I know whether application rollback remains usable.
17. As a release owner, I want data written before and after an upgrade verified through rollback and reactivation, so that the rehearsal checks data compatibility.
18. As a release owner, I want a passing staging result followed by manual production approval, so that I retain control over promotion.
19. As an operator, I want production to use the same images tested on staging, so that promotion does not introduce an untested rebuild.
20. As a developer on a small team, I want no additional server reservations or deployment/test coordination, so that local testing stays simple.
21. As an operator, I want failed rehearsal reports and service diagnostics retained, so that I can understand why a candidate is not ready.
22. As an operator, I want database downgrade treated as an explicit recovery operation, so that ordinary rollback does not silently delete newer data.

## Implementation Decisions

- Use the full existing production Compose definition and service topology. Retain persistent storage for the database and observability services.
- Bootstrap the dedicated host using the repository's accepted namespaced host layout and existing production-delivery vocabulary: Release, Activation, Active Release, and Retained Release.
- Keep staging configuration, state, and credentials separate from production. Supply required application, exchange-rate, registry, and Grafana credentials without putting secret values in source control.
- Provide DNS for the three agreed hostnames and the network access required by the production stack, including HTTPS over TCP and HTTP/3 over UDP.
- Deliver the existing wildcard certificate and its private key to staging through an appropriate operator-controlled mechanism, including propagation of renewals. No additional certificate issuance is part of this work.
- Make staging routing and Grafana's public URL selectable through environment configuration while preserving the exact promoted custom-image digests. Do not build separate staging application or nginx images. Any extension to the validated release bundle must preserve its existing validation guarantees.
- Give Android's autotest build an explicit staging API host. Keep normal Android and iOS API configuration unchanged. Mobile request paths already include the API prefix; configure the hostname without adding that prefix again.
- Keep the Android suite, assertions, retries, and runner behavior unchanged. Its fixed event fixture is already provisioned; no recurring fixture setup is needed. Do not modify unrelated accumulated data.
- Use releases built from `main`. Branch and PR deployments are outside the initial scope. Build custom images once, resolve immutable digests, validate them on staging, and promote those same digests to production.
- Add automatic staging deployment and validation to delivery, with a passing candidate result and explicit manual approval required before production promotion. Existing production rollback remains manual.
- Introduce no reservations, shared test locks, or new coordination between local testing and CI. Preserve existing activation transaction locks, intent recovery, replay protection, and production safety mechanisms.
- Retain release identity in reports. Results spanning different candidates must not be presented as validation of one exact release; detecting that mismatch does not reserve the server or prevent overlapping activity.
- Ordinary rollback changes application images and release configuration while preserving the migrated database. Do not automatically run migration reversals or restore a database snapshot.
- Database downgrade remains a separate explicit recovery procedure requiring a backup and review of the particular migrations' reversals. Implementing a general downgrade or database recovery system is outside this spec.
- Rehearsal activations need strictly increasing activation identifiers. A candidate activation, rollback, and reactivation must not reuse an identifier rejected by the existing replay checks.
- First installation must establish a usable baseline release before claiming an upgrade/rollback rehearsal. Bootstrap success alone is not evidence that a distinct baseline-to-candidate transition passed.

## Testing Decisions

- Prefer the deployed environment as the highest existing test seam: the normal delivery entry point, public HTTPS HTTP and gRPC endpoints, existing Android runner, and persistent PostgreSQL state.
- Test externally visible behavior and data survival. Container startup alone is insufficient evidence of a healthy application or functioning observability stack.
- Reuse existing public-service verification, service health checks, Android Marathon execution, and deployment-wrapper regression coverage. Extend their environment support rather than adding parallel implementations of the same checks.
- Verify that all Compose services are present and healthy where health checks exist, public web/API/gRPC routes work, Grafana is reachable, and telemetry ingestion and datasource access function.
- Run the unchanged Android suite locally and in CI against the staging API. Preserve its current retries and pass/fail rules. Do not introduce a special failure policy for tests that pass after retry.
- Verify that the existing Android fixture remains usable and unrelated accumulated events and expenses are preserved.
- For a distinct previously deployed staging baseline release A and candidate B, run the following deployment rehearsal:
  1. Establish identifiable events, participants, and expenses under A and confirm persistence through the backend.
  2. Activate B using its immutable release images and run pending migrations.
  3. Check service health, public web and HTTP API routes, gRPC, and the existing Android suite. Create identifiable B-era records through the backend and verify remote reads before rollback.
  4. Roll back application images to retained A without reversing database migrations.
  5. Verify that A can read preserved data, read representative B-era writes, and write new data against the migrated database.
  6. Reactivate B and verify that data from all rehearsal phases survives and remains usable.
- Keep the new deployment data checks outside the Android suite. Existing mobile assertions do not by themselves prove every expense has reached the backend; the rehearsal must explicitly verify remote persistence.
- Record the baseline, candidate, image digests, applied migration history, activation outcomes, Android report, and data-check outcomes. A failed required check must not become a passing promotion result.
- Distinguish migration execution from compatibility checks against an already migrated database. Because staging data accumulates, a repeated run may skip migrations already applied; record migration history and report that limitation rather than claiming it rehearsed a fresh schema upgrade. Use the previous retained staging release as the rollback baseline; reconstructing production schema history is not an additional gate requirement.
- Promotion is successful only when the candidate has a passing staging result, receives manual approval, and uses the same tested image digests. Existing post-activation production verification remains in place.

## Out of Scope

- New or rewritten Android UI tests, assertion changes, retry changes, or a new flaky-test policy.
- Automated iOS UI tests or broader mobile compatibility matrices.
- Changing production mobile endpoints, sharing origins, or adding real operating-system app-link validation.
- Resetting the staging database between suites, importing production data, or cleaning up all test data after each run.
- New deployment/local-test coordination or reservation systems.
- PR/branch preview environments, automatic production approval, or separate staging image builds.
- Additional staging certificate issuance.
- Automatic database downgrade, automatic snapshot restoration, or a general disaster-recovery system.
- A new zero-downtime requirement or load/benchmark test program. The rehearsal must not claim uninterrupted availability solely from blue-green service naming.

## Further Notes

- Implementation is split into [five local tickets](staging-server-and-production-deployment-gate/README.md), with explicit dependencies and acceptance checks.
- Delivery order is server bootstrap and unchanged Android validation first, then automated release rehearsal and manual production gating.
- Read-only discovery found a fresh Debian host with Docker and Compose running and no CommonEx deployment. Bootstrap must verify and accommodate the intended administrative and CI access model; access details remain private.
- Existing delivery is production-specific. Environment support is required in SSH configuration, public verification, reverse-proxy routing, and Grafana URL handling.
- Existing migration reversals can drop tables or columns. A successful application rollback proves compatibility for the tested release pair; it does not prove arbitrary destructive migrations can be safely undone.
- DNS administration, access to the existing certificate/key and renewals, environment credentials, registry access, CI host access, Android execution capacity, and an appropriate baseline release are implementation prerequisites. Verify these during preparation without publishing secret values.
- This is a local issue, labelled `ready-for-agent`, as requested. No external issue publication is required.
