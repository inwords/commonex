---
name: write-unit-tests
description: Creates or updates focused unit tests across CommonEx projects. Use when asked to add, update, move, or refine unit tests for Android/KMM `commonTest` or `androidHostTest`, backend Jest suites, or web unit tests when a runner already exists. Trigger for requests covering use cases, stores, services, reducers, mappers, repositories, remote stores, view models, and other non-UI business logic with isolated assertions and narrow validation.
---

# Write Unit Tests

## Overview

Write the smallest useful tests that lock observable behavior without coupling to implementation details. Match the target project's existing test framework, source set, fixtures, and validation commands instead of inventing new patterns.

## Workflow

1. Identify the target project and read its `AGENTS.md`.
2. Choose the narrowest seam that owns the behavior: pure function, mapper, use case, store, service, repository adapter, or view model.
3. Inspect neighboring tests before writing anything. Reuse the same source set, helpers, fixture style, and naming shape.
4. Draft a short test-case plan before editing: success path, important business branches, contract or status mappings, and explicitly out-of-scope cases.
5. Extend the nearest existing test file when it already owns the seam. Create a new suite only when that seam has no clear home.
6. Assert observable behavior only: returned values, emitted state, mapped errors, outgoing requests, or persisted commands.
7. Cover the main success path first, then the behaviorally important branches from the plan.
8. Use table-driven tests only when the production code already has a compact branch or mapping table.
9. Validate with the smallest relevant command first, then broaden only if the change justifies it.

## Example Requests

- Add `commonTest` coverage for a KMM remote store error-mapping path.
- Write Jest tests for a backend use case with mocked repositories and clocks.
- Add unit tests for a web reducer or service only if the web project already has a runnable unit-test setup.

## Project Notes

### Android/KMM

- Read `android/AGENTS.md` first and follow Android MCP-first validation when available.
- Put multiplatform-safe tests in `commonTest`.
- Use `androidHostTest` only for JVM-only APIs, platform wiring, or when the module already follows that pattern.
- Reuse existing `runTest`, Turbine, `MockEngine`, and fixture helpers instead of building new harnesses.
- For adapter tests, keep assertions on request shape and code-owned result mapping; leave generic retry and transport coverage to the shared network utilities that already own it.
- Prefer focused validation first, for example `.\gradlew :module:testAndroidHostTest --tests "pkg.ClassTest"`.

### Backend

- Read `backend/AGENTS.md` first.
- Use Jest/ts-jest and match nearby tests.
- Keep the test close to the changed layer: domain and use case logic first, framework adapters second.
- Mock only true boundaries such as repositories, clocks, gateways, and external services.
- Prefer the narrowest Jest run that the repo already uses nearby, then run broader checks if needed.

### Web

- Read `web/AGENTS.md` first.
- Check `package.json` before promising unit tests.
- If no unit-test runner is wired, say so plainly and stop unless the user also wants test tooling added.
- If a runner exists in the target area, match the local style and keep tests at the requested seam.

## Test Design Rules

- Plan cases by behavior buckets, not by lines of code.
- Prefer one clear reason to fail per test.
- Assert business meaning, not private helpers, incidental ordering, or mock call counts unless those are the contract.
- Keep fixtures minimal and local unless the module already uses shared builders.
- Focus on code-owned behavior. Cover malformed upstream payloads or impossible contract violations only when production code has an explicit branch for them.
- When refactoring, preserve behavior and write tests against the existing external contract.

## Deliverable

- Leave the repo with tests in the correct source set, matched to local style, and green on the narrowest validation command that proves the change.
- Report which behavior is covered, what validation ran, and any remaining gaps that were not verified.

## Stop Conditions

- No unit-test runner exists for the target project and the user did not ask to add one.
- Existing tests and code do not define the expected behavior clearly enough to write a reliable test.
- The requested coverage is really integration, UI, or end-to-end rather than unit-level.
