# OmniTech

A NeoForge Minecraft mod for version 26.1 — technological progression from
hand-cranked stone-age machinery to interplanetary space exploration.

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

---

## License

Copyright (c) 2026 – 2126 **David Eichendorf** — All rights reserved.

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

## Credits

**Author & Project Lead:** David Eichendorf — [dev1lroot.com](https://dev1lroot.com) — admin@dev1lroot.com

See [CREDITS.md](CREDITS.md) for the full contributor list.

### Contributing & Credit Policy

Pull requests are welcome. Any contributor whose meaningful pull request is
merged into the project will be added to [CREDITS.md](CREDITS.md) with their
name, role, and (optionally) contact details. "Meaningful" means a code,
asset, documentation, or translation change that is accepted and shipped —
trivial fixes (typos, whitespace) may or may not be credited at the author's
discretion.

By submitting a pull request you agree that your contribution is licensed to
the project under the same terms as [LICENSE](LICENSE) and that the author
retains the right to accept, reject, or modify submissions.

---

## Features

### Machine Networks

Five-tier energy progression, fully interconnected:

| Tier | Carrier | Key blocks |
|------|---------|-----------|
| 1 | Kinetic Force (KF) | Crank, KF Generator, KF Pipe, KF Reductor |
| 2 | Heat | Heater, Boiler, Stirling Engine, Heat Exchanger |
| 3 | Fluid Pressure | Pump, Fluid Tank, Fluid Pipe, Valve, Sorter |
| 4 | Chemical | Smelter, Foundry, Fractional Distiller, Electrolysis, Compressor |
| 5 | Electrical (EU) | Solar Panel, Capacitor, Electric Wire, Electric Furnace |

### Materials

Twelve metal families — Tungsten, Chromium, Tin, Aluminium, Cobalt, Nickel,
Zinc, Lead, Uranium, Titanium, Steel, Brass — each with eleven item forms
(ingot, dust, plate, rod, wire, coil, cog, reductor, mote, nugget) and world
ore generation.

### Space Exploration

- Rocket entity with mB-based propellant system (Hydrazine)
- `/spacemap` command — animated four-level orbital navigation GUI
  (Galaxy → Star System → Planet → Moon)
- Dynamic fuel costs calculated from real astronomical distances (km)
- Custom sky renderer: distance-scaled star disc, orbital inclinations,
  correct z-depth ordering between bodies
- Active dimensions: **Moon** (`omnitech:moon`), **Europa** (`omnitech:europa`)
- Modelled but unplayable: Mars, Io, Ganymede, Titan, Tau Ceti system,
  Alpha Centauri / Proxima b

---

## Planned Features

- **More dimensions** — Mars, Io, Ganymede, Titan; procedural exoplanet surfaces
- **Server-side fuel validation** — fuel is currently consumed client-side
  during thrust only; proper server deduction at launch is planned
- **Dynamo** — EU generation from KF (closing the electrical feedback loop)
- **Electronics tier** — printed circuit boards, logic gates, programmable
  controllers; precursor to the EUV microchip end-game
- **EUV Lithography** — end-game machine for producing nanometre-scale chips
- **Nuclear reactor** — Uranium-fuelled EU generation with heat management
- **Automation improvements** — programmable Sorter, item pipes with priority
- **More rocket propellants** — staged rockets, multi-stage orbital insertion
- **Interstellar travel** — Tau Ceti and Alpha Centauri playable systems
- **Localisation** — full translation support beyond `en_us`

---

## Links

- Author: [dev1lroot.com](https://dev1lroot.com) — admin@dev1lroot.com
- NeoForge: [docs.neoforged.net](https://docs.neoforged.net) · [discord.neoforged.net](https://discord.neoforged.net)
