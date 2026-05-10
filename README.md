# OmniTech

A global technical mod that covers every part of the game. The name comes from latin word: _Omnis_ (all, every, each) and english word: _Technology_. It is built to be a complete system for all your engineering and exploration needs.

---

## Project Goals

### Real World Skills
The first goal of OmniTech is to teach you how real machines and processes work.

- **Learning by Doing**: Every pipe, wire, and menu in the mod is made to show how things work in real life. By building these systems in the game, you learn the logic behind fluid flow, power grids, and chemistry.
- **Real Processes**: We want you to understand how modern industry is built. Mastering the mod gives you a better idea of how real-world factories and labs operate.
- **Disclaimer**: Even though these processes are based on real science, some of them are dangerous. Do not try to copy these experiments at home.

### Infinite Exploration
The second goal is to make the world feel huge and never-ending.

- **Beyond the Starting Planet**: The game does not stop at the horizon. You can leave the ground and travel to different moons and planets that are fully playable.
- **Deep Space**: With star systems already in place and plans for the Andromeda and Pegasus galaxies, the map is moving from a single world to a whole universe.
- **Future Growth**: We are working on a system to generate new star systems automatically. This means you will always have new places to find.
- **Open System**: The mod is built so it is easy to change or add to. Anyone will be able to add their own planets or star systems in the future using simple files.

---

## Core Features

### Multi-Stage Energy Ecosystem
OmniTech moves away from "magic energy" boxes, introducing a sophisticated progression of interconnected power systems. Each stage requires distinct engineering logic to master:

* **Kinetic Force (KF)** - The mechanical foundation. Utilize torque and rotation through shafts and reductors to power early-game machinery.
* **Thermal Energy** - Heat management and steam power. Harness boilers and exchangers to drive heavy-duty industrial processes.
* **Fluid Pressure** - Hydraulic logistics. A system focused on pressure differentials, utilizing valves and industrial piping for complex automation.
* **Chemical Processing** - The heart of refinement. Advanced electrolysis, distillation, and compression systems for high-tier material synthesis.
* **Electrical Power (EU)** - The modern pinnacle. High-voltage grids, sustainable energy harvesting, and dense storage solutions for end-game infrastructure.

---

### Advanced Metallurgy & Materials
The mod significantly expands the periodic table, introducing a wide array of industrial metals and alloys into world generation. Every material features a comprehensive set of processing forms-ranging from plates and wires to complex mechanical components-integrated into a deep, multi-stage manufacturing pipeline.

---

### Scientific Space Exploration
Space travel in OmniTech is a mathematically grounded experience, driven by astronomical logic rather than simple teleportation:

* **Chemical Propulsion** - Pilotable rocket systems utilizing realistic propellant mechanics.
* **Orbital Navigation** - A custom, hierarchical navigation interface (`/spacemap`) providing seamless transitions between moons, planets, star systems, and galaxies.
* **Astro-Physics Rendering** - A dedicated sky engine that handles orbital inclinations, relative celestial scale, and proper z-depth for total immersion.
* **The Frontier** - Explore active celestial bodies across the Sol system and beyond. The universe is built on a data-driven architecture, allowing for near-infinite expansion of star systems and reachable dimensions.

---

## Planned Features (The Roadmap)

Development is an ongoing battle against technical debt. The following systems are currently in the crosshairs:

### Space Exploration & Intergalactic Reach
* **Deep Space Expansion** - Deployment of Mars, Io, Ganymede, and Titan with unique gravitational profiles.
* **Interstellar Travel** - Relativistic propulsion to reach the Tau Ceti and Alpha Centauri systems.
* **Intergalactic Jumps** - Massive-scale expeditions to the **Andromeda** and **Pegasus** galaxies.
* **Universal Extensibility** - A fully data-driven system. The architecture is being built so anyone can add their own planets, star systems, or entire galaxies via simple configuration files and addons.
* **Server-side Fuel Validation** - Migration from client-side thrust consumption to a robust server-side validation and launch deduction system.

### Industrial Power & Resource Processing
* **The Oil Empire** - Full petroleum industry: from crude oil extraction to fractional distillation, cracking, and polymer production.
* **Nuclear Infrastructure** - Uranium-fuelled reactors with complex heat management, cooling cycles, and radioactive waste disposal.
* **Advanced Rocketry** - Staged rockets and multi-stage orbital insertion mechanics for heavy payload delivery.

### Electronics & High-Tech Manufacturing
* **Silicon Tier** - Production of Printed Circuit Boards (PCBs), logic gates, and programmable logic controllers (PLCs).
* **EUV Lithography** - The ultimate end-game: Extreme Ultraviolet Lithography for nanometre-scale microchip fabrication.
* **Next-Gen Automation** - Smart Sorters and priority-based item pipes for high-throughput logistics.

### Global Support & Logistics
* **UN-Grade Localization** - Full translation support for 5 official UN languages, ensuring the mod speaks more than just `en_us`.

---

> **Note:** If a feature isn't here, it's either a secret or we're still arguing about the math.

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


## License

Copyright (c) 2026 – 2126 **David Eichendorf** - All rights reserved.

OmniTech is distributed under a proprietary source-available license.
The full terms are in [LICENSE](LICENSE). Key points:

- **Modpacks:** Free inclusion is permitted in modpacks distributed at no cost.
- **Integration patches:** Minor compatibility changes for modpack use are
  allowed; the patch source must be made public within 30 days.
- **Prohibited:** Asset reuse, code reuse, paid distribution, rebranding
  (mod ID / display name / Java package), closed-source forks.
- **Retroactive:** The license covers the entire commit history. The repository
  was private and unreleased before 2026-04-14.

---

## Contributing & Credit Policy

Pull requests are welcome. Any contributor whose meaningful pull request is
merged into the project will be added to [CREDITS.md](CREDITS.md) with their
name, role, and (optionally) contact details. "Meaningful" means a code,
asset, documentation, or translation change that is accepted and shipped -
trivial fixes (typos, whitespace) may or may not be credited at the author's
discretion.

By submitting a pull request you agree that your contribution is licensed to
the project under the same terms as [LICENSE](LICENSE) and that the author
retains the right to accept, reject, or modify submissions.

---

## Links

- Author: [dev1lroot.com](https://dev1lroot.com) - admin@dev1lroot.com
- NeoForge: [docs.neoforged.net](https://docs.neoforged.net) · [discord.neoforged.net](https://discord.neoforged.net)
