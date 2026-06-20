# Ashfall Vertical Slice

The included Ashfall module is intentionally implemented through the same public module contracts available to other packs.

## Content

### Blocks

- Scorched Basalt
- Ash Soil
- Toxic Ash
- Rusted Debris
- Toxic Waste Barrel
- Shelter Anchor
- Field Terminal
- Damaged Power Node
- Crash Cache
- Rain Collector
- Water Purifier
- Scrap Press
- Radiation Cleanser

### Items

- Placeable block items
- Clean and dirty water
- Emergency rations
- Scrap metal and compressed scrap
- Filter cartridges
- Power cells
- Portable scanner

### Entities

- Ash Wraith
- Irradiated Wolf
- Wasteland Scavenger

## Survival loop

Hydration drains faster than hunger. Toxic ash and waste barrels accelerate exposure. Shelter anchors reduce exposure. Severe dehydration, starvation, or exposure damages health.

The first objective route is:

1. Inspect the field terminal.
2. Establish shelter.
3. Recover the crash cache.
4. Repair the damaged power node.
5. Stabilize supplies and survive.

State is stored in the `echoashfall` module state map and round-trips with the world save.

## Machine interactions

Right-click:

- Rain Collector: produces dirty water.
- Water Purifier: consumes dirty water and a filter; produces clean water.
- Scrap Press: consumes two scrap metal; produces compressed scrap.
- Radiation Cleanser: consumes clean water; sharply reduces exposure.
- Damaged Power Node: consumes a power cell and advances the mission.
- Crash Cache: one-time supply recovery.
- Field Terminal: reports the current objective.

## Parity posture

This slice proves the architecture needed to port Ashfall systems cleanly: data content, worldgen, survival, objectives, machines, creatures, HUD, inventory, interactions, and save data all originate from a separately built module JAR.

It is not a claim that every class, asset, mission, machine, biome, or entity from the NeoForge edition has already been ported.

## Phase 5 graph expansion

The local Phase 5 slice adds graph-only foundation and gameplay-contract coverage from `echocore`, `echoplatformcore`, `echoschemacore`, `echovalidationcore`, `echocontentcore`, `echohudcore`, and `echohealthcore`. These modules are imported from ECHO-Modules release graph resources with legacy executable entrypoints stripped so the standalone engine can validate their content graph contracts without loading old runtime code.

Current local evidence validates 19 modules, 140 graph nodes, 110 graph edges, 16 features, and 78 runtime-required nodes mapped across Native, NeoForge, and Standalone Engine targets. This is broader graph coverage for the playable vertical slice, not a full gameplay parity claim.
