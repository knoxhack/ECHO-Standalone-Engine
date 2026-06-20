#!/usr/bin/env python3
"""Collect Phase 6 Engine-vs-legacy Standalone Runtime comparison evidence."""

from __future__ import annotations

import argparse
import ctypes
import json
import os
import platform
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ENGINE_JAR = "echo-standalone-engine-2.0.0-beta.2.jar"
DEFAULT_LEGACY_TASKS = (
    "runStandaloneContentGraphLoadSmoke",
    "runStandaloneSaveRuntimeSmoke",
    "runStandaloneAlphaReadinessSmoke",
)
DEFAULT_LEGACY_GRADLE_ARGS = ("--console", "plain")
DEFAULT_LEGACY_CLIENT_LAUNCH_TASKS = (
    "runStandaloneClientRuntimeAssemblySmoke",
)
LEGACY_REPORTS = {
    "contentGraph": Path("reports/echo/standalone/content-graph-load.json"),
    "saveRuntime": Path("reports/echo/standalone/runtime-save.json"),
    "alphaReadiness": Path("reports/echo/standalone/alpha-readiness-gate.json"),
    "agent6AshfallParity": Path("reports/echo/standalone/agent6-ashfall-executable-parity.json"),
    "adapterCoreGameplayBridge": Path("reports/echo/standalone/adaptercore-gameplay-bridge-parity-smoke.json"),
}


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def git_snapshot(repo: Path) -> dict[str, Any]:
    def run_git(*args: str) -> str | None:
        try:
            result = subprocess.run(
                ["git", *args],
                cwd=repo,
                check=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            return result.stdout.strip()
        except (OSError, subprocess.CalledProcessError):
            return None

    status = run_git("status", "--short")
    return {
        "path": str(repo),
        "head": run_git("rev-parse", "--short=12", "HEAD"),
        "branch": run_git("branch", "--show-current"),
        "dirtyCount": len([line for line in (status or "").splitlines() if line.strip()]),
    }


def process_rss_bytes(pid: int) -> int | None:
    try:
        import psutil  # type: ignore

        root = psutil.Process(pid)
        total = root.memory_info().rss
        for child in root.children(recursive=True):
            try:
                total += child.memory_info().rss
            except psutil.Error:
                continue
        return int(total)
    except Exception:
        pass
    if os.name == "nt":
        return windows_process_rss_bytes(pid)
    return posix_process_rss_bytes(pid)


def windows_process_rss_bytes(pid: int) -> int | None:
    try:
        from ctypes import wintypes

        class ProcessMemoryCounters(ctypes.Structure):
            _fields_ = [
                ("cb", wintypes.DWORD),
                ("PageFaultCount", wintypes.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]

        process_query_information = 0x0400
        process_vm_read = 0x0010
        handle = ctypes.windll.kernel32.OpenProcess(process_query_information | process_vm_read, False, pid)
        if not handle:
            return None
        try:
            counters = ProcessMemoryCounters()
            counters.cb = ctypes.sizeof(counters)
            ok = ctypes.windll.psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb)
            if not ok:
                return None
            return int(counters.WorkingSetSize)
        finally:
            ctypes.windll.kernel32.CloseHandle(handle)
    except Exception:
        return None


def posix_process_rss_bytes(pid: int) -> int | None:
    try:
        result = subprocess.run(
            ["ps", "-o", "rss=", "-p", str(pid)],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        raw = result.stdout.strip()
        if not raw:
            return None
        return int(raw.splitlines()[0].strip()) * 1024
    except (OSError, subprocess.CalledProcessError, ValueError):
        return None


def tail(text: str, limit: int = 4000) -> str:
    if len(text) <= limit:
        return text
    return text[-limit:]


def run_measured(label: str, command: list[str], cwd: Path, timeout_seconds: int) -> dict[str, Any]:
    started = time.perf_counter()
    with tempfile.TemporaryDirectory(prefix="echo-phase6-") as temp_dir:
        stdout_path = Path(temp_dir) / "stdout.txt"
        stderr_path = Path(temp_dir) / "stderr.txt"
        with stdout_path.open("w", encoding="utf-8", errors="replace") as stdout_file, stderr_path.open(
            "w", encoding="utf-8", errors="replace"
        ) as stderr_file:
            process = subprocess.Popen(
                [str(part) for part in command],
                cwd=cwd,
                stdout=stdout_file,
                stderr=stderr_file,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            peak_rss = None
            timed_out = False
            while process.poll() is None:
                rss = process_rss_bytes(process.pid)
                if rss is not None:
                    peak_rss = max(peak_rss or 0, rss)
                if time.perf_counter() - started > timeout_seconds:
                    timed_out = True
                    process.kill()
                    break
                time.sleep(0.05)
            process.wait()
            elapsed_ms = round((time.perf_counter() - started) * 1000, 3)
            rss = process_rss_bytes(process.pid)
            if rss is not None:
                peak_rss = max(peak_rss or 0, rss)
        stdout = stdout_path.read_text(encoding="utf-8", errors="replace")
        stderr = stderr_path.read_text(encoding="utf-8", errors="replace")
    return {
        "label": label,
        "command": [str(part) for part in command],
        "cwd": str(cwd),
        "exitCode": process.returncode,
        "timedOut": timed_out,
        "wallMs": elapsed_ms,
        "peakRssBytes": peak_rss,
        "stdoutTail": tail(stdout),
        "stderrTail": tail(stderr),
    }


def percentile(values: list[float], percent: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * percent
    lower = int(index)
    upper = min(lower + 1, len(ordered) - 1)
    if lower == upper:
        return round(ordered[lower], 3)
    weight = index - lower
    return round(ordered[lower] * (1 - weight) + ordered[upper] * weight, 3)


def summarize_runs(runs: list[dict[str, Any]]) -> dict[str, Any]:
    wall = [float(run["wallMs"]) for run in runs if run.get("wallMs") is not None]
    memory = [int(run["peakRssBytes"]) for run in runs if run.get("peakRssBytes") is not None]
    failures = [run for run in runs if not run.get("ok")]
    return {
        "iterations": len(runs),
        "passed": len(runs) - len(failures),
        "failed": len(failures),
        "failureRate": round(len(failures) / len(runs), 4) if runs else None,
        "startupWallMs": {
            "min": round(min(wall), 3) if wall else None,
            "median": round(statistics.median(wall), 3) if wall else None,
            "p95": percentile(wall, 0.95),
            "max": round(max(wall), 3) if wall else None,
        },
        "peakRssBytes": {
            "min": min(memory) if memory else None,
            "median": int(statistics.median(memory)) if memory else None,
            "max": max(memory) if memory else None,
        },
    }


def benchmark_engine(args: argparse.Namespace) -> dict[str, Any]:
    dist = args.engine_root / "dist"
    jar = dist / ENGINE_JAR
    manifest = dist / "pack.json"
    if not jar.is_file():
        raise FileNotFoundError(f"Engine JAR not found: {jar}")
    if not manifest.is_file():
        raise FileNotFoundError(f"Pack manifest not found: {manifest}")

    engine_work = args.work_dir / "engine"
    if engine_work.exists():
        shutil.rmtree(engine_work)
    engine_work.mkdir(parents=True, exist_ok=True)

    runs: list[dict[str, Any]] = []
    for index in range(args.iterations):
        save_root = engine_work / f"run-{index + 1}"
        command = [
            args.java,
            "-Dfile.encoding=UTF-8",
            "-jar",
            str(jar),
            "--pack-root",
            str(dist),
            "--manifest",
            "pack.json",
            "--save-root",
            str(save_root),
            "--headless-smoke",
        ]
        measured = run_measured(f"engine-headless-smoke-{index + 1}", command, args.engine_root, args.timeout_seconds)
        report_path = save_root / "headless-smoke" / "headless-smoke-report.json"
        report = read_json(report_path)
        ok = (
            measured["exitCode"] == 0
            and not measured["timedOut"]
            and report is not None
            and report.get("status") == "PASS"
            and report.get("world", {}).get("saveReload") is True
            and report.get("adapterCore", {}).get("ready") is True
            and report.get("adapterCore", {}).get("rejected") == 0
        )
        runs.append({
            **measured,
            "ok": ok,
            "reportPath": str(report_path),
            "report": report,
            "metrics": {
                "saveReload": report.get("world", {}).get("saveReload") if report else None,
                "contentIdentityVerified": report.get("world", {}).get("contentIdentityVerified") if report else None,
                "contentGraph": report.get("contentGraph") if report else None,
                "adapterCore": report.get("adapterCore") if report else None,
            },
        })

    first_report = next((run.get("report") for run in runs if run.get("report")), None)
    return {
        "runtime": "echo-standalone-engine",
        "artifact": str(jar),
        "manifest": str(manifest),
        "summary": summarize_runs(runs),
        "contentGraph": first_report.get("contentGraph") if first_report else None,
        "saveLoad": {
            "allRunsSaveReload": all(run.get("metrics", {}).get("saveReload") is True for run in runs),
            "allRunsContentIdentityVerified": all(run.get("metrics", {}).get("contentIdentityVerified") is True for run in runs),
        },
        "adapterCore": first_report.get("adapterCore") if first_report else None,
        "runs": runs,
    }


def collect_legacy_reports(legacy_root: Path) -> dict[str, Any]:
    reports: dict[str, Any] = {}
    for key, relative in LEGACY_REPORTS.items():
        path = legacy_root / relative
        document = read_json(path)
        reports[key] = {
            "path": str(path),
            "present": document is not None,
            "status": document.get("status") if document else None,
            "schema": document.get("schema") if document else None,
            "generatedAt": document.get("generatedAt") if document else None,
            "summary": legacy_report_summary(key, document),
        }
    return reports


def legacy_report_summary(key: str, document: dict[str, Any] | None) -> dict[str, Any] | None:
    if not document:
        return None
    if key == "contentGraph":
        return {
            "graphs": document.get("graphs"),
            "moduleCount": document.get("moduleCount"),
            "nodes": document.get("nodes"),
            "edges": document.get("edges"),
            "features": document.get("features"),
            "diagnostics": document.get("diagnostics"),
            "failures": document.get("failures"),
        }
    if key == "saveRuntime":
        return {
            "transactionCount": document.get("transactionCount"),
            "filesTracked": document.get("filesTracked"),
            "corruptionDetected": document.get("corruptionDetected"),
            "journalEntries": document.get("journalEntries"),
        }
    if key == "alphaReadiness":
        return {
            "ready": document.get("ready"),
            "blockingFailures": document.get("blockingFailures"),
            "checksPassed": document.get("checksPassed"),
            "checksTotal": document.get("checksTotal"),
            "launcherLaunched": document.get("launcherLaunched"),
        }
    return {
        "keys": sorted(document.keys()),
    }


def run_legacy_tasks(args: argparse.Namespace) -> list[dict[str, Any]]:
    if not args.run_legacy:
        return []
    return run_legacy_gradle_tasks(args, args.legacy_task, "legacy")


def run_legacy_client_launch_tasks(args: argparse.Namespace) -> list[dict[str, Any]]:
    if not args.run_legacy_client_launch:
        return []
    return run_legacy_gradle_tasks(args, args.legacy_client_launch_task, "legacy-client-launch")


def run_legacy_gradle_tasks(args: argparse.Namespace, tasks: list[str], label_prefix: str) -> list[dict[str, Any]]:
    wrapper = args.legacy_runtime_root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise FileNotFoundError(f"Legacy Gradle wrapper not found: {wrapper}")
    runs = []
    for task in tasks:
        command = [str(wrapper), *DEFAULT_LEGACY_GRADLE_ARGS, task]
        runs.append(run_measured(f"{label_prefix}-{task}", command, args.legacy_runtime_root, args.timeout_seconds))
    return runs


def summarize_legacy(
    legacy_root: Path,
    task_runs: list[dict[str, Any]],
    client_launch_runs: list[dict[str, Any]],
) -> dict[str, Any]:
    reports = collect_legacy_reports(legacy_root)
    content_graph = reports["contentGraph"]
    save = reports["saveRuntime"]
    alpha = reports["alphaReadiness"]
    task_summary = summarize_runs([{**run, "ok": run.get("exitCode") == 0 and not run.get("timedOut")} for run in task_runs])
    client_launch_summary = summarize_runs(
        [{**run, "ok": run.get("exitCode") == 0 and not run.get("timedOut")} for run in client_launch_runs]
    )
    return {
        "runtime": "echo-standalone-runtime",
        "reports": reports,
        "taskRuns": task_runs,
        "clientLaunchProxyRuns": client_launch_runs,
        "summary": {
            "contentGraphPass": content_graph.get("status") == "PASS",
            "saveRuntimePass": save.get("status") == "PASS",
            "alphaReady": alpha.get("status") == "READY" or alpha.get("summary", {}).get("ready") is True,
            "taskFailureRate": round(
                len([run for run in task_runs if run.get("exitCode") != 0 or run.get("timedOut")]) / len(task_runs),
                4,
            ) if task_runs else None,
            "taskWallMs": task_summary["startupWallMs"] if task_runs else None,
            "taskPeakRssBytes": task_summary["peakRssBytes"] if task_runs else None,
            "clientLaunchProxyFailureRate": round(
                len([run for run in client_launch_runs if run.get("exitCode") != 0 or run.get("timedOut")])
                / len(client_launch_runs),
                4,
            ) if client_launch_runs else None,
            "clientLaunchProxyWallMs": client_launch_summary["startupWallMs"] if client_launch_runs else None,
            "clientLaunchProxyPeakRssBytes": client_launch_summary["peakRssBytes"] if client_launch_runs else None,
        },
    }


def build_gate(engine: dict[str, Any], legacy: dict[str, Any], run_legacy: bool) -> dict[str, Any]:
    reasons: list[str] = []
    engine_summary = engine.get("summary", {})
    if engine_summary.get("failureRate") != 0:
        reasons.append("Engine headless launch failure rate is not zero.")
    if not engine.get("saveLoad", {}).get("allRunsSaveReload"):
        reasons.append("Engine save/reload did not pass in every measured run.")
    if not legacy.get("summary", {}).get("contentGraphPass"):
        reasons.append("Legacy Standalone Runtime content graph evidence is missing or failing.")
    if not legacy.get("summary", {}).get("saveRuntimePass"):
        reasons.append("Legacy Standalone Runtime save evidence is missing or failing.")
    if not run_legacy:
        reasons.append("Legacy runtime tasks were not executed in this run; existing reports were ingested only.")
    reasons.append("Direct legacy player-launch startup and memory metrics are still required before replacement talk.")
    reasons.append("Gameplay proof and performance comparison remain advisory until a real play route is captured.")
    return {
        "status": "NOT_READY_FOR_REPLACEMENT_DECISION",
        "engineBaselineReady": engine_summary.get("failureRate") == 0 and engine.get("saveLoad", {}).get("allRunsSaveReload") is True,
        "legacyEvidenceReady": legacy.get("summary", {}).get("contentGraphPass") is True and legacy.get("summary", {}).get("saveRuntimePass") is True,
        "reasons": reasons,
    }


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine-root", type=Path, default=root)
    parser.add_argument("--legacy-runtime-root", type=Path, default=root.parent / "ECHO-Standalone-Runtime")
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--java", default="java")
    parser.add_argument("--work-dir", type=Path, default=root / "build" / "phase6-performance-comparison")
    parser.add_argument("--out", type=Path, default=root / "reports" / "PHASE6_PERFORMANCE_COMPARISON.json")
    parser.add_argument("--run-legacy", action="store_true", help="Also invoke selected legacy Standalone Runtime Gradle smoke tasks.")
    parser.add_argument(
        "--run-legacy-client-launch",
        action="store_true",
        help="Also invoke selected legacy client startup proxy tasks. This is not visible packaged player-launch evidence.",
    )
    parser.add_argument("--legacy-task", action="append", default=None)
    parser.add_argument("--legacy-client-launch-task", action="append", default=None)
    args = parser.parse_args()
    args.engine_root = args.engine_root.resolve()
    args.legacy_runtime_root = args.legacy_runtime_root.resolve()
    args.work_dir = args.work_dir.resolve()
    args.out = args.out.resolve()
    if args.iterations < 1:
        parser.error("--iterations must be at least 1")
    if args.legacy_task is None:
        args.legacy_task = list(DEFAULT_LEGACY_TASKS)
    if args.legacy_client_launch_task is None:
        args.legacy_client_launch_task = list(DEFAULT_LEGACY_CLIENT_LAUNCH_TASKS)
    return args


def main() -> None:
    args = parse_args()
    engine = benchmark_engine(args)
    legacy_task_runs = run_legacy_tasks(args)
    legacy_client_launch_runs = run_legacy_client_launch_tasks(args)
    legacy = summarize_legacy(args.legacy_runtime_root, legacy_task_runs, legacy_client_launch_runs)
    report = {
        "schema": "echo.standalone_engine.performance_comparison.v1",
        "generatedAt": iso_now(),
        "host": {
            "os": platform.platform(),
            "python": platform.python_version(),
            "machine": platform.machine(),
        },
        "repos": {
            "engine": git_snapshot(args.engine_root),
            "legacyStandaloneRuntime": git_snapshot(args.legacy_runtime_root),
        },
        "methodology": {
            "engine": "Runs the packaged ECHO Standalone Engine JAR with --headless-smoke and measures process wall time plus sampled peak RSS.",
            "legacy": "Ingests existing legacy Standalone Runtime reports; with --run-legacy, measures selected Gradle smoke tasks as task-level evidence.",
            "legacyClientLaunchProxy": "With --run-legacy-client-launch, measures selected legacy client startup proxy tasks. These exercise the player-facing client assembly but are not visible packaged player-launch evidence.",
            "comparabilityWarning": "Legacy Gradle task timings are not direct player-launch timings and must not be used alone for replacement decisions.",
            "iterations": args.iterations,
            "legacyTasksExecuted": args.run_legacy,
            "legacyClientLaunchProxyExecuted": args.run_legacy_client_launch,
        },
        "engine": engine,
        "legacyStandaloneRuntime": legacy,
        "replacementGate": build_gate(engine, legacy, args.run_legacy),
    }
    write_json(args.out, report)
    print(f"Phase 6 performance comparison evidence written: {args.out}")
    print(f"Gate: {report['replacementGate']['status']}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Phase 6 performance comparison failed: {error}", file=sys.stderr)
        sys.exit(1)
