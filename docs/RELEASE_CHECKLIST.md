# Release Checklist

## Automated

- [ ] `scripts/build.sh` or `scripts/build.ps1` passes.
- [ ] Engine and module compilation use Java 21.
- [ ] Module graph tests pass.
- [ ] Required module SHA-256 verification passes.
- [ ] Chunk palette reorder test passes.
- [ ] Offscreen render smoke produces a nonblank frame.
- [ ] Headless pack boot, world generation, save, and reload pass.
- [ ] `dist/checksums.sha256` matches every runtime file.

## Manual desktop

- [ ] New world reaches gameplay.
- [ ] Mouse capture and pause release work.
- [ ] Movement, collision, jump, and sprint work.
- [ ] Mining and placement work.
- [ ] Ashfall terminal, cache, shelter, purifier, collector, power node, press, and cleanser work.
- [ ] Creatures spawn, pursue, attack, take damage, and drop loot.
- [ ] Save, quit, continue, and autosave work.
- [ ] A 60-minute wall-clock play session has no progressive frame degradation.
- [ ] Audio either works or fails over without crashing.

## Distribution

- [ ] Runtime ZIP is built from a clean revision.
- [ ] Runtime and module artifacts are code-signed where required.
- [ ] Artifact provenance and checksums are published.
- [ ] Launcher points to the standalone main class, not NeoForge/FML.
- [ ] Release Index references exact artifact hashes.
- [ ] Gameplay evidence is captured from the exact published bytes.
