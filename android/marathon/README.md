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

- Healthy initialized staging stack at `https://staging.commonex.ru`, with the existing Android event fixture provisioned (see below)
- Python available as `python` (CI version is selected in `.github/workflows/android.yml`); the staging preflight uses only the standard library
- Device or emulator running (`adb devices` should list one)
- Android SDK configured in `ANDROID_SDK_ROOT` / `ANDROID_HOME` or `android/local.properties` (`sdk.dir`, e.g., `C:\Android\sdk`)
- Marathon configuration file: `android/Marathonfile`
- Tests must use JUnit 4 annotations for Marathon local discovery
- When running through an agent, prefer the JetBrains MCP terminal / IDE terminal and the repo script `.\scripts\run-marathon.ps1`

The repo script reads the SDK path from `ANDROID_SDK_ROOT`, `ANDROID_HOME`, or `android/local.properties`, exports both SDK env vars for the current PowerShell process, checks the public staging API and fixture, builds the autotest APKs, and then launches Marathon. Prefer that script over hand-written shell setup.

## Staging setup

Android's `autotest` build selects `staging.commonex.ru` through its build configuration. HTTPS and the existing `/api/...` request paths remain unchanged. Normal Android builds and iOS continue using their existing API endpoint.

After the staging application has initialized, an operator provisions the suite's fixed event from the repository root:

```powershell
python .\infra\deploy\bootstrap_staging_fixture.py --provision --staging-key C:\path\to\ycloud-commonex
```

Provisioning requires the staging SSH identity and the initialized deployment described in [staging operations](../../infra/deploy/staging.md). It is idempotent and preserves unrelated events and expenses; do not reset the database between runs. The command reads the fixed event requirements from the unchanged Android test and does not print its PIN.

Developers and CI only need public HTTPS access to staging. Check the fixture from `android/` with `python ../infra/deploy/bootstrap_staging_fixture.py`; no staging administrator key or additional CI fixture secret is required. The local runner performs this check automatically, including with `-SkipBuild`. A failed preflight is an environment setup failure and prevents launching the suite; restore the required fixture through the operator command before rerunning.

## Run (Windows / PowerShell and MCP terminal)

```powershell
# Verify staging, build the autotest APKs and run Marathon
.\scripts\run-marathon.ps1

# Re-run using APKs already built from this checkout with the staging endpoint
.\scripts\run-marathon.ps1 -SkipBuild

# Use a Python executable outside PATH
.\scripts\run-marathon.ps1 -PythonExecutable C:\path\to\python.exe
```

Rebuild once after adopting the staging configuration. `-SkipBuild` reuses existing APKs and cannot change their compiled endpoint.

If the script fails to resolve the SDK path, fix `android/local.properties` or set `ANDROID_SDK_ROOT` before rerunning it.
If PowerShell blocks direct script execution, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-marathon.ps1` instead.

## Run (bash)

```bash
# Verify public staging API and the existing fixed event
python ../infra/deploy/bootstrap_staging_fixture.py

# Build APKs for Marathon
./gradlew --quiet :app:assembleAutotest :app:assembleAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

# Ensure Android SDK path is set
export ANDROID_SDK_ROOT="/path/to/Android/Sdk"

# Run Marathon using the local distribution
./marathon/bin/marathon
```

## GitHub CI

The existing `ui_tests_android` job in [the mobile workflow](../../.github/workflows/android.yml) verifies the public staging API and fixture before building the staging autotest APKs. It reuses the existing Ubuntu runner, KVM setup, cached Android emulator, and Marathon distribution. There are no reservations or shared test locks.

The workflow runs for the existing Android/workflow push and pull-request paths plus fixture-script changes. After this workflow is on the default branch, a repository user with Actions write access can also select **Mobile CI/CD Pipeline → Run workflow** and choose the intended ref. An operator must have provisioned the fixture first; CI does not receive the staging SSH key. Autotest builds skip the Sentry Gradle plugin's upload tasks so UI tests require no Sentry upload token. Existing signing/Sentry inputs for other mobile jobs retain their current setup.

The suite keeps the same assertions, retry quotas, and Marathon pass/fail behavior. A successful run must include a completed Marathon result; a passing preflight or APK build alone is insufficient.

## Reports

- Results are generated in `build/reports/marathon/`
- CI retains the existing `android-test-reports` artifact even when the test step fails. Distinguish staging preflight/setup failures from test failures using the job steps and Marathon report. Local reports remain in the same directory.

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
