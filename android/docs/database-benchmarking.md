# Android Database Benchmarking

## Policy

- Primary harness: `androidx.benchmark.junit4.BenchmarkRule` + `measureRepeated`.
- Primary metrics source: AndroidX benchmark outputs (`time_nanos_*` and benchmark JSON/artifacts when available).
- DB benchmarks run only from `:benchmarks:databases`.
- Runner/config must stay benchmark-grade: `AndroidBenchmarkRunner`, `android:debuggable="false"`, non-debuggable test build type (`release` in this module).
- Decision-grade runs use a connected physical device. Emulator runs are provisional.
- Default execution mode: full-suite invocation (`connectedReleaseAndroidTest`) with process-level repeats.
- One class = one aspect. One `@Test` = one absolute metric on one DB config.
- Read/write/mixed workloads must be isolated into separate classes.
- A/B comparison is done offline from artifacts; do not mix A/B in one measured loop.
- Do not reset DB on each `measureRepeated` iteration.
- For write-heavy scenarios, use bounded writes over a fixed seeded row set.
- No suppressions by default. If any suppression is used, mark results as provisional.
- Benchmark classes are maintained as a permanent suite. Do not delete scenario classes after a run; refactor and keep structure clean.
- `MigrationTestHelper` is migration-testing only. Do not use it for benchmarking.

## Benchmark Code Structure

- Core helpers:
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/core/DatabaseBenchmarkSupport.kt`
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/core/DbBenchmarkCaseRunner.kt`
- Template:
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/template/DatabaseResearchBenchmarkTemplateTest.kt`
- Scenario packages:
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/scenarios/pragmas/`
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/scenarios/synchronous/`
    - `android/benchmarks/databases/src/androidTest/kotlin/com/inwords/expenses/benchmarks/databases/benchmark/scenarios/transactions/`
- Aggregation tool:
    - `android/benchmarks/databases/tools/run_connected_benchmark_suite.py`

## Correctness Checklist

- One-variable diff between baseline and variant.
- Deterministic seed and fixed workload size.
- DAO-driven workloads with multi-table access patterns.
- Validate effective PRAGMA values for tested configs.
- Keep setup/reset outside `measureRepeated` (use `runWithMeasurementDisabled` only when setup must happen inside the benchmark loop).
- Seed and checkpoint once before `measureRepeated`; no per-iteration reseed/reset.
- Verify work execution via one post-loop assertion on the last measured result.
- Keep write workload shape bounded so DB cardinality does not drift during measurement.
- Use separate DB files for each metric/config pair.
- Cleanup DB, `-wal`, `-shm` artifacts.
- Process isolation per run: fresh instrumentation process, `force-stop` + `pm clear` for test/target packages.
- Emit stable metadata logs (`DB_BENCH_CASE`) with `aspect`, `metric`, `label`.

## Runbook (Physical Device)

1. Install benchmark tests once:
    - `./gradlew :benchmarks:databases:installReleaseAndroidTest`
2. Execute full suite:
    - `./gradlew :benchmarks:databases:connectedReleaseAndroidTest`
3. Repeat process-level runs (default: 5). Fast repeat path without rebuild:
    - `adb -s <SERIAL> logcat -c`
    - `adb -s <SERIAL> shell am force-stop com.inwords.expenses.benchmarks.databases.test`
    - `adb -s <SERIAL> shell am force-stop com.inwords.expenses.benchmarks.databases`
    - `adb -s <SERIAL> shell pm clear com.inwords.expenses.benchmarks.databases.test`
    - `adb -s <SERIAL> shell pm clear com.inwords.expenses.benchmarks.databases`
    - `adb -s <SERIAL> shell am instrument -w -r com.inwords.expenses.benchmarks.databases.test/androidx.benchmark.junit4.AndroidBenchmarkRunner`
4. Cross-platform repeated Gradle full-suite runs + JSON aggregation (recommended):
    - `py -3 android/benchmarks/databases/tools/run_connected_benchmark_suite.py --android-dir android --runs 5`
    - artifact retention:
        - minimal (default): `--artifact-level minimal` (keeps only final aggregate CSVs)
        - full: `--artifact-level full` (keeps per-run logs/JSON and per-run summaries)
    - outputs are written to `android/build/db-benchmark-connectedRelease-r<runs>-<timestamp>/`

## Artifact Workflow

1. Run benchmark batches and generate minimal artifacts (cross-platform):
    - `py -3 android/benchmarks/databases/tools/run_connected_benchmark_suite.py --android-dir android --runs 5 --artifact-level minimal`
2. Keep these files per batch in `android/build/db-benchmark-connectedRelease-r<runs>-<timestamp>/`:
    - `combined-summary-timeNs.csv`
    - `readable-ab-deltas.csv`
3. For multi-batch research (for example 5x5), create `android/docs/artifacts/<research-id>/` with aggregate files and source-batch references (no copied batch CSVs):
    - `aggregate-<N>runs-ab-deltas.csv`
    - `aggregate-<N>runs-absolute-medians.csv`
    - `README.md` (must list referenced source batch folders)
    - Example: `android/docs/artifacts/database-benchmark-real-device-2026-02-23-5x5-aggregate/`
4. Update `android/docs/database-research-log.md` with conclusions and artifact links.

Notes:

- `runN_*` columns in CSV outputs are generated dynamically from `--runs`.
- If a run is missing or skipped, its corresponding `runN_*` cell stays empty; other run columns keep their original run index.
- `run_connected_benchmark_suite.py` in `--artifact-level minimal` parses only benchmark JSON files created or updated by the current run, so stale JSON from older runs is not re-attributed.

## Delta Sign Convention

- Delta is computed as `(variant - baseline) / baseline * 100`.
- Negative delta: variant is faster.
- Positive delta: variant is slower.
- In CSV outputs, use `*_variant_vs_baseline_pct` columns.

## Official References

- Microbenchmark overview: https://developer.android.com/topic/performance/benchmarking/microbenchmark-and-macrobenchmark
- Write microbenchmark tests: https://developer.android.com/topic/performance/benchmarking/microbenchmark-write
- Manual benchmark setup (`debuggable=false`): https://developer.android.com/topic/performance/benchmarking/microbenchmark-without-gradle-plugin
- SQLite performance best practices: https://developer.android.com/topic/performance/sqlite-performance-best-practices
