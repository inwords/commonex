# Database Research Log

This file tracks completed one-off Android database researches after benchmark code is cleaned up.

Entry template: `android/docs/database-research-log-template.md`

## 2026-02-20: Benchmark tooling policy update

- Scope: Define mandatory measurement tooling for future database performance researches.
- Hypothesis: Using a dedicated benchmarking harness reduces timing noise and improves result trustworthiness.
- Environment: Documentation policy update.
- Method: Session decision after checks of current Room/SQLite experiments.
- Variants: `androidx.benchmark` (primary) vs plain `androidDeviceTest` timing (auxiliary).
- Decision: For future researches, use `androidx.benchmark` for decision-grade timing. Use `androidDeviceTest` timing only as auxiliary and mark it provisional if used alone.
- Recommendation: Re-run high-impact future A/B decisions with `androidx.benchmark` harness even if a quick `androidDeviceTest` signal already exists.
- Artifacts: `android/docs/database-benchmarking.md`.

## 2026-02-20: Room pragmas A/B research

- Scope: Check baseline vs three pragmas (`cache_size`, `mmap_size`, `journal_size_limit`) using consistent methodology on isolated and mixed workloads.
- Hypothesis: The previous measured win should remain under isolated and paired measurement and also hold on mixed throughput.
- Environment: `androidDeviceTest` on `Pixel 9 Pro XL - 16`.
- Method: Paired Room A/B benchmarks with `shared.room.testing` + `MigrationTestHelper`:
- ABBA ordering per sample in both benchmarks.
- Variants:
- Variant A (baseline): no explicit `cache_size`, `mmap_size`, `journal_size_limit` pragmas.
- Variant B (three-pragmas): `cache_size=-8192`, `mmap_size=134217728`, `journal_size_limit=67108864`.
- Runs: 5 per workload type.
- Results (run-level):
- Run 1: isolated read `-1.13%` median, `-1.02%` avg; isolated write `+1.75%` median, `+1.06%` avg; mixed `+1.45%` median, `+0.14%` avg.
- Run 2: isolated read `+2.94%` median, `+4.66%` avg; isolated write `+0.63%` median, `+1.29%` avg; mixed `-4.59%` median, `-2.54%` avg.
- Run 3: isolated read `-0.83%` median, `+0.36%` avg; isolated write `+2.48%` median, `+2.04%` avg; mixed `-4.60%` median, `-2.28%` avg.
- Run 4: isolated read `+1.20%` median, `+1.94%` avg; isolated write `+3.55%` median, `+0.48%` avg; mixed `+0.82%` median, `+1.77%` avg.
- Run 5: isolated read `+0.73%` median, `+1.15%` avg; isolated write `+2.01%` median, `+4.56%` avg; mixed `-0.45%` median, `-0.93%` avg.
- Results (aggregate): isolated read `+0.58%` median and `+1.42%` avg; isolated write `+2.08%` median and `+1.89%` avg; mixed `-1.47%` median and `-0.77%` avg.
- Decision: Effect is workload-dependent; small positive on isolated read/write, neutral-to-slightly negative on mixed workload. Runtime decision: do not use these 3 pragmas.
- Recommendation: Keep runtime without these 3 pragmas unless future target-fleet measurements show clear, repeatable product-level gains.
- Artifacts: benchmark logs under tags `RoomPragmasIsoBenchmark` and `RoomPragmasMixedBenchmark`.
- Cleanup status: benchmark-specific implementations removed after research; reusable template retained.

## 2026-02-20: Transaction nesting write strategy

