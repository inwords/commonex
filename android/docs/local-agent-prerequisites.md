# Local Android Agent Prerequisites

This document consolidates the local environment and tooling requirements for running Android builds and tests from an agent or developer machine. Use it before starting a long local Android run; see also
`android/.agents/skills/run-android-local-long-task/SKILL.md`.

**Tooling versions:** Do not duplicate version numbers here or in other docs. Use the following as single sources of truth and reference them when documenting requirements:

- **Gradle:** `android/gradle/wrapper/gradle-wrapper.properties` (`distributionUrl`)
- **Android compile/target SDK:** `android/app/build.gradle.kts` (`compileSdk`, `targetSdk`)
- **JDK (for running Gradle/CI):** `.github/workflows/android.yml` (`java-version` in setup-java steps)

When updating this repo, change the version in the build/CI file above; docs that say "see …" or "match CI" stay correct.

## JDK and Android SDK

- **JDK:** Use the same version as CI (see `java-version` in `.github/workflows/android.yml`). Set `JAVA_HOME` accordingly. Project JVM target is 17; the daemon is run with the JDK from CI.
- **Android SDK:** Compile/target SDK and min SDK are in `app/build.gradle.kts`; ensure the SDK you install provides the required API level. Use the Gradle wrapper; do not install Gradle separately (version is in `gradle/wrapper/gradle-wrapper.properties`).
- **Commands:** Run `.\gradlew` from `android/` on Windows, `./gradlew` on Mac/Linux.

## local.properties and ANDROID_SDK_ROOT

- **`android/local.properties`** must exist for local builds. It typically contains `sdk.dir=C\:\\path\\to\\Android\\Sdk` (or the equivalent path on your OS). The Android Gradle Plugin reads this to find the SDK.
- **`ANDROID_SDK_ROOT`** (or `ANDROID_HOME`): if not set, Gradle uses `sdk.dir` from `local.properties`. Some tooling (e.g. Marathon, emulator) may require `ANDROID_SDK_ROOT` to be set explicitly; you can derive it from `sdk.dir` in `local.properties` (e.g.
  `C:\Android\sdk`).

## API and System Image (for device/emulator runs)

- For **instrumented tests** or **managed devices**: API level is set in CI (see `.github/workflows/android.yml`: `API_LEVEL`, AVD target `aosp_atd`, profile `pixel_6`). Locally, use an emulator or device at an API level compatible with the app (see
  `app/build.gradle.kts` for min/target SDK).
- For **Marathon**: a device or emulator must be running; `adb devices` should list at least one device.

## PowerShell Quoting (Windows)

- Use **`;`** instead of `&&` for chaining commands in PowerShell.
- **Quote `-D...` Gradle properties** so the shell does not misparse them, e.g. `"-Dcom.android.tools.r8.disableApiModeling=true"`. If Gradle still misparses, run the Gradle command via `cmd /c "..."`.

## When to Use Device, Emulator, Managed Device, or Marathon

| Need                      | When to use                                                                                                                     |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| **No device**             | Fast local gate only: `assembleDebug`, `testHostTest`, `lintDebug`. Sufficient for most code-only changes.                      |
| **Device or emulator**    | Instrumented tests (`connectedAutotestAndroidTest`), Room/device tests, or when validating on a real device.                    |
| **Gradle Managed Device** | Local runs without a pre-started emulator; e.g. `:app:pixel6Api35AtdAutotestAndroidTest`. Requires compatible SDK/system image. |
| **Marathon**              | Multi-device, sharded, retried instrumented runs; CI uses Marathon. Local use requires the Marathon distribution (see below).   |

Do not run instrumented or Marathon flows by default; use them only when the task explicitly requires device/emulator behavior (e.g. UI E2E, deeplinks, Room on device).

## Worktree-Copyable Tooling

These directories can be copied into a Cursor (or other) worktree from the main repo workspace so that local runs have the same tooling without committing binaries:

- **`android/local.properties`** – if present in the source workspace, copy it so the worktree has a valid `sdk.dir`. Do not commit this file (it is machine-specific).
- **`android/marathon/`** – Marathon distribution is not in git; extract the Marathon release into this folder manually. Required only for local Marathon runs. See `android/marathon/README.md`.
- **`android/gradle/profiler/`** – Gradle Profiler distribution is not in git; extract the profiler release into this folder manually. Required only for build benchmarking. See `android/gradle/README.md`.

Do **not** copy `build/`, `.gradle/`, generated outputs, reports, or secrets into worktrees.

## Quick Reference (CI vs local)

- **CI** (`.github/workflows/android.yml`): JDK and setup from workflow (see `java-version` there); no `local.properties` (SDK from actions); main job runs `testHostTest` + `assembleDebug` + `assembleRelease` + `bundleRelease`; separate job for UI tests with
  emulator and Marathon. Because CI runs root `testHostTest`, module host-test tasks such as `:shared:core:network:testAndroidHostTest` are included in CI host-test coverage.
- **Local**: Use the same JDK as CI; `local.properties` required; use the validation profiles in `run-android-local-long-task` (fast gate first, then broader or instrumented only when justified).
