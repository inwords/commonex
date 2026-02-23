#!/usr/bin/env python3
"""Run Android DB benchmark suite multiple times and aggregate benchmark JSON outputs.

This script is cross-platform and intended to replace ad-hoc PowerShell parsing.
It runs:
    :benchmarks:databases:connectedReleaseAndroidTest
for N process-level repetitions, stores per-run logs/artifacts, and generates:
    - runN/summary-timeNs.csv
    - combined-summary-timeNs.csv
    - readable-per-test-runs.csv
    - readable-ab-deltas.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable


DEFAULT_TASK = ":benchmarks:databases:connectedReleaseAndroidTest"
DEFAULT_OUTPUT_PREFIX = "db-benchmark-connectedRelease"
BENCHMARK_JSON_GLOB = "*benchmarkData.json"
ARTIFACT_LEVEL_MINIMAL = "minimal"
ARTIFACT_LEVEL_FULL = "full"


@dataclass(frozen=True)
class AbPair:
    aspect: str
    baseline_key: str
    variant_key: str


@dataclass(frozen=True)
class BenchmarkRow:
    run: int
    device: str
    sdk: int
    sustained_performance_mode_enabled: bool
    class_name: str
    test_name: str
    warmup_iterations: int
    repeat_iterations: int
    time_ns_min: float
    time_ns_median: float
    time_ns_max: float
    time_ns_cv: float

    @property
    def key(self) -> str:
        return f"{self.class_name.rsplit('.', 1)[-1]}.{self.test_name}"

    @property
    def time_ms_median(self) -> float:
        return self.time_ns_median / 1_000_000.0


AB_PAIRS: tuple[AbPair, ...] = (
    AbPair(
        aspect="PragmasRead",
        baseline_key="DbPragmasReadBench.measureBaselineReadNoExtraPragmas",
        variant_key="DbPragmasReadBench.measureVariantReadWithExtraPragmas",
    ),
    AbPair(
        aspect="PragmasWrite",
        baseline_key="DbPragmasWriteBench.measureBaselineWriteNoExtraPragmas",
        variant_key="DbPragmasWriteBench.measureVariantWriteWithExtraPragmas",
    ),
    AbPair(
        aspect="PragmasMixed",
        baseline_key="DbPragmasMixedBench.measureBaselineMixedNoExtraPragmas",
        variant_key="DbPragmasMixedBench.measureVariantMixedWithExtraPragmas",
    ),
    AbPair(
        aspect="SyncRead",
        baseline_key="DbSynchronousReadBench.measureSynchronousNormalReadAbsolute",
        variant_key="DbSynchronousReadBench.measureSynchronousFullReadAbsolute",
    ),
    AbPair(
        aspect="SyncWrite",
        baseline_key="DbSynchronousWriteBench.measureSynchronousNormalWriteAbsolute",
        variant_key="DbSynchronousWriteBench.measureSynchronousFullWriteAbsolute",
    ),
    AbPair(
        aspect="SyncMixed",
        baseline_key="DbSynchronousMixedBench.measureSynchronousNormalMixedAbsolute",
        variant_key="DbSynchronousMixedBench.measureSynchronousFullMixedAbsolute",
    ),
    AbPair(
        aspect="Transactions",
        baseline_key="DbTransactionNestingBench.measureTopLevelTransactionsAbsolute",
        variant_key="DbTransactionNestingBench.measureNestedTransactionsAbsolute",
    ),
)


def median(values: list[float]) -> float:
    if not values:
        return math.nan
    sorted_values = sorted(values)
    mid = len(sorted_values) // 2
    if len(sorted_values) % 2 == 1:
        return sorted_values[mid]
    return (sorted_values[mid - 1] + sorted_values[mid]) / 2.0


def format_float_dot(value: float) -> str:
    if math.isnan(value):
        return ""
    # Explicit locale-independent float formatting with dot decimal separator.
    return format(value, ".17g")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run connected DB benchmark suite repeatedly and aggregate results.",
    )
    parser.add_argument(
        "--android-dir",
        type=Path,
        default=Path("android"),
        help="Path to android/ directory containing Gradle wrapper.",
    )
    parser.add_argument(
        "--runs",
        type=int,
        default=5,
        help="Number of process-level benchmark runs.",
    )
    parser.add_argument(
        "--task",
        default=DEFAULT_TASK,
        help="Gradle task to execute on each run.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Output directory. Defaults to android/build/<prefix>-r<runs>-<timestamp>.",
    )
    parser.add_argument(
        "--gradle-arg",
        action="append",
        default=[],
        help="Additional argument forwarded to Gradle (can be repeated).",
    )
    parser.add_argument(
        "--stop-on-failure",
        action="store_true",
        help="Stop immediately when a run fails.",
    )
    parser.add_argument(
        "--artifact-level",
        choices=[ARTIFACT_LEVEL_MINIMAL, ARTIFACT_LEVEL_FULL],
        default=ARTIFACT_LEVEL_MINIMAL,
        help=(
            "Artifact retention level. "
            "'minimal' keeps only final aggregate CSVs; "
            "'full' keeps per-run logs, copied JSONs, and per-run summaries."
        ),
    )
    return parser.parse_args()


def resolve_gradle_wrapper(android_dir: Path) -> Path:
    wrapper_name = "gradlew.bat" if sys.platform.startswith("win") else "gradlew"
    wrapper = android_dir / wrapper_name
    if not wrapper.exists():
        raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def benchmark_output_root(android_dir: Path) -> Path:
    return (
        android_dir
        / "benchmarks"
        / "databases"
        / "build"
        / "outputs"
        / "connected_android_test_additional_output"
        / "releaseAndroidTest"
        / "connected"
    )


def run_gradle(
    wrapper: Path,
    android_dir: Path,
    task: str,
    extra_args: list[str],
    log_path: Path,
) -> int:
    cmd = [str(wrapper), task, "--console=plain", *extra_args]
    with log_path.open("w", encoding="utf-8", newline="\n") as log_file:
        process = subprocess.Popen(
            cmd,
            cwd=android_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            log_file.write(line)
            log_file.flush()
        return process.wait()


def copy_benchmark_jsons(
    json_sources: Iterable[Path],
    source_root: Path,
    run_dir: Path,
) -> list[Path]:
    copied: list[Path] = []
    for json_path in json_sources:
        rel_parent = json_path.parent.relative_to(source_root)
        target_parent = run_dir / rel_parent
        target_parent.mkdir(parents=True, exist_ok=True)
        target_path = target_parent / json_path.name
        shutil.copy2(json_path, target_path)
        copied.append(target_path)
    return copied


def snapshot_benchmark_json_state(source_root: Path) -> dict[Path, tuple[int, int]]:
    if not source_root.exists():
        return {}

    snapshot: dict[Path, tuple[int, int]] = {}
    for json_path in source_root.rglob(BENCHMARK_JSON_GLOB):
        stat = json_path.stat()
        snapshot[json_path] = (stat.st_size, stat.st_mtime_ns)
    return snapshot


def collect_updated_benchmark_jsons(
    source_root: Path,
    pre_run_snapshot: dict[Path, tuple[int, int]],
) -> list[Path]:
    if not source_root.exists():
        return []

    updated: list[Path] = []
    for json_path in source_root.rglob(BENCHMARK_JSON_GLOB):
        stat = json_path.stat()
        current_signature = (stat.st_size, stat.st_mtime_ns)
        if pre_run_snapshot.get(json_path) != current_signature:
            updated.append(json_path)
    return sorted(updated)


def parse_benchmark_json(json_path: Path, run: int) -> list[BenchmarkRow]:
    data = json.loads(json_path.read_text(encoding="utf-8"))
    context = data.get("context", {})
    build = context.get("build", {})
    version = build.get("version", {})

    device = str(build.get("model", "unknown"))
    sdk = int(version.get("sdk", -1))
    sustained = bool(context.get("sustainedPerformanceModeEnabled", False))

    rows: list[BenchmarkRow] = []
    for bench in data.get("benchmarks", []):
        metrics = bench.get("metrics", {})
        time_ns = metrics.get("timeNs", {})

        rows.append(
            BenchmarkRow(
                run=run,
                device=device,
                sdk=sdk,
                sustained_performance_mode_enabled=sustained,
                class_name=str(bench.get("className", "")),
                test_name=str(bench.get("name", "")),
                warmup_iterations=int(bench.get("warmupIterations", 0)),
                repeat_iterations=int(bench.get("repeatIterations", 0)),
                time_ns_min=float(time_ns.get("minimum", math.nan)),
                time_ns_median=float(time_ns.get("median", math.nan)),
                time_ns_max=float(time_ns.get("maximum", math.nan)),
                time_ns_cv=float(time_ns.get("coefficientOfVariation", math.nan)),
            )
        )
    return rows


def write_run_summary(rows: list[BenchmarkRow], output_csv: Path) -> None:
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(
            [
                "run",
                "device",
                "sdk",
                "sustainedPerformanceModeEnabled",
                "className",
                "testName",
                "warmupIterations",
                "repeatIterations",
                "timeNs_min",
                "timeNs_median",
                "timeNs_max",
                "timeNs_cv",
            ]
        )
        for row in rows:
            writer.writerow(
                [
                    row.run,
                    row.device,
                    row.sdk,
                    str(row.sustained_performance_mode_enabled),
                    row.class_name,
                    row.test_name,
                    row.warmup_iterations,
                    row.repeat_iterations,
                    format_float_dot(row.time_ns_min),
                    format_float_dot(row.time_ns_median),
                    format_float_dot(row.time_ns_max),
                    format_float_dot(row.time_ns_cv),
                ]
            )


def write_combined_summary(rows: Iterable[BenchmarkRow], output_csv: Path) -> None:
    write_run_summary(list(rows), output_csv)


def write_readable_per_test(
    rows: list[BenchmarkRow],
    output_csv: Path,
    run_count: int,
) -> None:
    if run_count <= 0:
        raise ValueError("run_count must be > 0")

    grouped: dict[str, dict[int, float]] = {}
    for row in rows:
        grouped.setdefault(row.key, {})[row.run] = row.time_ms_median

    with output_csv.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(
            [
                "test",
                *[f"run{run}_ms" for run in range(1, run_count + 1)],
                "avg_ms",
                "range_pct",
            ]
        )
        for test in sorted(grouped):
            run_to_ms = grouped[test]
            values = [run_to_ms.get(run, math.nan) for run in range(1, run_count + 1)]
            finite_values = [v for v in values if not math.isnan(v)]
            avg_ms = sum(finite_values) / len(finite_values) if finite_values else math.nan
            min_ms = min(finite_values) if finite_values else math.nan
            max_ms = max(finite_values) if finite_values else math.nan
            range_pct = (
                ((max_ms - min_ms) / avg_ms * 100.0)
                if finite_values and avg_ms and not math.isnan(avg_ms)
                else math.nan
            )
            writer.writerow(
                [
                    test,
                    *[
                        format_float_dot(round(v, 6)) if not math.isnan(v) else ""
                        for v in values
                    ],
                    format_float_dot(round(avg_ms, 6)) if not math.isnan(avg_ms) else "",
                    format_float_dot(round(range_pct, 6))
                    if not math.isnan(range_pct)
                    else "",
                ]
            )


def write_ab_deltas(
    rows: list[BenchmarkRow],
    output_csv: Path,
    run_count: int,
) -> None:
    if run_count <= 0:
        raise ValueError("run_count must be > 0")

    row_index: dict[tuple[int, str], BenchmarkRow] = {
        (row.run, row.key): row for row in rows
    }
    with output_csv.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(
            [
                "aspect",
                *[
                    f"run{run}_variant_vs_baseline_pct"
                    for run in range(1, run_count + 1)
                ],
                "median_variant_vs_baseline_pct",
                "avg_variant_vs_baseline_pct",
            ]
        )
        for pair in AB_PAIRS:
            deltas_by_run: dict[int, float] = {}
            for run in range(1, run_count + 1):
                baseline = row_index.get((run, pair.baseline_key))
                variant = row_index.get((run, pair.variant_key))
                if baseline is None or variant is None:
                    continue
                if baseline.time_ms_median == 0:
                    continue
                # Sign convention: negative means variant is faster.
                delta_pct = (
                    (variant.time_ms_median - baseline.time_ms_median)
                    / baseline.time_ms_median
                    * 100.0
                )
                deltas_by_run[run] = delta_pct

            deltas = list(deltas_by_run.values())
            median_delta = median(deltas)
            avg_delta = sum(deltas) / len(deltas) if deltas else math.nan
            run_columns = [
                (
                    format_float_dot(round(deltas_by_run[run], 6))
                    if run in deltas_by_run
                    else ""
                )
                for run in range(1, run_count + 1)
            ]
            writer.writerow(
                [
                    pair.aspect,
                    *run_columns,
                    format_float_dot(round(median_delta, 6))
                    if not math.isnan(median_delta)
                    else "",
                    format_float_dot(round(avg_delta, 6))
                    if not math.isnan(avg_delta)
                    else "",
                ]
            )


def main() -> int:
    args = parse_args()
    if args.runs <= 0:
        raise ValueError("--runs must be > 0")

    android_dir = args.android_dir.resolve()
    wrapper = resolve_gradle_wrapper(android_dir)

    if args.output_dir is None:
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        args.output_dir = (
            android_dir
            / "build"
            / f"{DEFAULT_OUTPUT_PREFIX}-r{args.runs}-{timestamp}"
        )

    out_dir = args.output_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    source_root = benchmark_output_root(android_dir)
    all_rows: list[BenchmarkRow] = []

    for run in range(1, args.runs + 1):
        print(f"===== RUN {run}/{args.runs} START =====")
        pre_run_snapshot = snapshot_benchmark_json_state(source_root)
        run_dir = out_dir / f"run{run}"
        if args.artifact_level == ARTIFACT_LEVEL_FULL:
            run_dir.mkdir(parents=True, exist_ok=True)
            log_path = run_dir / "gradle-output.txt"
        else:
            log_path = out_dir / f"_tmp-run{run}-gradle-output.txt"
        exit_code = run_gradle(
            wrapper=wrapper,
            android_dir=android_dir,
            task=args.task,
            extra_args=args.gradle_arg,
            log_path=log_path,
        )

        if exit_code != 0:
            print(f"RUN {run} FAILED (exit={exit_code})")
            failed_log = out_dir / f"run{run}-FAILED-gradle-output.txt"
            if log_path.exists():
                log_path.replace(failed_log)
            if args.stop_on_failure:
                print("Stopping because --stop-on-failure is set.")
                break
            continue

        json_sources = collect_updated_benchmark_jsons(source_root, pre_run_snapshot)
        if args.artifact_level == ARTIFACT_LEVEL_FULL:
            json_sources = copy_benchmark_jsons(json_sources, source_root, run_dir)

        if not json_sources:
            print(f"RUN {run}: no benchmark JSON artifacts found.")
            if args.stop_on_failure:
                break
            continue

        run_rows: list[BenchmarkRow] = []
        for json_path in json_sources:
            run_rows.extend(parse_benchmark_json(json_path, run))

        if not run_rows:
            print(f"RUN {run}: benchmark JSON parsed but contains no benchmark rows.")
            if args.stop_on_failure:
                break
            continue

        if args.artifact_level == ARTIFACT_LEVEL_FULL:
            write_run_summary(run_rows, run_dir / "summary-timeNs.csv")
        elif log_path.exists():
            log_path.unlink()
        all_rows.extend(run_rows)
        print(f"===== RUN {run}/{args.runs} DONE: {len(run_rows)} benchmark rows =====")

    if not all_rows:
        print("No benchmark rows collected.")
        return 1

    combined_csv = out_dir / "combined-summary-timeNs.csv"
    ab_csv = out_dir / "readable-ab-deltas.csv"
    per_test_csv = out_dir / "readable-per-test-runs.csv"

    write_combined_summary(all_rows, combined_csv)
    write_ab_deltas(all_rows, ab_csv, run_count=args.runs)
    if args.artifact_level == ARTIFACT_LEVEL_FULL:
        write_readable_per_test(all_rows, per_test_csv, run_count=args.runs)

    print(f"OUT_DIR={out_dir}")
    print(f"ARTIFACT_LEVEL={args.artifact_level}")
    print(f"COMBINED={combined_csv}")
    print(f"AB_DELTAS={ab_csv}")
    if args.artifact_level == ARTIFACT_LEVEL_FULL:
        print(f"PER_TEST={per_test_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