- Scope: Check `N` nested write transactions inside one top-level transaction vs `N` top-level write transactions.
- Hypothesis: One outer transaction with nested writes should still reduce overhead substantially.
- Environment: `androidDeviceTest` on `Pixel 9 Pro XL - 16`.
- Method: Isolated Room write benchmark (`TransactionNestingIsolatedBenchmarkTest`) with `shared.room.testing` + `MigrationTestHelper`; paired ABBA ordering per sample; `N = 80` per sample.
- Variants: `nested-in-top-level` vs `N-top-level`.
- Runs: 5.
- Results (run-level):
- Run 1: nested median `103.548ms`, avg `103.545ms`; top-level median `558.812ms`, avg `578.272ms`.
- Run 2: nested median `102.305ms`, avg `96.135ms`; top-level median `576.056ms`, avg `590.623ms`.
- Run 3: nested median `108.305ms`, avg `106.287ms`; top-level median `577.563ms`, avg `599.508ms`.
- Run 4: nested median `101.321ms`, avg `98.654ms`; top-level median `568.783ms`, avg `592.199ms`.
- Run 5: nested median `131.429ms`, avg `121.379ms`; top-level median `563.114ms`, avg `588.441ms`.
- Results (aggregate): median `109.382ms` (nested) vs `568.866ms` (top-level), `5.20x` faster; average `105.200ms` (nested) vs `589.809ms` (top-level), `5.61x` faster.
- Decision: Confirmed with current setup; nested writes inside one outer transaction are significantly faster for this workload.
- Recommendation: For many small writes, prefer one outer write transaction and group operations inside it; re-run on target devices/workload changes.
- Artifacts: benchmark logs under tag `RoomTxNestingIsoBenchmark`.
- Cleanup status: benchmark-specific implementation removed after research; reusable template retained.

## 2026-02-20: `synchronous=NORMAL` vs `synchronous=FULL`

- Scope: Compare `PRAGMA synchronous=NORMAL` vs `PRAGMA synchronous=FULL` with identical remaining pragmas on isolated and mixed workloads.
- Hypothesis: `NORMAL` should improve write throughput and remain faster when writes are part of workload mix.
- Environment: `androidDeviceTest` on `Pixel 9 Pro XL - 16`.
- Method: Paired Room A/B benchmarks with `shared.room.testing` + `MigrationTestHelper`:
- `SynchronousModeIsolatedBenchmarkTest` for isolated read and write (separate DB pairs).
- `SynchronousModeMixedBenchmarkTest` for mixed read-write chunks.
- ABBA ordering per sample in both benchmarks.
- Variants:
- Variant A (`NORMAL`): `cache_size=-8192`, `mmap_size=134217728`, `journal_size_limit=67108864`, `synchronous=NORMAL`.
- Variant B (`FULL`): `cache_size=-8192`, `mmap_size=134217728`, `journal_size_limit=67108864`, `synchronous=FULL`.
- Runs: 5 per workload type.
- Results (run-level):
- Run 1: isolated read `-0.84%` median, `+0.45%` avg; isolated write `+16.83%` median, `+12.24%` avg; mixed `+15.68%` median, `+18.26%` avg.
- Run 2: isolated read `-4.45%` median, `-0.86%` avg; isolated write `+22.64%` median, `+23.72%` avg; mixed `+19.88%` median, `+20.01%` avg.
- Run 3: isolated read `+1.76%` median, `-2.00%` avg; isolated write `+20.26%` median, `+18.65%` avg; mixed `+19.43%` median, `+19.80%` avg.
- Run 4: isolated read `+0.20%` median, `-2.42%` avg; isolated write `+18.20%` median, `+18.58%` avg; mixed `+18.92%` median, `+17.90%` avg.
- Run 5: isolated read `-1.13%` median, `-1.66%` avg; isolated write `+22.58%` median, `+17.98%` avg; mixed `+20.15%` median, `+19.88%` avg.
- Results (aggregate): isolated read `-0.89%` median and `-1.30%` avg; isolated write `+20.10%` median and `+18.23%` avg; mixed `+18.81%` median and `+19.17%` avg.
- Decision: `NORMAL` is decisively faster for write and mixed workloads; isolated read impact is small and slightly negative on average.
- Recommendation: Prefer `synchronous=NORMAL` for write-heavy or mixed profiles unless product requirements demand `FULL` durability semantics.
- Artifacts: benchmark logs under tags `RoomSyncIsoBenchmark` and `RoomSyncMixedBenchmark`.
- Cleanup status: benchmark-specific implementations removed after research; reusable template retained.
