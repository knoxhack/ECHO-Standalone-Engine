# Performance Comparison

Phase 6 compares the new `echo-standalone-engine` lane against the legacy `ECHO-Standalone-Runtime` lane before any replacement decision.

Run the local baseline:

```powershell
python .\scripts\compare_runtime_performance.py --iterations 3
```

This writes:

```text
reports/PHASE6_PERFORMANCE_COMPARISON.json
```

## What The Baseline Measures

- Engine headless startup wall time.
- Engine sampled peak resident memory.
- Engine launch failure rate across repeated headless runs.
- Engine content graph counts from the strict pack boot report.
- Engine save/reload and content identity status.
- Legacy Standalone Runtime content graph, save runtime, alpha readiness, and AdapterCore report status from existing reports.

## Optional Legacy Task Run

To refresh selected legacy smoke reports during the same comparison:

```powershell
python .\scripts\compare_runtime_performance.py --iterations 3 --run-legacy
```

The default legacy tasks are:

- `runStandaloneContentGraphLoadSmoke`
- `runStandaloneSaveRuntimeSmoke`
- `runStandaloneAlphaReadinessSmoke`

Additional tasks can be supplied with repeated `--legacy-task <task>` arguments.

## Optional Legacy Client Launch Proxy

To measure the legacy client startup proxy during the same comparison:

```powershell
python .\scripts\compare_runtime_performance.py --iterations 3 --run-legacy --run-legacy-client-launch
```

The default proxy task is:

- `runStandaloneClientRuntimeAssemblySmoke`

Additional proxy tasks can be supplied with repeated `--legacy-client-launch-task <task>` arguments.

This proxy exercises the player-facing client assembly route and records wall time plus sampled peak RSS. It is advisory evidence only: it is not a visible packaged player launch and it does not satisfy the replacement gate by itself.

## Guardrail

The report intentionally keeps `replacementGate.status` at `NOT_READY_FOR_REPLACEMENT_DECISION` until direct legacy player-launch startup, memory, content graph load, save/load, failure-rate, gameplay, and fallback evidence are all comparable. Gradle smoke task timings are useful runtime evidence, but they are not the same as measuring a player launch.
