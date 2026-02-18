# Marathon (Android Instrumented Tests)

Marathon docs: https://docs.marathonlabs.io/
Download: https://github.com/MarathonLabs/marathon/releases

Marathon is the test runner used for Android instrumented tests in this project. The Marathon library can be extracted into this folder manually to run Marathon; library files are excluded from git to save space.

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
- Android SDK path set (`ANDROID_SDK_ROOT`). If not set, derive it from `android/local.properties` (`sdk.dir`, e.g., `C:\Android\sdk`).
- Marathon configuration file: `android/Marathonfile`
- Tests must use JUnit 4 annotations for Marathon local discovery

## Run (Windows / CMD)

```cmd
rem Build APKs for Marathon
.\gradlew :app:assembleAutotest :app:assembleAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

rem Ensure Android SDK path is set
set ANDROID_SDK_ROOT=C:\Users\vasia\AppData\Local\Android\Sdk

rem Run Marathon using the local distribution
.\marathon\bin\marathon
```

PowerShell note: if Gradle reports task `.android.tools.r8.disableApiModeling` not found, run the Gradle command via `cmd /c`:

```powershell
cmd /c ".\\gradlew :app:assembleAutotest :app:assembleAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling"
```

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
- If you see `NoTestCasesFoundException`, verify:
    - `@RunWith(AndroidJUnit4::class)` is present
    - Test methods use `org.junit.Test`
    - `android/Marathonfile` has `testParserConfiguration: type: "local"`
