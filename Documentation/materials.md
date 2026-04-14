# OmniTech — Materials Reference

OmniTech adds twelve new metal families to Minecraft. Each metal follows an
identical processing chain from raw ore to finished components, and each
generates dedicated world ore blocks.

---

## Table of Contents

1. [Metal Families](#1-metal-families)
2. [Item Forms](#2-item-forms)
3. [Storage Blocks](#3-storage-blocks)
4. [Vanilla Dust Additions](#4-vanilla-dust-additions)
5. [Processing Chain](#5-processing-chain)
6. [Worldgen](#6-worldgen)

---

## 1. Metal Families

| Metal | Primary uses |
|-------|-------------|
| **Tungsten** | High-temperature components, drill bits, reactor shielding |
| **Chromium** | Stainless alloys, plating, catalysts |
| **Tin** | Bronze alloy (with Copper), solder, early-game wiring |
| **Aluminium** | Lightweight structural parts, electrical conductors |
| **Cobalt** | High-strength alloys, battery cathodes |
| **Nickel** | Corrosion-resistant plating, alloy hardener |
| **Zinc** | Galvanising, brass alloy (with Copper) |
| **Lead** | Radiation shielding, heavy counterweights |
| **Uranium** | Nuclear fuel, radiation-emitting components |
| **Titanium** | Rocket components, high-strength lightweight structures |
| **Steel** | General industry (Iron + Chromium in Alloy Furnace) |
| **Brass** | Precision parts, fittings (Copper + Zinc in Alloy Furnace) |

---

## 2. Item Forms

Every metal is available in the following forms. Replace `<metal>` with the
lowercase metal name (e.g. `omnitech:titanium_ingot`).

| Form | Item ID | Description |
|------|---------|-------------|
| Raw ore chunk | `omnitech:raw_<metal>` | Dropped when mining ore; smelts into ingot |
| Ingot | `omnitech:<metal>_ingot` | Base refined form |
| Dust | `omnitech:<metal>_dust` | Produced by Macerator from ore or ingot |
| Plate | `omnitech:<metal>_plate` | Pressed from ingot; used in machine crafting |
| Mote | `omnitech:<metal>_mote` | Fine powder; intermediate chemical reagent |
| Nugget | `omnitech:<metal>_nugget` | 9 nuggets = 1 ingot |
| Rod | `omnitech:<metal>_rod` | Structural component for machines |
| Wire | `omnitech:<metal>_wire` | Electrical conductor; used in coils and wiring |
| Coil | `omnitech:<metal>_coil` | Wound wire for motors and generators |
| Cog | `omnitech:<metal>_cog` | Kinetic transmission gear |
| Reductor | `omnitech:<metal>_reductor` | Kinetic step-down gear; reduces shaft speed |

---

## 3. Storage Blocks

Each metal also has two storage blocks for compact inventory management:

| Block | ID | Contents |
|-------|----|---------|
| Metal block | `omnitech:<metal>_block` | 9 ingots |
| Raw ore block | `omnitech:raw_<metal>_block` | 9 raw ore chunks |

---

## 4. Vanilla Dust Additions

OmniTech also adds dust forms of several vanilla stone types, used as abrasives
and reagents in chemical processing:

| Item | ID |
|------|----|
| Granite Dust | `omnitech:granite_dust` |
| Andesite Dust | `omnitech:andesite_dust` |
| Diorite Dust | `omnitech:diorite_dust` |
| Stone Dust | `omnitech:stone_dust` |
| Hematite Dust | `omnitech:hematite_dust` |
| Deepslate Dust | `omnitech:deepslate_dust` |

---

## 5. Processing Chain

The full ore-to-component pipeline for any metal:

```
[Ore block]
    │  (mine)
    ▼
[raw_<metal>]  ──(Macerator)──▶  [<metal>_dust]  ──▶  (chemical processing)
    │
    │  (Smelter / Furnace)
    ▼
[<metal>_ingot]
    │
    ├──(Macerator)──────────────▶  [<metal>_dust]
    ├──(press / craft)─────────▶  [<metal>_plate]
    ├──(craft 9:1)──────────────▶  [<metal>_nugget]
    ├──(cut / lathe)────────────▶  [<metal>_rod]
    ├──(wire drawing)───────────▶  [<metal>_wire]
    │       │
    │       └──(winding)────────▶  [<metal>_coil]
    ├──(gear cut)───────────────▶  [<metal>_cog]
    └──(gear cut)───────────────▶  [<metal>_reductor]
```

## 6. Worldgen

Each metal ore spawns in the world at specific Y-ranges and biome conditions.
Worldgen JSON lives in:

```
src/main/resources/data/omnitech/worldgen/
├── configured_feature/   ← ore shape and target block
├── placed_feature/       ← Y-range, count, spread, biome filter
└── biome_modifier/       ← which biomes each ore injects into
```

Tin has biome-specific override spawning (higher density in mountains and jungle).
All other metals use the default biome modifier.

---

*See also: [machines.md](machines.md) for machines that process these materials,
[power_networks.md](power_networks.md) for the energy systems that drive them.*
