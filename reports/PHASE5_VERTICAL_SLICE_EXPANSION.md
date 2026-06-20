# Phase 5 Vertical Slice Expansion

- Date: 2026-06-20
- Engine version: `2.0.0-beta.2`
- Pack id: `ashfall-standalone-engine-edition`
- Status: local verification pass
- Publication state: not yet promoted to public release assets or Release Index hashes

## Imported graph-only modules

The expansion imports selected ECHO-Modules release graph resources as Engine-compatible, resource-only module folders. Legacy executable entrypoints are stripped because those jars target older runtime contracts and do not implement the Standalone Engine public module API.

- `echocore`
- `echoplatformcore`
- `echoschemacore`
- `echovalidationcore`
- `echocontentcore`
- `echohudcore`

## Verification command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

## Local evidence

- Build result: PASS
- Java compile: PASS (`javac --release 21 -Xlint:all -Werror`)
- Installed module JARs: 18
- Content graph status: PASS
- Content graph modules: 18
- Content graph nodes: 133
- Content graph edges: 109
- Content graph features: 16
- Unresolved references: 0
- Export mapping totals in packaged evidence: 133 per target
- Runtime-required parity mappings in headless smoke: 78 per target
- Cross-runtime targets: `echo_native`, `neoforge`, `echo_runtime_standalone`
- Content graph fingerprint: `c37b52c8a3bf35c1236a2326c3892bdc61f04a0efb0bda1f7874a428545a7330`
- Headless smoke: PASS
- Save/reload content identity: PASS

## Runtime evidence

- AdapterCore ready: true
- AdapterCore accepted descriptors: 48
- AdapterCore rejected descriptors: 0
- AdapterCore revoked descriptors: 0
- Runtime blocks: 16
- Runtime items: 24
- Runtime recipes: 4
- Runtime entity definitions: 3
- Runtime game extensions: 1
- Headless world chunks: 25
- Headless spawned entities: 4

## Guardrail

This is a graph-first playable vertical slice expansion. It is valid install, repair, launch, content graph, and headless runtime evidence, but it is not a full Ashfall gameplay parity claim and is not a replacement decision for the legacy Standalone Runtime lane.
