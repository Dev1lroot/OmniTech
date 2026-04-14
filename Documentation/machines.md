# OmniTech — Machine Reference

This document describes every machine added by OmniTech, its purpose, inputs,
outputs, energy requirements, and any special mechanics.

---

## Table of Contents

1. [Kinetic System](#1-kinetic-system)
2. [Thermal System](#2-thermal-system)
3. [Fluid System](#3-fluid-system)
4. [Chemical Processing](#4-chemical-processing)
5. [Electrical System](#5-electrical-system)
6. [Item Transport](#6-item-transport)

---

## 1. Kinetic System

Kinetic Force (KF) is the earliest power carrier. It is produced by hand-cranking
or water wheels and transported through pipes.

### Crank

| Property | Value |
|----------|-------|
| ID | `omnitech:crank` |
| Power produced | Variable (player-driven) |
| Output face | Any attached KF Pipe |

The player right-clicks the Crank to spin it. Each click produces a pulse of KF
that travels through connected KF Pipes to machines. Sustained cranking keeps
machines running; releasing the crank lets them wind down.

### KF Generator (Kinetic Force Generator)

| Property | Value |
|----------|-------|
| ID | `omnitech:kf_generator` |
| Input | Water current / rotation source |
| Output | KF network |

Converts environmental rotation (flowing water, wind attachments) into KF.
More flow rate = more KF per tick.

### KF Pipe

| Property | Value |
|----------|-------|
| ID | `omnitech:kf_pipe` |
| Function | KF transmission |
| Loss | None (lossless short-range) |

Connects KF sources to consumers. Pipes connect automatically when placed
adjacent. Use KF Reductors to branch and reduce speed.

### KF Reductor

| Property | Value |
|----------|-------|
| ID | `omnitech:kf_reductor` |
| Input | KF network |
| Output | Reduced-speed KF network |
| Variants | Wooden, and one per metal tier |

Steps down KF speed (increases torque). Required before attaching slow machines
to a fast crank. Different material variants have different reduction ratios.

---

## 2. Thermal System

Heat is a secondary energy carrier produced by Heaters and consumed by
smelting-class machines.

### Heater

| Property | Value |
|----------|-------|
| ID | `omnitech:heater` |
| Input | KF (kinetic force) |
| Output | Heat (propagated to adjacent blocks) |

Converts kinetic force to heat. The faster the KF input, the more heat produced
per tick. Heat propagates to directly adjacent machines.

### Boiler

| Property | Value |
|----------|-------|
| ID | `omnitech:boiler` |
| Input side | Bottom — Water (fluid) |
| Output side | Top — Steam (fluid) |
| Tank capacity | 8,000 mB each |
| Min boil temperature | 100 |
| Heat range | −500 to +500 |

Consumes water and heat to produce steam. The boiler must reach the minimum
boil temperature before conversion begins. Pipe water in from the bottom and
collect steam from the top.

### Stirling Engine

| Property | Value |
|----------|-------|
| ID | `omnitech:stirling_engine` |
| Input sides | Sides — Steam (fluid) |
| Output side | Bottom — Water (fluid, condensate) |
| Output | KF (via attached KF Pipe) |

Consumes steam, produces water (condensed) and KF. A complete Boiler →
Stirling Engine loop is the primary way to convert heat into mechanical power.

---

## 3. Fluid System

### Fluid Tank

| Property | Value |
|----------|-------|
| ID | `omnitech:fluid_tank` |
| Capacity | Configurable per recipe |

Simple storage for any fluid. Tanks stack vertically and share capacity when
connected. Right-click with a bucket to fill/drain manually.

### Pump

| Property | Value |
|----------|-------|
| ID | `omnitech:pump` |
| Input | Fluid from connected tank or world |
| Output | Fluid to connected pipe/tank |
| Power | KF |

Moves fluid through the network at a configurable rate. Required whenever fluid
must flow upward or over long distances.

### Fluid Pipe

| Property | Value |
|----------|-------|
| ID | `omnitech:fluid_pipe` |
| Function | Fluid transport |

Connects tanks, machines, and pumps. Pipes merge fluid networks automatically.
Use Valves and Sorters to control routing.

### Valve

| Property | Value |
|----------|-------|
| ID | `omnitech:valve` |
| Function | Manual flow control |

Right-click the Valve Wheel to open or close the valve. Closed valves block all
fluid flow through that pipe segment.

### Sorter

| Property | Value |
|----------|-------|
| ID | `omnitech:sorter` |
| Function | Fluid and item routing |

Routes fluids (or items) to specific destinations based on configured filters.
Useful for splitting multi-output machine products to the correct tanks.

### Fluid Collector

| Property | Value |
|----------|-------|
| ID | `omnitech:fluid_collector` |
| Function | Drains fluid blocks from the world |
| Power | KF |

Scans the block below (or in front) and extracts any fluid block (water, lava,
custom fluids) into its output tank. Used to collect atmospheric fluids or
natural liquid pools.

---

## 4. Chemical Processing

### Manual Macerator

| Property | Value |
|----------|-------|
| ID | `omnitech:manual_macerator` |
| Input | 1 item slot |
| Output | 1–2 item slots (dust/crushed ore) |
| Power | Player interaction (Crank) |

Grinds ores and materials into dust or smaller particles. Must be powered by a
Crank. Slower than the automatic version but requires no infrastructure.

### Manual Centrifuge

| Property | Value |
|----------|-------|
| ID | `omnitech:manual_centrifuge` |
| Input | 1 item or fluid slot |
| Output | Separated components |
| Power | Player interaction (Crank) |

Separates mixtures by simulated centrifugal force. Used early-game before
electrical machines are available.

### Alloy Furnace

| Property | Value |
|----------|-------|
| ID | `omnitech:alloy_furnace` |
| Input | 2 item slots |
| Output | 1 item slot |
| Power | Heat (from adjacent Heater) |

Combines two metals or materials into an alloy (e.g. Copper + Tin → Bronze,
Iron + Chromium → Steel). Recipes are JSON-defined.

### Smelter

| Property | Value |
|----------|-------|
| ID | `omnitech:smelter` |
| Input | 9 item slots |
| Output | Fluid tank — 64,000 mB (molten metal) |
| Max heat | 3,000 |
| Power | Heat |

High-capacity industrial furnace. Accepts up to 9 ore/ingot stacks and outputs
molten metal into an attached Fluid Tank. Feed the output into a Foundry to cast
ingots. Required for bulk metal production at scale.

### Foundry

| Property | Value |
|----------|-------|
| ID | `omnitech:foundry` |
| Input | Molten metal (fluid) + mold item |
| Output | Cast item (ingot, plate, rod…) |
| Power | Heat |

Pours molten metal into molds to produce finished forms. The mold item
determines the output shape.

### Solvation Machine

| Property | Value |
|----------|-------|
| ID | `omnitech:solvation_machine` |
| Input side | Front — fluid or item |
| Output side | Back — solution fluid |
| Power | KF or electrical |

Dissolves solids into solution. Used to produce reagents for electrolysis and
other chemical processes.

### Electrolysis Machine

| Property | Value |
|----------|-------|
| ID | `omnitech:electrolysis_machine` |
| Input | 8,000 mB fluid |
| Output | 3 fluid tanks — 8,000 mB each (anode product / cathode product / solution) |
| Item slots | Anode material + cathode material (2 slots) |
| Max EU | 1,600 EU |
| Process time | 100 ticks |
| Power | Electrical (EU) |

Splits a fluid into its electrochemical components. For example, water →
hydrogen (cathode) + oxygen (anode). The electrode materials (inserted as items)
influence the reaction and may be consumed over time.

**Outputs:**

| Output port | Description |
|-------------|-------------|
| Anode output | Oxidised product (e.g. Oxygen) |
| Cathode output | Reduced product (e.g. Hydrogen) |
| Solution output | Remaining electrolyte |

### Rotary Compressor

| Property | Value |
|----------|-------|
| ID | `omnitech:rotary_compressor` |
| Input side | Front — gas fluid |
| Output side | Back — compressed gas fluid |
| Power | KF |

Compresses gases (Hydrogen, Oxygen, Air, etc.) for storage or reaction.
Compressed fluids have higher energy density. Feed into a Decompressor to
release on demand.

### Decompressor

| Property | Value |
|----------|-------|
| ID | `omnitech:decompressor` |
| Input side | Front — compressed fluid |
| Output side | Back — decompressed fluid |
| Power | None (passive release) |

Releases compressed fluids at a controlled rate. Used before engines or reaction
chambers that expect atmospheric-pressure gas.

### Heat Exchanger

| Property | Value |
|----------|-------|
| ID | `omnitech:heat_exchanger` |
| Input side | Front — hot fluid |
| Output side | Back — cooled fluid + transferred heat |
| Power | None (passive) |

Transfers heat between two fluid streams without mixing them. Hot steam can pre-
heat feedwater, or waste heat can be recovered to reduce fuel consumption.

### Fractional Distiller

| Property | Value |
|----------|-------|
| ID | `omnitech:fractional_distiller` |
| Structure | Multiblock tower — 1 to 4 distiller blocks stacked vertically |
| Input | Front face of the bottom block — 16,000 mB |
| Output | Back face of every block in the tower — 8,000 mB per segment |
| Heat range | −1,000 to +1,000 |
| Power | Heat |

A vertically stacked multiblock machine. Each additional block above the base
adds one more output fraction at a different boiling-point band. The higher a
segment is in the tower, the lower the effective temperature — heavier fractions
condense at the bottom, lighter fractions at the top.

**Example 4-block tower:**

```
[Distiller 4]  ← lightest fraction (e.g. Hydrogen)
[Distiller 3]  ← medium-light fraction (e.g. Nitrogen)
[Distiller 2]  ← medium-heavy fraction (e.g. Oxygen)
[Distiller 1]  ← heaviest fraction (input on front, back output)
```

All four back faces output simultaneously when the tower is at the correct
temperature range.

---

## 5. Electrical System

Electrical energy (EU — Energy Units) is the highest-tier power carrier.
It flows through Electric Wires and is stored in Capacitors.

### Solar Panel

| Property | Value |
|----------|-------|
| ID | `omnitech:solar_panel` |
| Output | EU |
| Generation | Proportional to sky brightness |

Generates EU during daylight. Output scales with sky brightness (0 at night or
underground, peak at noon). Connect to Electric Wire to distribute power.

### Electric Capacitor

| Property | Value |
|----------|-------|
| ID | `omnitech:electric_capacitor` |
| Storage | 1,600 EU |
| I/O | All faces (via EU capability) |

Buffers electrical energy. Necessary to smooth out intermittent solar output or
to power burst-demand machines.

### Electric Wire

| Property | Value |
|----------|-------|
| ID | `omnitech:electric_wire` |
| Function | EU transmission |
| Loss | None (lossless within supported distance) |

Connects electrical machines and capacitors. Wires render as thin cables and do
not occlude adjacent blocks.

### Electric Engine

| Property | Value |
|----------|-------|
| ID | `omnitech:electric_engine` |
| Input | EU |
| Output | KF |

Converts electrical energy into kinetic force. Bridges the electrical tier back
to machines that require KF. Useful for automating crank-requiring machines.

### Electric Furnace

| Property | Value |
|----------|-------|
| ID | `omnitech:electric_furnace` |
| Input | 1 item slot |
| Output | 1 item slot |
| Power | EU |

Standard smelting with electrical power. Faster and more convenient than a
heat-powered Alloy Furnace for basic smelting tasks.

---

## 6. Item Transport

### Conveyor Belt

| Property | Value |
|----------|-------|
| ID | `omnitech:conveyor_belt` |
| Function | Horizontal and inclined item transport |
| Power | KF |

Moves items along its surface in the direction the belt faces. Belts can be
placed on slopes. Chain multiple belts for longer runs, and use Sorters at
branch points to direct items to specific machines.

---

*See also: [materials.md](materials.md) for the full metal processing reference,
[power_networks.md](power_networks.md) for energy carrier details,
[planets.md](planets.md) for the space exploration system, and
[space_map_api.md](space_map_api.md) for the JSON data format.*
