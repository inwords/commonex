---
id: STG-002
title: Run the existing Android UI suite against staging locally and in CI
labels: [ready-for-agent]
phase: 1
blocked_by: [STG-001]
---

## Outcome

The existing Android UI suite runs locally and in GitHub CI against the initialized staging server, with its assertions, retries, and runner behavior unchanged. Persistent test data accumulates across runs.

## Context

Follow the [accepted spec](../staging-server-and-production-deployment-gate.md). STG-001 supplies the running full production stack. Shared KMM networking currently uses a fixed API hostname, and an existing Android test requires a fixed event fixture. Address these environment prerequisites without redesigning the test suite.

## Scope

- Add an explicit API host input at the shared KMM networking boundary and select `https://staging.commonex.ru` for Android's `autotest` build. Mobile request paths already include the API prefix; do not duplicate it in the configured host.
- Keep normal Android and iOS endpoint behavior unchanged. Wire the explicit input through the existing build/application setup rather than changing test assertions or request-path conventions.
- Add an idempotent staging bootstrap step that provisions the existing suite's required fixed event after application initialization. Determine the fixture requirements from the current test and provision those requirements without committing secrets or altering unrelated accumulated events and expenses.
- Support local and GitHub CI execution through the existing Android runner and Marathon setup. Preserve existing retries, pass/fail rules, and report generation. Make staging selection explicit and document the commands and required inputs.
- Provide CI access and execution setup sufficient to run the existing suite against staging. Reuse available Android execution capacity and existing conventions; do not introduce reservations or shared test locks.

## Acceptance criteria

1. An Android `autotest` build sends its existing API requests to staging with correct paths; normal Android and iOS builds retain their current endpoint.
2. Repeated bootstrap leaves the required fixed fixture usable and preserves unrelated accumulated data.
3. The unchanged UI suite completes successfully through the existing runner locally and in GitHub CI against staging, retaining its current retry behavior and reports.
4. Developers can reproduce the staging run using documented setup, fixture provisioning, and runner commands. CI configuration contains no committed secret values.
5. No suite-wide database reset or new cleanup step removes accumulated data. Existing test deletion flows retain their current behavior, and no deployment/test coordination mechanism is added.

## Validation

Use the repository's Android validation guidance for the touched shared networking/build configuration. Verify effective endpoints for autotest and normal builds, inspect request routing for a duplicated API prefix, run fixture provisioning twice with unrelated data present, and run the existing full suite locally and in CI. Retain runner reports and distinguish environment setup failures from test outcomes.

## Operational prerequisites

STG-001 must provide healthy public staging API access. Verify local SDK/device or emulator availability, CI Android execution capacity, staging/bootstrap access, and any required fixture credentials without publishing secret values.
