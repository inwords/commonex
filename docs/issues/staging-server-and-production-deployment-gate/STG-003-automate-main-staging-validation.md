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

Each applicable release from `main` is automatically activated on the persistent staging server and checked through public services, followed by the unchanged Android suite. Separate deployment and Android checks retain the activated release/image identities and the Android build source identity.

## Context

STG-001 provides working staging delivery and STG-002 supplies staging-targeted autotest builds, fixtures, and existing Android execution. Current main delivery builds and publishes custom images for production; staging validation should reuse those release artifacts rather than build another variant.

## Scope

- Extend main delivery to prepare a staging Release from the candidate Git SHA and resolved immutable custom-image digests.
- Activate it using the existing delivery machinery with staging-specific connection details, credentials, and environment configuration.
- Wait for applicable service health and verify the public web, HTTP API, gRPC, and Grafana routes before running mobile tests.
- Reuse the Android test execution established in STG-002 and retain its existing runner behavior and retries. Build the autotest app from the candidate revision so the report records its source identity.
- Preserve application data and persistent volumes. Use the already provisioned Android fixture without recurring bootstrap or a database reset, as specified in the accepted spec.
- Retain separate candidate-associated deployment and Android artifacts. The deployment report contains image digests, activation identity, health/public verification, and failure diagnostics; Android retains its source identity and existing test reports.
- Record the previously Active Release before replacing it, so STG-004 can retain the correct rehearsal baseline and extend orchestration before the candidate's first activation.
- Supply the initial automated deployment and Android checks. STG-004 owns the full release rehearsal and STG-005 enforces promotion.

## Acceptance Criteria

- An applicable main release starts staging validation automatically without a separate manual staging trigger.
- Staging runs the published custom-image digests recorded for the candidate; no staging image rebuild occurs.
- Android execution follows backend readiness and uses the staging autotest host, unchanged test assertions, and unchanged retry/pass rules.
- Activation or service failure fails the deployment check and prevents Android execution. Successful deployment records `status=passed`; an Android failure fails its own check and retains its reports without changing the deployment report.
- The deployment report records the observed Active Release and immutable image digests after activation. Android records and verifies its candidate checkout. There is no combined result or release recheck after Android.
- No server reservation, shared test lock, or new deployment/test coordination is introduced. Existing activation transaction safety and replay checks remain intact.
- A repeat deployment retains accumulated data. Credentials and certificate private material are absent from logs and artifacts.

## Validation

Exercise the actual CI entry point with an eligible main candidate, confirm deployment records the active release and image digests before Android starts, and inspect the separate deployment and Android artifacts. Verify Android records the candidate and checked-out SHAs. Exercise a required-check failure and confirm that its check fails with retained diagnostics; deployment failure must prevent Android execution. Use existing workflow and delivery regression checks where relevant to changed orchestration.

## Out of Scope

Branch/PR deployments, new Android UI tests, retry-policy changes, upgrade/rollback orchestration, and production approval enforcement. The latter two are owned by STG-004 and STG-005.

## Implementation status

Implemented in [PR #323](https://github.com/inwords/commonex/pull/323).
Setup, counter policy, and report semantics are documented in the
[staging runbook](../../../infra/deploy/staging.md#automatic-main-validation).

Host tooling is installed and staging CI secrets are configured. On main candidate
`74c0bff`, staging activation and public/release identity checks passed, and Android
passed on the second workflow attempt. Verify the simplified two-check CI entry
point and persistence using the [runbook procedure](../../../infra/deploy/staging.md#verification-and-persistence)
before closing the ticket.
