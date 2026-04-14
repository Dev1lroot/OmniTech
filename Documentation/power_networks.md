# OmniTech — Power Networks

OmniTech uses five distinct energy carriers arranged in a progression from
primitive to advanced. Each tier can feed into the next, and machines from
different tiers interoperate through dedicated converter blocks.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Tier 1 — Kinetic Force (KF)](#2-tier-1--kinetic-force-kf)
3. [Tier 2 — Heat](#3-tier-2--heat)
4. [Tier 3 — Fluid Pressure](#4-tier-3--fluid-pressure)
5. [Tier 4 — Chemical Energy](#5-tier-4--chemical-energy)
6. [Tier 5 — Electrical Energy (EU)](#6-tier-5--electrical-energy-eu)
7. [Cross-Tier Conversion](#7-cross-tier-conversion)
8. [Fluids Reference](#8-fluids-reference)

---

## 1. Overview

```
[Player / Water]
      │
      ▼
  KINETIC (KF)
      │
      ├──▶  HEAT  ──▶  [Boiler]  ──▶  STEAM (fluid)
      │                                     │
      │                                     ▼
      │                            STIRLING ENGINE ──▶  KF (back to tier 1)
      │
      ├──▶  FLUID PRESSURE  ──▶  [Chemical machines]
      │
      └──▶  ELECTRICAL (EU)  ──▶  [Electric Engine]  ──▶  KF
```

Every tier is optional at its level — you can stay in kinetic and thermal for
a long time before needing electricity. However, the most powerful machines
(Electrolysis, advanced compressors, rocket fuel synthesis) require EU.

---

## 2. Tier 1 — Kinetic Force (KF)

**Carrier:** Kinetic Force pulses through KF Pipes.

| Block | ID | Role |
|-------|----|------|
| Crank | `omnitech:crank` | Primary manual source |
| KF Generator | `omnitech:kf_generator` | Environmental rotation source |
| KF Pipe | `omnitech:kf_pipe` | Lossless transmission |
| KF Reductor | `omnitech:kf_reductor` | Speed step-down (multiple material tiers) |

**How it works:**

- The player right-clicks the **Crank** to generate KF pulses. Continuous
  clicking maintains flow; releasing causes machines to wind down.
- The **KF Generator** converts flowing water or other rotation sources into
  sustained KF automatically.
- **KF Pipes** carry force to any adjacent machine that consumes KF. There is
  no distance loss for normal runs.
- **KF Reductors** step down the shaft speed when connecting a fast source to
  a slow machine (or vice-versa). Different material variants provide different
  gear ratios — wooden reductors give the gentlest step-down; tungsten reductors
  the highest ratio.

**Consumers of KF:**

Heater, Pump, Rotary Compressor, Fluid Collector, Conveyor Belt, Manual
Macerator, Manual Centrifuge, Solvation Machine, Electric Engine (in reverse).

---

## 3. Tier 2 — Heat

**Carrier:** Heat is a value propagated between directly adjacent blocks.
It has no pipe — machines must be physically touching the source.

| Block | ID | Role |
|-------|----|------|
| Heater | `omnitech:heater` | KF → Heat converter |
| Boiler | `omnitech:boiler` | Heat + Water → Steam |
| Stirling Engine | `omnitech:stirling_engine` | Steam → KF + Water |
| Heat Exchanger | `omnitech:heat_exchanger` | Transfers heat between fluid streams |

**How it works:**

- The **Heater** converts incoming KF into a heat value that raises the
  temperature of adjacent machines each tick. Faster KF = more heat.
- Machines have a heat range (min/max). Operating outside the range stops
  processing. Exceeding the max can damage or destroy the machine.
- The **Boiler** accepts heat adjacently and water through its bottom face,
  outputting steam through the top. It will not boil below 100 heat units.
- The **Stirling Engine** closes the loop by consuming steam to produce KF,
  making the Boiler + Stirling pair a self-sustaining power amplifier given
  an initial KF input.

**Heat ranges by machine:**

| Machine | Min heat | Max heat |
|---------|----------|---------|
| Boiler | 100 | 500 |
| Smelter | — | 3,000 |
| Fractional Distiller | −1,000 | 1,000 |

---

## 4. Tier 3 — Fluid Pressure

**Carrier:** Fluids transported through Fluid Pipes driven by Pumps.

| Block | ID | Role |
|-------|----|------|
| Fluid Tank | `omnitech:fluid_tank` | Storage |
| Fluid Pipe | `omnitech:fluid_pipe` | Transport |
| Pump | `omnitech:pump` | Active mover (requires KF) |
| Valve | `omnitech:valve` | Manual shutoff |
| Sorter | `omnitech:sorter` | Routing by filter |
| Fluid Collector | `omnitech:fluid_collector` | World extraction (requires KF) |

**Registered fluids:**

| Category | Fluids |
|----------|--------|
| Gases | Steam, Hydrogen, Oxygen, Hydrazine, Nitrogen, Ammonia, Chlorine, Sodium, Argon, Air, Compressed Air, Compressed Heated Air, Liquefied Air |
| Liquids | Distilled Water, Sodium Hydroxide, Molten Brass, Brine |

**How it works:**

- Fluids flow through pipes passively when there is a pressure difference, or
  actively when a **Pump** (powered by KF) is in the line.
- **Tanks** connect vertically and share capacity. Right-click with a bucket
  to manually fill or drain.
- **Valves** are opened and closed by right-clicking the Valve Wheel. Use them
  to isolate sections of a network for maintenance or routing.
- **Sorters** route fluids (and items) to specific outputs based on configured
  filter lists. Place a Sorter at a junction to split multi-product machine
  output to the correct destinations.

---

## 5. Tier 4 — Chemical Energy

This tier is defined not by a single carrier but by a class of machines that
combine KF, heat, and fluid pressure to perform chemical transformations.
The products of these machines feed directly into rocket fuel synthesis and
electrical generation.

| Machine | ID | Input | Output |
|---------|----|-------|--------|
| Manual Macerator | `omnitech:manual_macerator` | Ore / ingot | Dust |
| Manual Centrifuge | `omnitech:manual_centrifuge` | Mixture | Separated fractions |
| Alloy Furnace | `omnitech:alloy_furnace` | 2 metals | Alloy ingot |
| Smelter | `omnitech:smelter` | Up to 9 ore stacks | Molten metal (64,000 mB) |
| Foundry | `omnitech:foundry` | Molten metal + mold | Cast form |
| Solvation Machine | `omnitech:solvation_machine` | Fluid / solid | Solution |
| Electrolysis Machine | `omnitech:electrolysis_machine` | Electrolyte fluid | 3 output fluids |
| Rotary Compressor | `omnitech:rotary_compressor` | Gas | Compressed gas |
| Decompressor | `omnitech:decompressor` | Compressed gas | Gas |
| Heat Exchanger | `omnitech:heat_exchanger` | Hot fluid | Cooled fluid + heat |
| Fractional Distiller | `omnitech:fractional_distiller` | Mixed fluid | Up to 4 fractions |

See [machines.md](machines.md) for full per-machine specifications.

---

## 6. Tier 5 — Electrical Energy (EU)

**Carrier:** EU (Energy Units) transmitted through Electric Wires and buffered
in Capacitors.

| Block | ID | Role |
|-------|----|------|
| Solar Panel | `omnitech:solar_panel` | EU generation (daylight) |
| Electric Capacitor | `omnitech:electric_capacitor` | Storage (1,600 EU) |
| Electric Wire | `omnitech:electric_wire` | Lossless transmission |
| Electric Engine | `omnitech:electric_engine` | EU → KF converter |
| Electric Furnace | `omnitech:electric_furnace` | Direct electric smelting |
| Electrolysis Machine | `omnitech:electrolysis_machine` | Primary EU consumer |

**How it works:**

- **Solar Panels** generate EU proportional to sky brightness. Output is zero
  at night or underground; peak at solar noon. Multiple panels stack.
- **Capacitors** (1,600 EU each) buffer intermittent generation. They expose
  the NeoForge `IEnergyStorage` capability on all faces.
- **Electric Wires** connect panels, capacitors, and machines in any topology.
  Wires are thin and do not occlude adjacent blocks.
- **Electric Furnace** is the simplest EU consumer — faster and more convenient
  than heat-powered alternatives for basic smelting.
- **Electrolysis Machine** is the primary high-EU consumer, splitting fluids
  into chemical components required for rocket fuel and advanced materials.

---

## 7. Cross-Tier Conversion

| From | To | Converter |
|------|----|-----------|
| KF | Heat | Heater |
| Heat + Water | Steam | Boiler |
| Steam | KF + Water | Stirling Engine |
| EU | KF | Electric Engine |
| KF | EU | *(planned — Dynamo)* |

The intended progression path:

```
Crank (manual KF)
  → Heater + Boiler + Stirling (self-sustaining KF loop with amplification)
    → Chemical machines (fluid-based processing)
      → Solar Panel + Capacitor (EU generation)
        → Electrolysis (rocket fuel synthesis)
          → Space travel
```

---

## 8. Fluids Reference

Complete list of fluids registered by OmniTech:

### Gases

| Fluid ID | Description |
|----------|-------------|
| `omnitech:steam` | Produced by Boiler from water + heat |
| `omnitech:hydrogen` | Electrolysis cathode product (from water) |
| `omnitech:oxygen` | Electrolysis anode product (from water) |
| `omnitech:hydrazine` | Rocket propellant |
| `omnitech:nitrogen` | Distillation product from liquefied air |
| `omnitech:ammonia` | Chemical synthesis intermediate |
| `omnitech:chlorine` | Electrolysis product (from brine) |
| `omnitech:sodium` | Electrolysis product (from brine) |
| `omnitech:argon` | Inert gas; distillation byproduct |
| `omnitech:air` | Atmospheric gas; Fluid Collector source |
| `omnitech:compressed_air` | Compressed air (Rotary Compressor output) |
| `omnitech:compressed_heated_air` | High-energy gas for turbines |
| `omnitech:liquefied_air` | Cryogenic precursor to nitrogen/oxygen/argon |

### Liquids

| Fluid ID | Description |
|----------|-------------|
| `omnitech:distilled_water` | Purified water; required for high-purity reactions |
| `omnitech:sodium_hydroxide` | Caustic soda; chemical reagent |
| `omnitech:molten_brass` | Smelter output; cast in Foundry |
| `omnitech:brine` | Saltwater; electrolysis source for chlorine and sodium |

---

*See also: [machines.md](machines.md) for full machine specifications,
[materials.md](materials.md) for the metal processing chain.*
