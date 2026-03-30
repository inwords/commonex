---
name: add-ui-test
description: Create or update Android Compose instrumented UI tests for the CommonEx app. Use when asked to add or edit BasicInstrumentedTest flows, screen objects under android/app/src/androidTest, or Compose UI test helpers for end-to-end backend scenarios.
---

# Add UI Test

## Overview

Write or modify Android Compose UI instrumented tests in `android/app/src/androidTest` using the existing screen-object and context-receiver pattern. Tests must be true backend end-to-end scenario flows; keep assertions focused and avoid duplicate checks.

## Workflow

1. Confirm scenario and constraints.
   Keep the test as end-to-end against the real backend and avoid mocks.
   Prefer long, coherent user flows with assertions only at critical transitions.
   Avoid hardcoded remote fixtures by creating required data within the test flow.
   When the scenario depends on persisted timestamps or ordering, still create data through the real UI. If exact historical ordering is required, prefer a small `@VisibleForTesting` backdoor mutation-time seam at write time over DI/store seeding.

2. Reuse or extend screen objects.
   Use or add classes in `android/app/src/androidTest/kotlin/ru/commonex/screens/`.
   Keep methods `context(rule: ComposeTestRule)` and return the next screen to support chaining.
   Prefer test tags for new selectors; if missing, add a tag in production Compose, otherwise use `getString(Res.string...)`.
   Use BaseScreen waits like `waitForElementWithText` and `waitUntilNodeCount`; avoid sleeps.
   Use `performScrollTo()` for off-screen elements.

3. Add or update the test in `BasicInstrumentedTest`.
   Stay on JUnit4 with `AndroidJUnit4` for Marathon compatibility.
   Use `ComposeTestRule.runTest {}` with the existing `RuleChain` and `ConnectivityRule`.
   Factor repeated setup into helpers inside the test class, such as `createLocalEvent`.
   For deeplinks, reuse `waitForShareUrl` and `triggerActivityOnNewIntent` patterns.

4. Handle connectivity and offline paths.
   Use `@Offline` to force offline tests and rely on `ConnectivityRule`.
   If toggling network inside a test, wrap in `try/finally` and restore connectivity.

## Conventions and Pitfalls

Prefer stable selectors in this order: test tags, resource strings, raw literals.
Keep long flows but avoid duplicated checks between steps or across tests.
Avoid `TestScope` or `StandardTestDispatcher` in instrumented Compose tests; keep the `runTest` wrapper.
Ensure clipboard or async actions are awaited before assertions.

## Key Files

- `android/app/src/androidTest/kotlin/ru/commonex/BasicInstrumentedTest.kt`
- `android/app/src/androidTest/kotlin/ru/commonex/testUtils.kt`
- `android/app/src/androidTest/kotlin/ru/commonex/ConnectivityRule.kt`
- `android/app/src/androidTest/kotlin/ru/commonex/screens/*.kt`
