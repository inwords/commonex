# Gradle Tooling

## Gradle Versions Plugin Usage

GitHub: [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)

To check for dependency updates run:
`./gradlew dependencyUpdates --refresh-dependencies -Drevision=release`

Report location:
`./build/dependencyUpdates/report.txt`

To update JDK, see [gradle-daemon-jvm.properties](gradle-daemon-jvm.properties)
and [android.yml](../../.github/workflows/android.yml).
Available distributions are listed at:
https://github.com/actions/setup-java?tab=readme-ov-file#supported-distributions

## Gradle Build Benchmarks (Gradle Profiler)

Gradle Profiler is used to benchmark build performance.

Locations:

- Scenario file: `android/gradle/performance.scenarios` (run using `gradle/performance.scenarios` from `android/`)
- Profiler distribution: `android/gradle/profiler/`

The Gradle Profiler library must be extracted into `android/gradle/profiler/` manually;
its binaries are excluded from git to save space.

Download: https://github.com/gradle/gradle-profiler/releases

Run from the `android/` directory.

### CI/CD benchmark (clean build)

## Run (Windows / CMD)

```cmd
rem CI/CD benchmark
.\gradle\profiler\bin\gradle-profiler --benchmark --project-dir . --scenario-file gradle\performance.scenarios clean_build --no-daemon
```

## Run (bash)

```bash
# CI/CD benchmark
./gradle/profiler/bin/gradle-profiler --benchmark --project-dir . --scenario-file gradle/performance.scenarios clean_build --no-daemon
```

### Local debug benchmark

## Run (Windows / CMD)

```cmd
rem Local debug benchmark
.\gradle\profiler\bin\gradle-profiler --benchmark --project-dir . --scenario-file gradle\performance.scenarios debug_build
```

## Run (bash)

```bash
# Local debug benchmark
./gradle/profiler/bin/gradle-profiler --benchmark --project-dir . --scenario-file gradle/performance.scenarios debug_build
```

## Baseline Profiles

Baseline-profile generation and startup macrobenchmarks live in the dedicated `android/baselineprofile/` module.

Current source of truth:

- Generator: `baselineprofile/src/main/java/ru/commonex/baselineprofile/BaselineProfileGenerator.kt`
- Startup benchmark: `baselineprofile/src/main/java/ru/commonex/baselineprofile/StartupBenchmarks.kt`
- Managed-device setup: `baselineprofile/build.gradle.kts`

Current operational behavior:

- The module targets `:app`.
- Managed device name: `pixel6Api34`
- Managed-device generation is enabled through the baseline-profile Gradle plugin.
- `useConnectedDevices = false`, so the checked-in default path is managed-device based.

Common commands from `android/`:

```bash
# Generate the release baseline profile through the target app plugin wiring
./gradlew :app:generateBaselineProfile

# Run startup macrobenchmarks for the baselineprofile module
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

Current scope notes:

- The checked-in generator optimizes startup only; it does not yet script deeper user journeys.
- The startup benchmark compares `CompilationMode.None()` and `CompilationMode.Partial(BaselineProfileMode.Require)`.
- The startup benchmark is intended for physical-device validation; emulators are weaker for performance conclusions.

Prefer keeping baseline-profile operational guidance here and leaving `android/AGENTS.md` to short pointers.
