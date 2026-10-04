---
id: STG-003
title: Automatically deploy and validate main releases on staging
labels:
  - ready-for-agent
blocked_by:
  - STG-002
phase: 2
status: in-progress
---

## Outcome

Each applicable release from `main` is automatically activated on the persistent staging server and checked through public services and the unchanged Android suite. The result identifies the exact release and images tested.

## Context

STG-001 provides working staging delivery and STG-002 supplies staging-targeted autotest builds, fixtures, and existing Android execution. Current main delivery builds and publishes custom images for production; staging validation should reuse those release artifacts rather than build another variant.

## Scope

- Extend main delivery to prepare a staging Release from the candidate Git SHA and resolved immutable custom-image digests.
- Activate it using the existing delivery machinery with staging-specific connection details, credentials, and environment configuration.
- Wait for applicable service health and verify the public web, HTTP API, gRPC, and Grafana routes before running mobile tests.
- Reuse the Android test execution established in STG-002 and retain its existing runner behavior and retries. Build the autotest app from the candidate revision so the report records its source identity.
- Preserve application data and persistent volumes. Use the already provisioned Android fixture without recurring bootstrap or a database reset, as specified in the accepted spec.
- Publish one candidate-associated result with image digests, activation identity, health/public verification, Android reports, and relevant failure diagnostics.
- Record the previously Active Release before replacing it, so STG-004 can retain the correct rehearsal baseline and extend orchestration before the candidate's first activation.
- Expose that result for the rehearsal and production-gating tickets. This ticket supplies the initial automated checks; STG-004 extends them with the full release rehearsal and STG-005 enforces promotion.

## Acceptance Criteria

- An applicable main release starts staging validation automatically without a separate manual staging trigger.
- Staging runs the published custom-image digests recorded for the candidate; no staging image rebuild occurs.
- Android execution follows backend readiness and uses the staging autotest host, unchanged test assertions, and unchanged retry/pass rules.
- Required activation, service, or Android failures produce a failed result with usable reports rather than a promotion-ready success.
- The report records the observed Active Release. Activity that changes the candidate during validation cannot produce a misleading pass attributed solely to the original candidate.
- No server reservation, shared test lock, or new deployment/test coordination is introduced. Existing activation transaction safety and replay checks remain intact.
- A repeat deployment retains accumulated data. Credentials and certificate private material are absent from logs and artifacts.

## Validation

Exercise the actual CI entry point with an eligible main candidate, confirm the active release and image digests, and inspect its retained Android and public-service reports. Exercise a required-check failure and confirm that diagnostics survive and the candidate result fails. Use existing workflow and delivery regression checks where relevant to changed orchestration.

## Out of Scope

Branch/PR deployments, new Android UI tests, retry-policy changes, upgrade/rollback orchestration, and production approval enforcement. The latter two are owned by STG-004 and STG-005.

## Implementation and remaining verification

The main workflow now automatically activates eligible main candidates on staging,
checks public services and running-container health, runs the reusable existing
Android suite from the candidate SHA, and retains a combined candidate result.
The report includes the previous Active Release, exact digests, activation number,
Android source identity/reports, and the final observed release. Intervening
activations, failing required checks, and missing evidence fail validation.

The read-only `release-status` command preserves the existing activation lock,
configuration validation, intent checks, and replay protection. Staging uses exactly
the next observed host activation number, so an intervening activation also rejects
a stale baseline. No reservation or deployment/test lock was added. The result
explicitly records that rehearsal is not run and promotion is not yet eligible.

Local validation passed: the complete deployment regression suite ran under Linux
Python 3.9.25 (197 tests, one skipped), and all three changed workflows passed
workflow syntax and shell checks. Focused coverage includes unhealthy/missing
services, image/candidate substitution, intervening activation, missing or malformed
Android identity, failed Android jobs, and retained safe failure diagnostics.

Setup and report details are in the [staging runbook](../../../infra/deploy/staging.md#automatic-main-validation).
Before closing this ticket, install the updated host tool, configure staging CI
secrets, exercise a real main candidate and a required-check failure, inspect
retained Android/public-service evidence, and confirm persistence on repeat
activation. Live CI validation is still pending; production gating remains STG-005.
