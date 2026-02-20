# Database Benchmarking (Room + Android Device Test)

This guide documents the repeatable workflow for one-off Room database research benchmarks.

Results from completed researches belong in `android/docs/database-research-log.md`, following `android/docs/database-research-log-template.md`.

## Tooling policy (mandatory for future researches)

- Use `androidx.benchmark` as the primary measurement harness for all new performance researches.
- Treat plain `androidDeviceTest` timing as auxiliary only (setup validation, migration/seed correctness, quick smoke checks).
- Keep `shared.room.testing` + `MigrationTestHelper` for deterministic schema and data seeding, but collect decision-grade timing via `androidx.benchmark` (`BenchmarkRule` / `measureRepeated`).
- If `androidx.benchmark` cannot be used for a specific research, record the blocker and mark results as provisional in `android/docs/database-research-log.md`.

## Template class

Start from:

- `android/shared/integration/databases/src/androidDeviceTest/kotlin/com/inwords/expenses/integration/databases/data/benchmark/DatabaseResearchBenchmarkTemplateTest.kt`

The template is intentionally `@Ignore`-ed. Copy and rename it for each research, then remove `@Ignore`.

## Benchmark location

- Module: `android/shared/integration/databases`
- Source set: `src/androidDeviceTest`
- Package: `com.inwords.expenses.integration.databases.data.benchmark`

`androidDeviceTest` in this module already includes:

- `shared.room.testing`
- `androidx.test.runner`
- `androidx.test.ext.junit`

## Canonical setup

Use `MigrationTest` as reference for schema setup with Room testing:

- `android/shared/integration/databases/src/androidDeviceTest/kotlin/com/inwords/expenses/integration/databases/data/migration/MigrationTest.kt`

Important behavior:

- `MigrationTestHelper.createDatabase(...)` bypasses Room callbacks.
- Seed required base tables manually before opening with Room.

## Recommended workflow

1. Build 2 profiles for A/B comparison (baseline vs changed behavior).
2. Create DB files with `MigrationTestHelper` using the same schema version and seed data.
3. Open both through Room with symmetric configuration.

For baseline-vs-pragmas A/B, avoid using app runtime callbacks directly in test setup. Build each profile explicitly in the benchmark class so only the intended difference changes between profiles.

4. Warm up queries/writes before measuring.
5. Measure multiple samples with `measureNanoTime`.
6. Log median and average with a stable tag.
7. Assert checksum or equivalent guard to ensure work is executed.
8. Close and delete `db`, `-wal`, and `-shm` files.

## A/B protocol (mandatory)

Use this protocol to avoid biased or non-reproducible results.

1. Isolate workloads:

- Read benchmark and write benchmark must run on separate DB pairs.
- Never benchmark read and write on the same DB instance in one run.

2. Use paired ABBA ordering per sample:

- Sample 0: A then B.
- Sample 1: B then A.
- Alternate for all samples to reduce thermal and temporal drift bias.

3. Keep one-variable-only diffs:

- Build both Room variants explicitly in benchmark class.
- Keep schema, seed shape, query coroutine context, driver, migrations, callback stack identical except for the tested variable.

4. Keep data shape deterministic:

- Seed identical cardinality and relationship shape for A/B.
- Use deterministic IDs/indexing in benchmark loops.

5. Add correctness guards:

- Keep checksum assertions so compiler/runtime cannot skip actual work.
- Log WAL size before read benchmarks when WAL-sensitive behavior is evaluated.

6. Run multiple clean process-level runs:

- Run each class at least 3 times.
- For decision-grade conclusions, prefer 5 runs.
- Clear `logcat` before each run.
- Aggregate by averaging run-level deltas (do not report one run).

## Running locally (PowerShell)

Run commands from `android/`.

1. Verify device:

```powershell
C:\Android\sdk\platform-tools\adb devices
```

2. Clear logs:

```powershell
C:\Android\sdk\platform-tools\adb logcat -c
```

3. Run one benchmark class:

```powershell
.\gradlew :shared:integration:databases:connectedAndroidDeviceTest -P"android.testInstrumentationRunnerArguments.class=com.inwords.expenses.integration.databases.data.benchmark.<YourBenchmarkClass>"
```

4. Extract logs by benchmark tag:

```powershell
C:\Android\sdk\platform-tools\adb logcat -d | rg <YourBenchmarkTag>
```

5. Repeat a class 3 times with clean logs (example):

```powershell
$testClass = "com.inwords.expenses.integration.databases.data.benchmark.<YourBenchmarkClass>"
for ($run = 1; $run -le 3; $run++) {
  C:\Android\sdk\platform-tools\adb logcat -c
  .\gradlew :shared:integration:databases:connectedAndroidDeviceTest -P"android.testInstrumentationRunnerArguments.class=$testClass"
  C:\Android\sdk\platform-tools\adb logcat -d | rg <YourBenchmarkTag>
}
```

## Repro tips

- Run 3-5 times and compare medians, not one run.
- Keep device state stable (charging, no heavy background work).
- Keep seed shape close to production usage.
- Change one variable at a time.
