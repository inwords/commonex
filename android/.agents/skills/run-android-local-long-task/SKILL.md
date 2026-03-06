---
name: run-android-local-long-task
description: Orchestrate one long, high-quality local Android agent run. Use when the user asks for a sustained Android task, long local run, or general Android development that should follow a clear validation and reporting workflow. Start from android/AGENTS.md; classify the task, choose validation depth, and follow stop/ask rules.
---

# Run Android Local Long Task

## Overview

Use this skill when running a single long local Android task. Read `android/AGENTS.md` first. Classify the task by touched area, pick the smallest sufficient validation profile, escalate only when justified, and end with a standard report.

## Preflight (before substantive work)

1. **Start from** `android/AGENTS.md` – read it for build commands, architecture, and validation steps.
2. **Confirm local readiness** using `android/docs/local-agent-prerequisites.md`:
    - JDK and Android SDK versions match the build/CI (see that doc for sources of truth: `gradle-wrapper.properties`, `app/build.gradle.kts`, `.github/workflows/android.yml`); `local.properties` / `ANDROID_SDK_ROOT` set.
    - If the task needs device/emulator, Marathon, or Gradle Profiler: ensure they are available or that the worktree has the copyable tooling (e.g. `marathon/`, `gradle/profiler/`) as documented there.
3. **Classify the task** (see below) so you pick the right validation profile and avoid unnecessary instrumented or release flows.

## Task Classification

Classify the task by what is touched:

| Touched area                                                               | Category              | Route or validation                                                                                                       |
|----------------------------------------------------------------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------|
| Release version, baseline profiles, tagging                                | **Release**           | Use `prepare-mobile-release` skill; do not use this long-run skill for release-only work.                                 |
| New or edited instrumented UI tests, screen objects, BasicInstrumentedTest | **UI test authoring** | Use `add-ui-test` skill for the test authoring part; use this skill only if the broader task includes other Android work. |
| `shared/**/composeResources/**`, strings, localization only                | **Resources**         | Fast local gate.                                                                                                          |
| `app/src/androidTest/**` only (no production code)                         | **Test-only**         | Fast gate + optionally instrumented run if device/Marathon available.                                                     |
| `shared/**` (KMM), `buildSrc/**`, `gradle/*.toml`, DB/navigation           | **Broad**             | Broader host-side validation; consider iOS targets if shared code changed.                                                |
| `baselineprofile/**`, profile generation                                   | **Profile**           | Defer to release/profile flow or dedicated docs; not default long-run path.                                               |
| Default (single feature, app module, UI-only logic)                        | **Standard**          | Fast local gate first; escalate if failures or risk.                                                                      |

## Validation Profiles

Apply in order; stop once the chosen profile passes.

### Fast local gate (use first for most tasks)

Run from `android/` with `.\gradlew` (Windows) or `./gradlew` (Mac/Linux):

1. `.\gradlew assembleDebug`
2. `.\gradlew testHostTest`
3. `.\gradlew lintDebug`

Use for: Standard, Resources, or when the change is clearly limited in scope.

### Broader host-side validation

Use when the task touches `shared/`, DB, navigation, or build logic:

1. `.\gradlew test`
2. `.\gradlew testHostTest`
3. `.\gradlew lint --continue`
4. `.\gradlew :shared:integration:base:linkDebugFrameworkIosSimulatorArm64` (if shared/KMM code changed; KMM defines iosArm64 and iosSimulatorArm64 only, no iosX64)

### Instrumented path (only when the task requires it)

Use only when the task explicitly involves device/emulator behavior, UI E2E, or deeplinks:

1. `.\gradlew assembleAutotest` and `.\gradlew :app:assembleAutotestAndroidTest`
2. Then one of:
    - Connected device/emulator: `.\gradlew :app:connectedAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"`
    - Managed device: `.\gradlew :app:pixel6Api35AtdAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"`
    - Marathon: see `android/marathon/README.md` (requires local `marathon/` distribution)

PowerShell: quote `-D...` properties (e.g. `"-Dcom.android.tools.r8.disableApiModeling=true"`). If Gradle misparses, use `cmd /c "..."` for that command.

## Stop / Ask Conditions

- **Stop and ask** when:
    - The same command or step fails repeatedly (e.g. 2–3 times) with no progress.
    - Environment is missing (no SDK, wrong JDK vs CI, no device when instrumented run is required).
    - The task is ambiguous or conflicts with repo guidance; do not guess.
    - User explicitly asked for confirmation before a destructive or high-impact action.
- **Do not** run release flow, baseline profile generation, or heavy benchmark automation as part of the default long run; route those to dedicated skills or docs.

## Final Report Structure

At the end of a long run, provide:

1. **Summary**: What was done (scope, main files, validation level run).
2. **Verified**: What was built/tested/linted and succeeded.
3. **Skipped or deferred**: What was not run and why (e.g. no device, user asked to skip).
4. **Blockers**: Any remaining issues or follow-up questions.
5. **Next steps**: Concrete suggestions (e.g. run instrumented tests locally, commit, open PR).

Keep the report concise; avoid duplicating full command logs unless relevant to a failure.

## Key References

- `android/AGENTS.md` – main Android instructions and commands
- `android/docs/local-agent-prerequisites.md` – local environment and worktree copy rules
- `android/.agents/skills/add-ui-test/SKILL.md` – UI test authoring
- `android/.agents/skills/prepare-mobile-release/SKILL.md` – release SOP
