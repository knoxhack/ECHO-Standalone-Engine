# Architecture

## Design goals

The rewrite follows six rules:

1. **The running pack is authoritative.** The game cannot silently substitute hard-coded content when a required module failed to load.
2. **One boot path.** GUI play, headless smoke, release verification, and save loading use the same `ModuleHost`, registries, pack manifest, and world implementation.
3. **Stable content identity.** Saves store resource IDs and module state, not assumptions about registration order.
4. **Render work is bounded.** Generation and meshing run on worker pools; the render loop consumes completed immutable results.
5. **Failures are fail-closed.** Missing files, hash drift, dependency errors, untrusted entrypoints, and path escapes stop boot with a direct error.
6. **Data is the default.** Blocks, items, recipes, and entities do not require code. Java entrypoints are reserved for behavior that cannot be expressed declaratively.

## Boot flow

```text
EngineMain
  -> LaunchOptions
  -> PackManifest
  -> ModuleHost.discover
  -> SHA-256 and descriptor verification
  -> ModuleGraph topological sort
  -> data content registration
  -> trusted EchoModule entrypoints
  -> registry freeze
  -> title screen
  -> GameSession
```

`ModuleHost` is retained for the full process lifetime. It owns module class loaders, state containers, event subscriptions, registered services, and reverse-order unload.

## Module integration

### Pack layer

`pack.json` declares the exact module files used by the game. Each required row contains:

- module ID
- expected version
- relative file path
- SHA-256
- required flag
- pack trust decision

Paths are resolved under the configured pack root and rejected if normalization escapes that root.

### Descriptor layer

`META-INF/echo.mod.json` declares:

- schema version
- stable module ID
- name and version
- optional Java entrypoint
- module trust class
- dependencies
- requested permissions

Pack and descriptor identity must agree.

### Content layer

The runtime loads JSON content before executing code. This allows a trusted entrypoint to refer to content that already exists in registries.

Registries include:

- blocks
- items
- recipes
- entities
- world generators

They reject duplicate IDs and become immutable before world creation.

### Behavior layer

Trusted code modules implement `EchoModule` and receive `EchoModuleContext`. The context exposes only engine APIs:

- content registries
- event bus
- multi-service registry
- module state
- logger
- pack and module paths

Ashfall registers a `WorldGeneratorDefinition` and a `GameExtension`. The engine discovers all `GameExtension` services and runs them through a shared lifecycle.

## World pipeline

### Chunks

Chunks are 16×64×16 integer arrays. Each chunk tracks a monotonic version and dirty state.

### Generation

`WorldStreamer` prioritizes required chunks nearest the player and generates them on daemon workers. Multiple registered generators may compose in priority order.

### Meshing

`MeshScheduler` snapshots a chunk and its neighbors, then builds visible faces off-thread. Completed meshes are accepted only when the live chunk version still matches the source version.

### Rendering

`SoftwareRenderer` performs:

- camera-space transformation
- back-face culling
- distance culling
- perspective projection
- painter sorting
- directional color shading
- fog blending
- a hard per-frame face budget

The software renderer keeps the project buildable and testable with only a JDK. A future OpenGL/Vulkan backend can consume the same `ChunkMesh` representation.

## Save format

Each world is a directory:

```text
saves/<world-id>/
  world.json
  chunks/
    c.<x>.<z>.echc
```

`world.json` stores:

- engine and pack identity
- seed and game time
- player position, view, vitals, inventory, and selected slot
- module-owned key/value state

Each `.echc` file is GZIP-compressed and contains a resource-ID palette plus palette indices. If a block no longer exists, it loads as `echo:missing_block` instead of changing into an unrelated block.

Writes use temporary files and atomic replacement when supported.

## Thread ownership

| State | Owner |
| --- | --- |
| Input, player, entities, extension ticks | Game thread |
| Chunk generation | World worker pool |
| Chunk snapshot meshing | Mesh worker pool |
| Window presentation | Game/render thread |
| Audio synthesis | Audio worker |
| Registry mutation | Boot thread only |

Chunks synchronize individual reads, writes, snapshots, and save copies. Worker results are immutable records.

## Security position

Data-only modules are the recommended untrusted addon format.

Java code modules are not a hardened security sandbox. Java's retired SecurityManager is not used. Executable modules therefore require both the descriptor and pack manifest to classify them as `official` or `developer`. A production public ecosystem should add signature/provenance checks and run untrusted code out of process rather than pretending class-loader isolation is a security boundary.
