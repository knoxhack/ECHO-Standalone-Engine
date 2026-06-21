# Ashfall Parity Checklist — Standalone Engine

This checklist tracks what the Standalone Engine must implement before the Ashfall Standalone Engine Edition can stop being warning-gated.

Source of truth: `ECHO-Ashfall-NeoForge-Edition/docs/parity-spec.md`

## Beta Requirements

### UI Foundation
- [ ] Engine consumes `echoscreencore` `UI_SCREEN` / `SERVICE` contracts.
- [ ] Engine consumes `echothemecore` theme tokens.
- [ ] Engine consumes `echoinputcore` input contexts and keybind registry.
- [ ] Engine consumes `echohudcore` HUD widget contracts.
- [ ] Canonical Ashfall screen IDs are mapped to module page IDs.

### Screen Runtime
- [ ] Screen stack exists (push/pop/replace).
- [ ] Input focus, mouse hover, keyboard navigation, tab focus, disabled states work.
- [ ] Shared widgets: button, label, panel, item slot, text field, progress bar, tooltip.
- [ ] Title screen rendered through ScreenCore runtime.
- [ ] Pause screen rendered through ScreenCore runtime.

### Main Menu / World Flow
- [ ] Main menu: Continue, New World, Load World, Settings, Modules, Diagnostics, Quit.
- [ ] New world screen: name, seed, difficulty, survival settings, module validation.
- [ ] Load world screen: save list, version warnings, missing-module warnings, repair suggestions.
- [ ] Pause menu: Resume, Save, Settings, Modules, Return To Title, Quit.
- [ ] Return to title does not terminate the runtime.

### Inventory / Crafting
- [ ] Full inventory screen with hotbar and backpack grid.
- [ ] Drag/drop, split stack, shift-click, number-key swap, mouse-wheel hotbar selection.
- [ ] Item tooltips and stack limits.
- [ ] Crafting through `echorecipecore` contracts.
- [ ] Inventory persists through save/load.

### HUD
- [ ] Health, food, water bars.
- [ ] Ash exposure / hazard meter.
- [ ] Hotbar and crosshair.
- [ ] Block/entity nameplate.
- [ ] Objective and interaction prompts.

### Keybinds / Input
- [ ] Movement: W/A/S/D, jump, sprint.
- [ ] Mouse look, left-click attack/mine, right-click use/place.
- [ ] Inventory key, terminal key, lens key, index key.
- [ ] Keybinds driven by `echoinputcore` contracts.

### World / Save
- [ ] New world creation with validated module set.
- [ ] Save metadata includes version and content-graph fingerprint.
- [ ] Load world validates identity and warns on mismatch.
- [ ] Save/reload smoke passes.

## RC Requirements

### Advanced Systems
- [ ] Stations via `echostationcore`.
- [ ] Terminal via `echoterminal`.
- [ ] Index via `echoindex`.
- [ ] Lens via `echolens`.
- [ ] Settings screen.
- [ ] Death and respawn flow.
- [ ] Module diagnostics screen.

### Gameplay
- [ ] Survival systems: health, hunger, hydration, ash exposure, heat/storm pressure.
- [ ] Status effects via `echostatuscore`.
- [ ] Entities spawn, pursue, attack, take damage, drop loot.
- [ ] Player can die and respawn.

## Later

- [ ] Full visual theme parity with `echothemecore`.
- [ ] Advanced accessibility settings.
- [ ] Rich animation and particle parity.
- [ ] Full module catalog parity beyond the 19-module slice.

## Engine-Specific Evidence Gates

Before the warning gate is removed, the Engine must produce:
- [ ] Build verification report with all checks PASS.
- [ ] Content graph evidence with 0 unresolved references.
- [ ] Headless smoke save/reload PASS.
- [ ] Render smoke screenshot of title/pause screens.
- [ ] Performance comparison against legacy Standalone Runtime.
- [ ] Gameplay proof: mine, place, collect, craft, inventory, terminal, index, lens, save/reload.
- [ ] Launcher install/repair/launch path evidence.
