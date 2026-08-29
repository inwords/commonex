# Cronet Response Benchmarking

## Purpose

`android/benchmarks/network` compares the custom Ktor Cronet engine's bounded streaming response path with a frozen copy of the pre-hardening full-response buffering path.

The benchmark uses the same Cronet 500 runtime, Ktor request execution, loopback server, and complete body consumption for both variants. The frozen baseline retains the historical 100 KiB response buffer. The optimized streaming engine uses `Content-Length` to right-size smaller unencoded responses and currently caps encoded, unknown, or larger responses at 64 KiB.

## Workloads

- 32 KiB: typical API response.
- 256 KiB: large sync response.
- 1 MiB: realistic upper-bound response.

The server runs inside the benchmark process on `127.0.0.1`, returns deterministic bytes, and closes each connection. This removes public-network latency and payload variability while exercising the real Cronet transport and Ktor engine boundary.

AndroidX `BenchmarkRule` reports elapsed time and allocation count. Allocation count measures allocated objects, not allocated bytes or peak heap usage.

## Commands

Run from `android/`.

Compile the benchmark:

```powershell
.\gradlew --quiet :benchmarks:network:compileReleaseAndroidTestKotlin
```

Validate once on the managed device without collecting measurements:

```powershell
.\gradlew --quiet :benchmarks:network:pixel6Api35AtdReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true"
```

Collect provisional managed-emulator measurements:

```powershell
.\gradlew --quiet --rerun-tasks :benchmarks:network:pixel6Api35AtdReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,UNLOCKED" "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.profiling.mode=None"
```

Collect decision-grade measurements on a connected physical device without suppressions:

```powershell
.\gradlew --quiet :benchmarks:network:connectedReleaseAndroidTest
```

## Historical Provisional Results: 2026-08-28

Environment: Pixel 6 API 35 AOSP ATD managed emulator, x86_64, two virtual CPU cores, unlocked clocks. Values are medians of three fresh-process benchmark medians. Each process used AndroidX's default warmup and 50 timing measurements. These A/B results used the former 256 KiB streaming cap and are retained as historical evidence rather than measurements of the current 64 KiB default.

| Response | Buffered | Streaming | Streaming time delta | Buffered throughput | Streaming throughput | Allocation delta |
|---|---:|---:|---:|---:|---:|---:|
| 32 KiB | 8.30 ms | 7.62 ms | -8.2% | 3.8 MiB/s | 4.1 MiB/s | +11.9% |
| 256 KiB | 16.55 ms | 12.20 ms | -26.3% | 15.1 MiB/s | 20.5 MiB/s | +10.7% |
| 1 MiB | 40.16 ms | 23.83 ms | -40.7% | 24.9 MiB/s | 42.0 MiB/s | +6.9% |

Negative time delta means the streaming path was faster. The 32 KiB result still changed direction between individual emulator runs and should be treated as no demonstrated timing difference. The 256 KiB and 1 MiB runs consistently favored streaming. Streaming still allocated more objects, but its relative allocation overhead decreased as the response grew.

### Upload-Dispatcher Change Sanity Rerun

After `WriteChannelContent` upload conversion was changed to inherit the configured Ktor engine dispatcher, the representative response A/B suite was repeated across three fresh managed-device processes. These benchmarks use GET requests, so they do not execute the changed upload path; this rerun is a response-path regression check rather than a measurement of the dispatcher change itself.

| Response | Buffered | Streaming | Streaming time delta | Allocation delta |
|---|---:|---:|---:|---:|
| 32 KiB | 7.57 ms | 6.94 ms | -8.4% | +12.6% |
| 256 KiB | 15.53 ms | 11.64 ms | -25.0% | +11.0% |
| 1 MiB | 38.56 ms | 22.25 ms | -42.3% | +7.2% |

The relative results remain close to the earlier provisional run, with no response-path regression signal. Absolute emulator timings should not be compared as decision-grade measurements.

### Historical Buffer-Cap Sweep

The buffer-size tradeoff was measured in a separate three-process sweep on the same environment. All variants used the optimized dispatcher path.

| Response | Buffer | Median time | Median allocations |
|---|---:|---:|---:|
| 256 KiB | 64 KiB | 12.60 ms | 1024.4 |
| 256 KiB | 100 KiB | 12.49 ms | 1006.5 |
| 256 KiB | 128 KiB | 13.13 ms | 1000.8 |
| 256 KiB | 256 KiB | 12.51 ms | 981.3 |
| 1 MiB | 64 KiB | 28.85 ms | 1483.5 |
| 1 MiB | 100 KiB | 26.34 ms | 1369.5 |
| 1 MiB | 128 KiB | 25.43 ms | 1318.2 |
| 1 MiB | 256 KiB | 23.68 ms | 1235.2 |

At 256 KiB, the 64 KiB and 256 KiB timing medians were effectively tied, while the 256 KiB buffer allocated fewer objects. At 1 MiB, the 256 KiB buffer was both fastest and lowest-allocation among the tested sizes. The production default was subsequently changed to 64 KiB to reduce direct memory per active encoded response by 75%. This accepts the measured large-response throughput tradeoff because the application's responses are normally much smaller, while `Content-Length` still right-sizes smaller unencoded responses.

These emulator results establish provisional direction only. Do not use their absolute timings or small-response delta for a release decision; repeat on a physical device first.
