# Migration from the old Standalone Runtime

## Replace parallel boot paths

The old runtime should not be incrementally merged into this project. Migrate capabilities into one of four places:

- public API contract
- declarative module content
- engine runtime service
- pack-specific module behavior

Every migrated feature must be exercised through the same player-facing boot used by the packaged game.

## Recommended migration order

1. Convert pack and module manifests.
2. Move block, item, recipe, and entity catalogs to `echo-content` JSON.
3. Port world generation into `WorldGenerator` implementations.
4. Port survival, mission, machine, and UI logic into `GameExtension` or new public services.
5. Add save migration from old identifiers to stable `ResourceId` values.
6. Add real assets and a GPU backend without changing module identity.
7. Add networking only after server authority and deterministic state contracts are defined.

## Do not migrate

- Bootstrap evidence that only checks file existence.
- Synthetic parity counts presented as gameplay proof.
- Hard-coded Ashfall fallback content in the engine client.
- Source-tree-only module discovery.
- Separate headless and player-facing content pipelines.
- Numeric registry IDs in long-lived save files.
