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

Automatically deploy applicable `main` releases to persistent staging and run the existing Android suite.

## Context

STG-001 provides staging delivery; STG-002 provides staging-targeted autotest builds and fixtures. Reuse their machinery and the published main release images. The [staging runbook](../../../infra/deploy/staging.md#automatic-main-validation) defines setup, activation counters, job order, and separate check/artifact semantics.

## Scope

- Connect main delivery to the staging environment using its dedicated access and runtime inputs.
- Integrate public-service verification and the shared Android runner.
- Retain candidate evidence and the previously Active Release for STG-004's rehearsal baseline.

## Acceptance Criteria

- Applicable main pushes start automatically; staging runs the candidate's published immutable custom-image digests without rebuilding them.
- Service health and public web, HTTP API, gRPC, and Grafana checks pass before Android starts. The autotest build verifies its candidate checkout and preserves STG-002's assertions, runner behavior, and retry/pass rules.
- Required failures fail the responsible check with retained diagnostics. Deployment failure prevents Android execution.
- Accumulated data and the provisioned fixture survive deployments. Existing activation safety remains intact, with no new reservations or deployment/test coordination. Credentials and certificate private material stay out of logs and artifacts.

## Validation

Run an eligible main candidate through CI and compare retained deployment identities with the published image digests and Android source identity. Exercise a required failure in each check and inspect its evidence; confirm a deployment failure prevents Android execution. Verify persistence using the [runbook procedure](../../../infra/deploy/staging.md#verification-and-persistence).

Include a main run where optional backend or web checks are skipped: after a successful staging deployment, Android must still execute. Inspect the actual job results; local contract tests and lint do not reproduce GitHub's scheduler.

## Out of Scope

Branch/PR deployments, new Android UI tests, retry-policy changes, upgrade/rollback orchestration, and production approval enforcement. The latter two are owned by STG-004 and STG-005.

## Implementation status

Implemented in [PR #323](https://github.com/inwords/commonex/pull/323); [PR #330](https://github.com/inwords/commonex/pull/330) simplifies the validation checks.

Host tooling is installed and staging CI secrets are configured. On main candidate
`74c0bff`, staging activation and public/release identity checks passed, and Android
passed on the second workflow attempt. The simplified CI entry point and
persistence validation above remain pending before closing the ticket.
