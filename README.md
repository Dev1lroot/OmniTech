# OmniTech

[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)
[![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-brightgreen)](https://www.minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3.0.1--beta-orange)](https://neoforged.net)
[![Java 25](https://img.shields.io/badge/Java-25-red)](https://adoptium.net)

A global technical mod that covers every part of the game. The name comes from the Latin word _Omnis_ (all, every, each) and the English word _Technology_. Built to be a complete system for all your engineering and exploration needs.

---

## Table of Contents

- [Project Goals](#project-goals)
- [Core Features](#core-features)
- [Planned Features](#planned-features-the-roadmap)
- [Getting Started](#getting-started)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Changelog](#changelog)
- [License](#license)
- [Links](#links)

---

## Project Goals

### Real World Skills
The first goal of OmniTech is to teach you how real machines and processes work.

- **Learning by Doing** — Every pipe, wire, and menu in the mod is made to show how things work in real life. By building these systems in the game, you learn the logic behind fluid flow, power grids, and chemistry.
- **Real Processes** — We want you to understand how modern industry is built. Mastering the mod gives you a better idea of how real-world factories and labs operate.
- **Disclaimer** — Even though these processes are based on real science, some of them are dangerous. Do not try to copy these experiments at home.

### Infinite Exploration
The second goal is to make the world feel huge and never-ending.

- **Beyond the Starting Planet** — The game does not stop at the horizon. You can leave the ground and travel to different moons and planets that are fully playable.
- **Deep Space** — With star systems already in place and plans for the Andromeda and Pegasus galaxies, the map is moving from a single world to a whole universe.
- **Future Growth** — We are working on a system to generate new star systems automatically. This means you will always have new places to find.
- **Open System** — The mod is built so it is easy to change or add to. Anyone will be able to add their own planets or star systems in the future using simple files.

---

## Core Features

### Multi-Stage Energy Ecosystem
OmniTech moves away from "magic energy" boxes, introducing a sophisticated progression of interconnected power systems. Each stage requires distinct engineering logic to master:

- **Kinetic Force (KF)** — The mechanical foundation. Utilise torque and rotation through shafts and reductors to power early-game machinery.
- **Thermal Energy** — Heat management and steam power. Harness boilers and exchangers to drive heavy-duty industrial processes.
- **Fluid Pressure** — Hydraulic logistics. A system focused on pressure differentials, utilising valves and industrial piping for complex automation.
- **Chemical Processing** — The heart of refinement. Advanced electrolysis, distillation, and compression systems for high-tier material synthesis.
- **Electrical Power (EU)** — The modern pinnacle. High-voltage grids, sustainable energy harvesting, and dense storage solutions for end-game infrastructure.

### Advanced Metallurgy & Materials
The mod significantly expands the periodic table, introducing a wide array of industrial metals and alloys into world generation. Every material features a comprehensive set of processing forms — ranging from plates and wires to complex mechanical components — integrated into a deep, multi-stage manufacturing pipeline.

### Nuclear Reactors
Multi-block reactor structures with per-rod temperature simulation, coolant loops, and realistic failure modes. Overheated reactors trigger a nuclear explosion with three zones of block destruction (0–32 blocks: 100%, 32–64: 75%, 64–128: 25%). Radiation persists in world data; players within range receive Radiation I–III effects including health reduction and a noise overlay.

### Scientific Space Exploration
Space travel in OmniTech is a mathematically grounded experience driven by astronomical logic, not simple teleportation:

- **Chemical Propulsion** — Pilotable rocket systems utilising realistic propellant mechanics.
- **Orbital Navigation** — A custom hierarchical navigation interface (`/spacemap`) providing seamless transitions between moons, planets, star systems, and galaxies.
- **Astro-Physics Rendering** — A dedicated sky engine that handles orbital inclinations, relative celestial scale, and proper z-depth for total immersion.
- **The Frontier** — Explore active celestial bodies across the Sol system and beyond. The universe is built on a data-driven architecture allowing near-infinite expansion.

### Logic Machine (RISC-V Computer)
An in-game RV32I computer with ECALL-based peripheral access, GPIO MMIO at `0xF0000000`, a serial terminal, and floppy disk storage. Runs bare-metal programs or a full Linux distribution.

---

## Planned Features (The Roadmap)

### Space Exploration & Intergalactic Reach
- **Deep Space Expansion** — Mars, Io, Ganymede, and Titan with unique gravitational profiles.
- **Interstellar Travel** — Relativistic propulsion to reach Tau Ceti and Alpha Centauri.
- **Intergalactic Jumps** — Expeditions to the Andromeda and Pegasus galaxies.
- **Universal Extensibility** — Fully data-driven: add planets, star systems, or galaxies via config files and addons.
- **Server-side Fuel Validation** — Robust server-side launch deduction replacing client-side thrust consumption.

### Industrial Power & Resource Processing
- **The Oil Empire** — Full petroleum industry: crude extraction → fractional distillation → cracking → polymer production.
- **Advanced Rocketry** — Staged rockets and multi-stage orbital insertion for heavy payload delivery.

### Electronics & High-Tech Manufacturing
- **Silicon Tier** — PCBs, logic gates, and programmable logic controllers.
- **EUV Lithography** — The ultimate end-game: Extreme Ultraviolet Lithography for nanometre-scale microchip fabrication.
- **Next-Gen Automation** — Smart sorters and priority-based item pipes for high-throughput logistics.

### Global Support & Logistics
- **UN-Grade Localisation** — Full translation support for the 5 official UN languages.

> **Note:** If a feature isn't here, it's either a secret or we're still arguing about the math.

---

## Getting Started

**Requirements**

| Dependency | Version |
|------------|---------|
| Java | 25 (Temurin recommended) |
| Minecraft | 26.3 |
| NeoForge | 26.3.0.1-beta |

**Build from source**

```bash
git clone https://github.com/Dev1lroot/OmniTech.git
cd OmniTech
./gradlew build          # compile + package JAR
./gradlew runClient      # launch game client for testing
./gradlew runServer      # launch dedicated server
./gradlew runData        # regenerate models / lang files
```

The built JAR is placed in `build/libs/`. Drop it into your NeoForge `mods/` folder alongside the NeoForge installation.

---

## Documentation

| Document | Contents |
|----------|----------|
| [Installation & Build](Documentation/index.md) | Requirements, setup, project structure |
| [Machines](Documentation/machines.md) | Every machine: I/O, energy, mechanics |
| [Power Networks](Documentation/power_networks.md) | Energy carriers, cross-tier conversion, fluids |
| [Materials](Documentation/materials.md) | 12 metals, item forms, processing chain |
| [Planets & Space](Documentation/planets.md) | Dimensions, rocket, navigation GUI, sky renderer |
| [Space Map API](Documentation/space_map_api.md) | `space_map.json` authoring reference |
| [Logic Network Programming](Documentation/logic_network.md) | RISC-V bare-metal examples: GPIO, display, floppy |
| [Linux MMIO Programming](Documentation/linux_mmio.md) | GPIO and display access from Linux via `/dev/mem` |

---

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the full process — setup, commit style, PR checklist, and the file-header requirement.

For security vulnerabilities, follow [SECURITY.md](SECURITY.md) and **do not open a public issue**.

Any contributor whose pull request is merged will be credited in [CREDITS.md](CREDITS.md).

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a full history of changes by version.

---

## License

Copyright (c) 2026 **David Eichendorf** &lt;admin@dev1lroot.com&gt;

OmniTech is free software released under the **GNU General Public License v3.0 only** (`GPL-3.0-only`). You may copy, distribute, and modify it under the terms of the GPL-3.0 as published by the Free Software Foundation. See [LICENSE](LICENSE) for the full text.

In short:
- Source must remain open if you distribute the mod or a derivative.
- Modifications must be released under the same GPL-3.0-only license.
- There is no warranty.

---

## Links

- Author: [dev1lroot.com](https://dev1lroot.com) — admin@dev1lroot.com
- NeoForge: [docs.neoforged.net](https://docs.neoforged.net) · [discord.neoforged.net](https://discord.neoforged.net)
- Issues: [github.com/Dev1lroot/OmniTech/issues](https://github.com/Dev1lroot/OmniTech/issues)
