# Database Research Log Entry Template

Use this template for each new entry in `android/docs/database-research-log.md`.

```markdown
## YYYY-MM-DD: <Research title>

- Scope: <what is tested>
- Environment: <device model, Android version, serial if needed>
- Module/runner: `:benchmarks:databases`, `AndroidBenchmarkRunner`, non-debuggable release test build type
- Execution: <full-suite or class subset, number of process-level runs>
- Scenario classes: <class list>
- Correctness checks: <one-variable diff, deterministic seed, PRAGMA asserts, one-time seed/checkpoint before measure loop, bounded writes (no cardinality drift), one post-loop assertion on last measured result, cleanup, process isolation>
- Primary metrics source: <path to `combined-summary-timeNs.csv` and benchmark JSON if retained in full mode>
- Secondary metadata source: <optional path to per-run metadata if retained in full mode>
- Aggregated artifacts:
  - `combined-summary-timeNs.csv`
  - `readable-ab-deltas.csv`
  - `aggregate-<N>runs-ab-deltas.csv` (when multiple batches are combined)
  - `aggregate-<N>runs-absolute-medians.csv` (when multiple batches are combined)
  - `README.md`
- Results (worthy conclusions only):
  - <aspect>: baseline `<label>` `<ms>` vs variant `<label>` `<ms>`, delta median `<%>`, delta avg `<%>`
- Decision: <what to adopt/reject>
- Caveats: <noise/provisional constraints if any>
- Artifacts folder: `android/docs/artifacts/<research-id>/`
```

Delta sign convention:

- `delta = (variant - baseline) / baseline * 100`
- negative => variant faster
- positive => variant slower
