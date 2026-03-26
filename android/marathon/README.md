# Marathon (Android Instrumented Tests)

Marathon docs: https://docs.marathonlabs.io/
Download: https://github.com/MarathonLabs/marathon/releases

Marathon is the test runner used for Android instrumented tests in this project. The Marathon library can be extracted into this folder manually to run Marathon; library files are excluded from git to save space.

For local Compose UI test validation, prefer Marathon over `:app:connectedAutotestAndroidTest`. Marathon is the closer match to the CI runner because it provides retries and sharding, so it is the default path for agent-driven UI validation in this repo.

Run from the `android/` directory.

## What it provides

- Intelligent test sharding and batching
- Automatic retry of flaky tests
- Parallel execution across multiple devices
- HTML reports with screenshots/video

## Installation (local distribution)

1) Download the latest Marathon release
2) Extract the distribution into `android/marathon/`

## Prerequisites

- Device or emulator running (`adb devices` should list one)
- Android SDK configured in `ANDROID_SDK_ROOT` / `ANDROID_HOME` or `android/local.properties` (`sdk.dir`, e.g., `C:\Android\sdk`)
- Marathon configuration file: `android/Marathonfile`
- Tests must use JUnit 4 annotations for Marathon local discovery
- When running through an agent, prefer the JetBrains MCP terminal / IDE terminal and the repo script `.\scripts\run-marathon.ps1`

The repo script reads the SDK path from `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or `android/local.properties`, exports both SDK env vars for the current PowerShell process, builds the autotest APKs, and then launches Marathon. Prefer that script over hand-written shell setup.

## Run (Windows / PowerShell and MCP terminal)

```powershell
# Build the autotest APKs and run Marathon
.\scripts\run-marathon.ps1

# Re-run Marathon without rebuilding the APKs
.\scripts\run-marathon.ps1 -SkipBuild
```

If the script fails to resolve the SDK path, fix `android/local.properties` or set `ANDROID_SDK_ROOT` before rerunning it.
If PowerShell blocks direct script execution, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-marathon.ps1` instead.

## Run (bash)

```bash
# Build APKs for Marathon
./gradlew :app:assembleAutotest :app:assembleAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

# Ensure Android SDK path is set
export ANDROID_SDK_ROOT="/path/to/Android/Sdk"

# Run Marathon using the local distribution
./marathon/bin/marathon
```

## Reports

- Results are generated in `build/reports/marathon/`

## Marathonfile configuration

Key settings in `android/Marathonfile`:

- `applicationApk` / `testApplicationApk`
- `autoGrantPermission`
- `testParserConfiguration` (use `type: "local"` for JUnit 4 discovery)
- `retryStrategy`
- `batchingStrategy`

## Notes

- **Important:** Instrumented tests must use **JUnit 4** (not JUnit 5). Marathon's local test parser only recognizes `org.junit.Test` annotations, not `org.junit.jupiter.api.Test`.
- **Windows consistency:** Keep Windows Marathon docs and agent instructions PowerShell-first. Do not mix `cmd /c` examples into PowerShell guidance; use `.\scripts\run-marathon.ps1` instead.
- If you see `NoTestCasesFoundException`, verify:
    - `@RunWith(AndroidJUnit4::class)` is present
    - Test methods use `org.junit.Test`
    - `android/Marathonfile` has `testParserConfiguration: type: "local"`
