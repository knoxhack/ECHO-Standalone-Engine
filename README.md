# ECHO Standalone Engine

A clean Java 21 standalone engine lane for ECHO packs and modules.

This repository is a side-by-side rewrite of the older ECHO Standalone Runtime. It is not replacing that runtime yet. The first public lane is the Ashfall Standalone Engine Edition beta, built as a graph-first playable vertical slice for install, repair, launch, content graph, and runtime verification.

Current release target:

```text
2.0.0-beta.5
```

## Runtime Contract

- Runtime product id: `echo-standalone-engine`
- Loader id: `echo-standalone-engine`
- Runtime target: `echo_runtime_standalone`
- Required Java: `21+`
- Pack id: `ashfall-standalone-engine-edition`
- Pack artifact: `dist/ashfall-standalone-engine-edition-2.0.0-beta.5.zip`
- Launcher manifest: `dist/ashfall-standalone-engine-edition-beta-2.0.0-beta.5.pack.json`

## Run The Prebuilt Game

Java 21 or newer is required.

Windows:

```powershell
.\run.ps1
```

Linux or macOS:

```bash
./run.sh
```

Equivalent engine command:

```text
java -Dfile.encoding=UTF-8 -jar dist/echo-standalone-engine-2.0.0-beta.5.jar --pack-root dist --manifest pack.json --save-root saves
```

## Build And Verify

Linux or macOS:

```bash
./scripts/build.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

The build:

1. Compiles the engine and public API with Java 21 and `-Xlint:all -Werror`.
2. Packages the current 19-module standalone verification slice.
3. Writes strict module SHA-256 and size evidence into the pack manifests.
4. Produces `content-graph-evidence.json` with standalone target parity evidence.
5. Runs JSON, dependency graph, palette/save, render, and headless smoke checks.
6. Emits the install ZIP, Launcher manifest, runtime checksums, and build verification report.

## Included Verification Slice

The beta ZIP includes the current 19-module Ashfall/ECHO verification slice:

- `echoadaptercore`
- `echoashfallprotocol`
- `echocommonloot`
- `echocreatureroles`
- `echofoundationcore`
- `echomaterialcore`
- `echoscreencore`
- `echostationcore`
- `echoterminal`
- `echotoolcore`
- `echoweathercore`
- `echoworldstarter`

The Phase 5 expansion also imports seven graph-only ECHO foundation and gameplay-contract modules from the ECHO-Modules release graph, with old executable entrypoints stripped:

- `echocore`
- `echoplatformcore`
- `echoschemacore`
- `echovalidationcore`
- `echocontentcore`
- `echohudcore`
- `echohealthcore`

The expanded local slice validates 19 modules, 140 graph nodes, 110 graph edges, 16 features, and 78 runtime-required nodes mapped across `echo_native`, `neoforge`, and `echo_runtime_standalone`. This content set is intentionally labeled as a graph-first playable vertical slice. It verifies the standalone engine path and module graph behavior, but it does not claim full Ashfall gameplay parity with the existing NeoForge or legacy Standalone Runtime lanes.

## Strict Runtime Rules

The generated manifests preserve:

- `strictArtifacts: true`
- `strictContentGraph: true`
- `requireCrossRuntimeParity: true`

Required module JARs install under `mods/`. The engine refuses required modules when their file, descriptor, version, dependency, trust declaration, hash, or size does not match the manifest.

## Controls

| Control | Action |
| --- | --- |
| `W A S D` | Move |
| Mouse | Look |
| `Space` | Jump |
| `Shift` | Sprint |
| Left click | Attack an entity or mine a block |
| Right click | Use an interaction or place the selected block |
| `1`-`9` / wheel | Select hotbar slot |
| `F` | Consume selected food or water |
| `C` | Craft the first available recipe |
| `F3` | Debug overlay |
| `Esc` | Pause or resume |
| `S` in pause menu | Save |
| `Q` in pause menu | Save and return to title |

## Scope

This is a working clean-room beta foundation and playable vertical slice. Networking, GPU rendering, advanced automation logic, complex block shapes, full UI inventories, animation systems, and complete first-party ECHO module catalog parity remain follow-on work.
