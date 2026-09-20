# Changelog

All notable changes to OmniTech are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Versions follow [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Added
- Petroleum chemistry: `crude_oil`, `heavy_fuel_oil`, `diesel`, `naphtha`, `propane`, `butane` fluids, plus `sulfuric_acid`, `hydrochloric_acid`, and `nitric_acid`.
- Crude oil now spawns underground as distorted ore-free pockets (`CrudeOilPocketFeature`), mostly in desert biomes and occasionally in badlands; harvestable with a Fluid Collector.
- Fractional Distiller recipes refining crude oil into heavy fuel oil, diesel, naphtha, and propane (2/3/4-block structures), plus a naphtha-cracking recipe producing propane and butane.
- Fluids can now optionally declare `"world_placeable": true` in their JSON definition to register a companion in-world `LiquidBlock` (used by crude oil).
- Rocket multiblock: place `omnitech:rocket_controller` as the anchor of a structure defined in `data/omnitech/rocket_structure/*.json`. The JSON pattern is an arbitrary 3D shape (fins, tapering, non-cuboid silhouettes — not just a box); on match, every structure block is removed (no drops) and a `RocketEntity` is spawned in its place. Assembly is checked automatically right after the controller is placed, and again on right-click as a manual retry. Ships with an example `basic_rocket` template.
- `mercury` fluid (liquid metal, room-temp) and new Extractor recipes tying Minecraft 26.3's vanilla `cinnabar` and `sulfur` into the chemistry chain: roasting either `omnitech:cinnabar` or vanilla `minecraft:cinnabar` yields mercury with elemental sulfur as residue, and extracting `minecraft:sulfur` now produces `sulfuric_acid`.

### Changed
- Upgraded to NeoForge 26.3.0.1-beta on Minecraft 26.3; bumped moddev plugin to 2.0.147.
- Bumped JEI to 26.3-neoforge-31.0.0.9 for Minecraft 26.3 compatibility; migrated `OmniTechJeiPlugin` off JEI APIs deprecated for removal (`RecipeType` → `IRecipeType`, `addRecipeCatalysts` → `addCraftingStation`, `addItemStack`/`addFluidStack` → the unified `add(...)` overloads).
- Upgraded to NeoForge 26.2.0.6-beta on Minecraft 26.2; bumped JEI to 26.2-neoforge-30.1.0.12.

### Fixed
- Fixed a `NullPointerException: Block id not set` crash on startup for any fluid marked `world_placeable`, caused by constructing its companion `LiquidBlock` from `BlockBehaviour.Properties` that hadn't had its registry id bound yet; now uses `DeferredRegister.Blocks#registerBlock` so the id is set before construction.
- Discovered (not fixed, pre-existing) that `data/omnitech/worldgen/biome_modifier/penguin_spawn.json` sits at the wrong datapack path for the `neoforge:biome_modifier` registry — it needs to be under `data/omnitech/neoforge/biome_modifier/` to actually load.
- Fixed `CrudeOilPocketFeature` carving through generated structures (e.g. Trial Chambers): it replaced any non-air, non-fluid block, which included structure blocks placed before the feature runs. Now uses a natural-terrain allowlist (`BlockTags.BASE_STONE_OVERWORLD`, sand, red sand, gravel) so only genuine terrain is ever replaced.
- Crude oil pockets now generate across a much taller height range (desert: Y −50 to 65; badlands: Y −50 to 90) instead of only deep underground, so a pocket occasionally breaches the surface and settles into a visible "oil lake" in its own basin.

### Added — Reactor & Radiation (2026-05-17 – 2026-05-19)
- Nuclear explosion on meltdown (core ≥ 1200 °C) or reactor destruction while hot (> 300 °C).
  - Zone 1 (0–32 blocks): 100% block destruction, immediate.
  - Zone 2 (32–64 blocks): 75% destruction, queued at 500 blocks/tick.
  - Zone 3 (64–128 blocks): 25% destruction via random spherical sampling, queued.
- Explosion centres persisted in world save data (`RadiationSavedData`) across sessions.
- Three-tier radiation effect driven by player proximity to explosion centres:
  - **Radiation I** (≤ 256 blocks): nausea + 1 HP damage every 10 s.
  - **Radiation II** (≤ 128 blocks): all of I + max-health reduction every 3 min.
  - **Radiation III** (≤ 64 blocks): all of II + max-health reduction every 20 s + HUD noise overlay.
- Radiation effects cannot be removed by milk; Radiation III is permanent until death.
- White noise dot HUD overlay for all radiation tiers (30 / 100 / 250 dots for I / II / III).
- Per-cell control rod renderer (`ReactorCellBER`) — rods render at all times regardless of formation state.
- Core temperature heat bar in the Reactor GUI synced via `ContainerData`.
- Fuel Rod durability increased from 200 to 2000.
- Reactor controller tracks `coreTemperature` (max rod temperature) each tick.

### Added — Computing & Research (2026-05-08 – 2026-05-10)
- Research Table: 9×9 minesweeper-style blueprint unlock system with JSON-driven research definitions.
- Linux Machine Mk0 and Mk1: LogicMachine upgraded to boot a full Linux distribution.
- Floppy disk read-only attribute; ExpansionSlot render overhaul.
- Logic Gate block with full rotation support and Redstone integration (v1 → v2 final design).
- `console=ttyS0` boot argument handling; `TerminalSyncPacket` for chunked terminal output.

### Added — RISC-V & Advanced Electronics (2026-05-03 – 2026-05-06)
- RISC-V emulator upgraded to RV32GC (was RV32I); AFC instruction sets added.
- RV32IM integer multiplication/division extension.
- Display multiblock with configurable pixel density; additional display instruction set.
- Advanced Electronics tier: RAM, mass storage, display, math co-processor (parts 1–3).
- GuideBook with truth tables; floppy disk fixes.

### Added — Radio & Audio (2026-04-29 – 2026-05-01)
- Radio system v1 → v3: proximity chat, microphone, recording options, radio bands.
- Radio Scanner upgrade for band discovery.
- Thermal Electrolysis machine.
- Electric Heater machine.

### Added — Licensing & Repository (2026-05-19)
- Switched to **GPL-3.0-only**; SPDX copyright headers added to all 439 Java source files.
- `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`, GitHub issue templates, PR template.
- `gradle.properties` and `neoforge.mods.toml` updated to reflect the new license.

---

## [1.0.0] — 2026-04-14

Initial public release. Covers the full first-tier tech tree from stone tools to chemical processing and space exploration.

### Added — Space Exploration
- Rocket multi-block with smooth rendering and model; launch countdown mode.
- Moon dimension with surface generation.
- Europa dimension with trenches, underwater surfaces, and bottom spikes.
- Star Menu (v1 → v6) with background and navigation polish.
- `/spacemap` hierarchical navigation command.
- Sky Renderer v2 with orbital inclinations, relative celestial scale, and real planetary distances.
- Space Map data structure refactor for data-driven extensibility.
- Space suit item.
- Server-side fuel validation groundwork.

### Added — Thermal Systems
- Thermal Conductor network with heat distribution and losses.
- Heat Exchanger with model and radiator heat animation.
- Heat and Cold simulation (v1 → v3) with visuals.
- Fractional Distiller with heat-driven column separation.
- Boiler, steam generation, and boiler recipes.
- Stirling Engine with auto-inject fluid and upgrade.

### Added — Chemical & Electrical Processing
- Electrolysis machine (prerequisites → full implementation).
- Solvation Machine.
- Fluid Collector and Rotary Compressor.
- Electric Heater, Solar Panel, Electric Engine with reverse functionality.
- Electricity system: Simple → Remastered, with fixes and balancing.
- Thermal Electrolysis.
- Chemical reactor and fluid canisters.
- Fractional distilling upgrade; hydrazine synthesis; liquefied air processing.

### Added — Kinetic Force & Fluid Infrastructure
- Pipe system: coloured pipes, waterloggable pipes, fluid interface, worldly I/O faces.
- Fluid system with connected tanks, different fluid type collision handling, correct textures.
- Pump and conveyance integration.
- Valve block.
- KF (Kinetic Force) system rework: Reductor, Stirling Engine.
- Conveyor belts with item display, directional control, and animation fixes.
- Sorter fixes.
- Salt and pipe recipes.

### Added — Metallurgy & Processing
- 12-material system: ores, ingots, plates, wires, dusts, and components.
- Ore generation and geology system (Geology v1.0, Carnotite init).
- Macerator, Centrifuge, Foundry, Smelter, Coke Oven, Bore.
- Heavy metals, zinc, hematite; recipe fixes throughout.
- Data-driven item, fluid, recipe, and GUI systems.
- Asset generator tooling; integrity checker.
- JEI integration for all custom recipe types.

### Added — Infrastructure
- NeoForge 26.1.0.17-beta on Minecraft 26.1, Java 25.
- Deferred registration pattern for blocks, items, fluids, and creative tabs.
- GUI framework with data-driven layouts; fluid tank GUI.
- Custom fluid textures and asset factories.
- Full material tool and armour sets via factory scripts.
