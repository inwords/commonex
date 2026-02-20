# Database Research Log

Entry template: `android/docs/database-research-log-template.md`

## 2026-02-22: Real-device full-suite DB benchmark (3x5 runs, historical)

Status:

- Superseded by the 2026-02-23 5x5 dataset below; original 2026-02-22 artifact folder was removed during artifact reset.

- Scope: Room/SQLite comparison for three aspects: extra pragmas, transaction nesting, `synchronous=FULL` vs `synchronous=NORMAL`.
- Environment: Pixel 9 Pro XL (serial `47251FDAS004QB`), connected physical device.
- Module/runner: `:benchmarks:databases`, `AndroidBenchmarkRunner`, non-debuggable release test build type.
- Execution: three independent full-suite batches, 5 process-level runs per batch (15 runs total per aspect).
- Scenario classes:
    - `DbPragmasReadBench`, `DbPragmasWriteBench`, `DbPragmasMixedBench`
    - `DbSynchronousReadBench`, `DbSynchronousWriteBench`, `DbSynchronousMixedBench`
    - `DbTransactionNestingBench`
- Primary metrics source: `android/docs/artifacts/database-benchmark-real-device-2026-02-22-3x5-aggregate/aggregate-15runs-absolute-medians.csv`.
- Secondary metrics source: `android/docs/artifacts/database-benchmark-real-device-2026-02-22-3x5-aggregate/aggregate-15runs-ab-deltas.csv`.
- Artifacts folder: `android/docs/artifacts/database-benchmark-real-device-2026-02-22-3x5-aggregate/`.

Results (15-run aggregate):

- `PragmasRead`: median delta `+0.900431%`, avg `+1.130871%` (extra pragmas slower).
- `PragmasWrite`: median delta `-1.274124%`, avg `-1.131864%` (extra pragmas faster).
- `PragmasMixed`: median delta `+2.726467%`, avg `+4.259385%` (extra pragmas slower).
- `Transactions`: median delta `-95.950000%`, avg `-95.845646%` (nested much faster).
    - absolute medians: top-level `40.033509ms`, nested `1.637585ms` (~24.45x speedup).
- `SyncRead` (baseline=`NORMAL`, variant=`FULL`): median delta `-0.448912%`, avg `-2.085837%` (`FULL` slightly faster).
- `SyncWrite` (baseline=`NORMAL`, variant=`FULL`): median delta `-1.180300%`, avg `-0.220493%` (`FULL` slightly faster / near parity).
- `SyncMixed` (baseline=`NORMAL`, variant=`FULL`): median delta `-8.138400%`, avg `-6.324806%` (`FULL` faster).

Decision:

- Adopt nested transaction strategy for batched writes.
- Do not adopt extra pragmas as a universal default; effect is workload-dependent and small outside nesting.
- For these scenarios on this device, `synchronous=NORMAL` is not a universal win.

Caveats:

- Single-device dataset; confirm on additional target devices before broad runtime policy changes.

Delta sign convention:

- `delta = (variant - baseline) / baseline * 100`
- negative => variant faster
- positive => variant slower

## 2026-02-23: Real-device full-suite DB benchmark (5x5 runs, current)

- Scope: Room/SQLite comparison for three aspects: extra pragmas, transaction nesting, `synchronous=FULL` vs `synchronous=NORMAL`.
- Environment: Pixel 9 Pro XL (SDK 36), connected physical device.
- Module/runner: `:benchmarks:databases`, `AndroidBenchmarkRunner`, non-debuggable release test build type.
- Execution: five independent full-suite batches, 5 process-level runs per batch (25 runs total per aspect).
- Scenario classes:
    - `DbPragmasReadBench`, `DbPragmasWriteBench`, `DbPragmasMixedBench`
    - `DbSynchronousReadBench`, `DbSynchronousWriteBench`, `DbSynchronousMixedBench`
    - `DbTransactionNestingBench`
- Primary metrics source: `android/docs/artifacts/database-benchmark-real-device-2026-02-23-5x5-aggregate/aggregate-25runs-absolute-medians.csv`.
- Secondary metrics source: `android/docs/artifacts/database-benchmark-real-device-2026-02-23-5x5-aggregate/aggregate-25runs-ab-deltas.csv`.
- Artifacts folder: `android/docs/artifacts/database-benchmark-real-device-2026-02-23-5x5-aggregate/`.

Results (25-run aggregate):

- `PragmasRead`: median delta `+1.933357%`, avg `+2.331810%` (extra pragmas slower).
- `PragmasWrite`: median delta `+1.450589%`, avg `+2.069928%` (extra pragmas slower in aggregate; high run-to-run spread).
- `PragmasMixed`: median delta `+2.330356%`, avg `+2.361804%` (extra pragmas slower).
- `Transactions`: median delta `-95.906058%`, avg `-95.919545%` (nested much faster).
    - absolute medians: top-level `41.100454ms`, nested `1.653631ms` (~24.85x speedup).
- `SyncRead` (baseline=`NORMAL`, variant=`FULL`): median delta `-0.763709%`, avg `-0.656473%` (`FULL` slightly faster / near parity).
- `SyncWrite` (baseline=`NORMAL`, variant=`FULL`): median delta `+0.563784%`, avg `+0.385208%` (`FULL` slightly slower / near parity).
- `SyncMixed` (baseline=`NORMAL`, variant=`FULL`): median delta `-1.655841%`, avg `-3.587040%` (`FULL` faster).

Decision:

- Keep nested transaction strategy for batched writes.
- Do not adopt extra pragmas as a universal default; results are consistently slower in read/mixed and slower in aggregate across write.
- Keep `synchronous` mode decision workload-aware; differences are small for read/write and moderate for mixed.

Caveats:

- Single-device dataset; confirm on additional target devices before broad runtime policy changes.

Delta sign convention:

- `delta = (variant - baseline) / baseline * 100`
- negative => variant faster
- positive => variant slower
