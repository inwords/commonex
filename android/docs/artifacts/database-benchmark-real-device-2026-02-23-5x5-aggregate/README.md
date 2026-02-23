# Database benchmark real-device aggregate (2026-02-23, 5x5 runs)

Scope:

- Room/SQLite comparison for pragmas, transaction nesting, and synchronous mode.
- Device: Pixel 9 Pro XL (SDK 36), connected physical device.
- Execution: five independent full-suite batches, 5 process-level runs each (25 runs total per aspect).

Referenced source batch artifact folders (not copied here):

- `android/docs/artifacts/database-benchmark-real-device-2026-02-23-153952-5runs-clean-state/`
- `android/docs/artifacts/database-benchmark-real-device-2026-02-23-184436-5runs-rerun/`
- `android/docs/artifacts/database-benchmark-real-device-2026-02-23-190819-5runs-rerun/`
- `android/docs/artifacts/database-benchmark-real-device-2026-02-23-201258-5runs-rerun/`
- `android/docs/artifacts/database-benchmark-real-device-2026-02-23-204020-5runs-rerun/`

Aggregate files:

- aggregate-25runs-ab-deltas.csv
- aggregate-25runs-absolute-medians.csv

Delta convention:

- delta = (variant - baseline) / baseline * 100
- negative => variant faster
- positive => variant slower
