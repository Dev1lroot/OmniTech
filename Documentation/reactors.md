# OmniTech — Nuclear Reactors & Fuel Cycle

This document covers the multiblock fission reactor, its temperature and
radiation simulation, failure modes, and the full nuclear fuel cycle —
uranium enrichment, fuel rod fabrication, spent-fuel reprocessing, and
plutonium production.

---

## Table of Contents

1. [Reactor Multiblock](#1-reactor-multiblock)
2. [Cell Types & Rods](#2-cell-types--rods)
3. [Neutron Flow Simulation](#3-neutron-flow-simulation)
4. [Coolant, Pressure & Ports](#4-coolant-pressure--ports)
5. [Controls](#5-controls)
6. [Failure Modes](#6-failure-modes)
7. [Radiation](#7-radiation)
8. [Nuclear Fuel Cycle](#8-nuclear-fuel-cycle)
9. [Nuclear Bomb](#9-nuclear-bomb)

---

## 1. Reactor Multiblock

A reactor is a hollow rectangular box built from **Reactor Block**
(`omnitech:reactor_block`), with **Reactor Cell** blocks (`omnitech:reactor_cell`)
forming the non-corner positions of the top face, and **Reactor Port** blocks
(`omnitech:reactor_port`) optionally replacing shell blocks on the walls/floor
to move coolant in and out.

| Property | Value |
|----------|-------|
| Height | Fixed at 7 blocks |
| Width / Depth | 3–11 blocks each, independently |
| Corners | Must always be `reactor_block` |
| Top face (non-corner) | `reactor_cell` (usable) or `reactor_block` |
| Walls & floor (non-corner) | `reactor_block` or `reactor_port` |
| Interior | Must be entirely air |
| Coolant capacity | `(width − 2) × (depth − 2) × 5` × 1000 mB (interior air-block count × 1000 mB) |

Structure detection runs whenever a boundary block changes and re-validates
every second (20 ticks) while formed. Removing any cell or port block
invalidates the nearest formed reactor; if the reactor is hot when that
happens (coolant ≥ 300 °C or any rod ≥ 300 °C), it detonates instead of
just shutting down.

---

## 2. Cell Types & Rods

Each `reactor_cell` slot holds one rod item, determining its type:

| Type | Item | Behaviour |
|------|------|-----------|
| Fuel | `omnitech:reactor_fuel_rod` | Emits neutron flux; heats up under flow; damages over time and becomes a **Depleted Reactor Fuel Rod** when its durability is exhausted. |
| Control | `omnitech:reactor_control_rod` | Absorbs a percentage (0–100%, set per-rod) of neutron flux passing through its cell; damaged whenever it sits adjacent to an active fuel rod, and is consumed when its durability runs out. |
| Reflector | `omnitech:reactor_neutron_reflector` | Bounces 50% of the flux that would have passed through it back to the emitting fuel rod instead of absorbing it. |
| Empty | — | No effect. |

`reactor_fuel_rod` has 2000 durability, `reactor_control_rod` has 500.
Both track a **current temperature** and **peak temperature** as item data.

---

## 3. Neutron Flow Simulation

Every 20 ticks the reactor recomputes flux for all cells using a
Chebyshev-distance ring model, evaluated for every fuel rod against every
other cell in the structure:

| Chebyshev distance | Base flux |
|---------------------|-----------|
| 1 (3×3 ring) | 100% |
| 2 (5×5 ring) | 75% |
| 3 (7×7 ring) | 50% |
| 4 (9×9 ring) | 25% |

- All flux is scaled by **coolant fill fraction × the coolant fluid's
  neutron-slowing coefficient** (see [`OmniTechFluids`](../src/main/java/com/dev1lroot/mcmods/omnitech/OmniTechFluids.java) /
  `FluidPhysicsRegistry`); an empty tank means zero reaction regardless of
  rod layout.
- The straight-line path between source and target is traced cell-by-cell:
  a **control rod** on the path multiplies attenuation by `1 − insertion%`;
  a **reflector** stops the path and instead adds flux back to the source;
  another **fuel rod** on the path fully blocks it.
- A cell receiving > 25% flux gains +1 °C on its rod that tick (up to
  `MAX_TEMPERATURE` = 2000 °C) and takes 1 durability damage. Below that
  threshold the rod cools, but never below `min(peak temperature, 300 °C)` —
  a rod that has run hot stays warm.
- Coolant temperature is the average of all fuel-rod temperatures, decaying
  1 °C per cycle on its own.

The reactor GUI shows each cell's flux as a percentage relative to the
hottest cell that tick.

---

## 4. Coolant, Pressure & Ports

- The reactor holds a **single fluid tank** shared by the whole structure.
  Inserting a fluid blends its temperature with the existing contents;
  only one fluid type may be present at a time.
- **Pressure** (0–1000) is the sum of a fill component (fluid amount ÷
  capacity × 500) and a thermal component (scaled 0–500 between ambient
  and 350 °C).
- **Reactor Ports** on the shell push coolant out to any connected fluid
  network (pipes, tanks) once per reactor tick, and are the only way to
  drain or refill the tank externally.
- Any fluid works as coolant, but its **neutron-slowing coefficient**
  (physics data registered per-fluid) determines how much reaction it
  actually sustains — see [Power Networks](power_networks.md) for the
  fluid physics registry.

---

## 5. Controls

The reactor GUI (`ReactorScreen`/`ReactorMenu`) exposes:

| Action | Packet | Effect |
|--------|--------|--------|
| Start | `StartReactorPacket` | Withdraws all control rods (insertion → 0%). |
| Scram | `ScramReactorPacket` | Emergency shutdown — drives all control rods to 100% insertion. |
| Set rod insertion | `SetControlRodPacket` | Sets one control rod's insertion percentage directly. |
| Depressurize | `DepressurizeReactorPacket` | Forces an immediate coolant flush through the ports. |

---

## 6. Failure Modes

| Trigger | Threshold | Result |
|---------|-----------|--------|
| Cell visual state | rod ≥ 300 °C | Cell renders as **heating**. |
| Meltdown | any rod ≥ 1200 °C | Reactor detonates and invalidates itself immediately. |
| Unsafe disassembly | coolant ≥ 300 °C or any rod ≥ 300 °C when a cell/port is broken | Reactor detonates instead of quietly shutting down. |

A meltdown or unsafe disassembly both call the same `NuclearExplosion.trigger`
used by the [nuclear bomb](#9-nuclear-bomb) — reactor safety and weapons
share one explosion model.

**Nuclear explosion — timed, four-zone effect:**

| Zone | Radius | Fires at | Effect |
|------|--------|----------|--------|
| Entity kill | 96 blocks (sphere) | immediate | All non-creative living entities inside die. |
| 1 | 0–32 blocks | 5 s (100 ticks) | 100% block destruction; a 32-block-diameter sky-clearing column is queued upward from the blast. |
| 2 | 32–64 blocks | 6 s (120 ticks) | 75% chance per block of destruction. |
| Leaf/flower strip | up to 300 blocks | 6 s (120 ticks) | Leaves fully cleared to 96 blocks, 50% chance out to 300; flowers cleared to 192 blocks. |
| 3 | 64–192 blocks | 11 s (220 ticks) | 96 primed TNT entities scattered through the shell (no direct block removal). |
| Biome conversion | 320 blocks (XZ) | 11 s (220 ticks) | Surface biome converted to `omnitech:nuclear_wastelands`. |

Explosion queues are broken into `RadiationSavedData.BLOCKS_PER_TICK` = 14,000
block-removals per tick to avoid a single-tick freeze on large blasts.

---

## 7. Radiation

Two independent radiation sources exist, both applying the `omnitech`
Radiation mob effect (health drain + a screen noise overlay client-side).

**Active reactor proximity** — while a formed reactor's core temperature is
above 0, every 5 s (100 ticks) all non-creative living entities within 10
blocks of the structure's bounding box receive Radiation I; anyone standing
*inside* the reactor's interior receives Radiation III.

**Fallout** — every nuclear explosion (reactor meltdown, unsafe disassembly,
or a detonated [nuclear bomb](#9-nuclear-bomb)) registers a permanent
radiation center in `RadiationSavedData`, persisted in the world save.
Every 5 s, all non-creative players in the Overworld are checked against
every registered center and given the *highest* tier in range:

| Tier | Range | Effect |
|------|-------|--------|
| Radiation I | ≤ 256 blocks | Noise overlay; no health reduction. |
| Radiation II | ≤ 128 blocks | I, plus −1 max health every 3 minutes. |
| Radiation III | ≤ 64 blocks | II, but −1 max health every 20 s; the effect itself becomes permanent (cleared only on death) instead of a refreshed 10 s buff. |

Max-health loss is cumulative per player (clamped to leave at least 1 heart)
and stored in `RadiationSavedData`; it — and Radiation III itself — is only
cleared on death. Milk and any other effect-clearing path are blocked for
this effect. A fully-worn **Hazmat Suit** or **Space Suit** blocks Radiation
I and II entirely and downgrades Radiation III to a temporary Radiation I
for the duration spent inside the zone.

---

## 8. Nuclear Fuel Cycle

Fresh fuel is produced through a multi-machine enrichment pipeline, mirroring
the real-world route from ore to fuel rod. Depleted rods are reprocessed
through a second pipeline (PUREX-style) that recovers both unused uranium
and bred plutonium.

### 8.1 Front end — enrichment

| Stage | Machine | Input | Output |
|-------|---------|-------|--------|
| 1. Digestion | Solvation | `uranium_dust` × 4 + `nitric_acid` (1000 mB) | `uranyl_nitrate` (1000 mB) |
| 2. Fluorination | Chemical Reactor | `uranyl_nitrate` (1000 mB) + `fluorine` (1500 mB) | `uranium_hexafluoride` (1000 mB) |
| 3. Enrichment cascade | Fractional Distillation | `uranium_hexafluoride` (1000 mB) | `enriched_uranium_hexafluoride` (150 mB) + `depleted_uranium_hexafluoride` (850 mB) |
| 4. Metal reduction | Chemical Infuser | `enriched_uranium_hexafluoride` (1000 mB) + `magnesium_ingot` × 2 | `enriched_uranium_ingot` × 1 |
| 5. Rod assembly | Crafting table | `enriched_uranium_ingot` + `zirconium_ingot` × 2 (cladding) | `reactor_fuel_rod` |

Stage 4 mirrors the real Ames/calciothermic reduction of `UF₆` to metal using
a reducing metal (magnesium here). Stage 5 mirrors zirconium-alloy ("Zircaloy")
cladding — real fuel-rod cladding uses zirconium for its low neutron
absorption, matching why zirconium is one of the fuel cycle's core inputs.

### 8.2 Back end — reprocessing & plutonium

| Stage | Machine | Input | Output |
|-------|---------|-------|--------|
| 6. Dissolution | Solvation | `depleted_reactor_fuel_rod` × 1 + `nitric_acid` (1500 mB) | `spent_fuel_solution` (1500 mB) |
| 7. PUREX separation | Fractional Distillation | `spent_fuel_solution` (1500 mB) | `uranyl_nitrate` (1000 mB, recycle to stage 2) + `plutonium_nitrate` (300 mB) + `nuclear_waste` (200 mB) |
| 8. Metal reduction | Chemical Infuser | `plutonium_nitrate` (1000 mB) + `calcium_dust` × 3 | `plutonium_ingot` × 1 |

Stage 7 recovers unburned uranium as `uranyl_nitrate`, which re-enters
Stage 2 — closing the loop rather than discarding it as waste. Stage 8
mirrors the historical calciothermic reduction of plutonium fluoride with
calcium metal.

---

## 9. Nuclear Bomb

`omnitech:nuclear_bomb` is a placeable block that detonates via
`NuclearExplosion.trigger` (see [§6](#6-failure-modes)) the moment it
receives any redstone signal — including from simply being placed against
an already-powered block. It has no internal fuse or timer; treat any
redstone connection as live.

Crafted from a plutonium core:

```
[Tungsten Plate] [Steel Plate]    [Tungsten Plate]
[Steel Plate]    [Plutonium Ingot][Steel Plate]
[Tungsten Plate] [Redstone Block] [Tungsten Plate]
```

The redstone block at the base doubles as the trigger the finished bomb
reacts to.
