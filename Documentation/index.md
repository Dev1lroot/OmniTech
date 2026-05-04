# OmniTech — Documentation Index

---

## Documents

| Document | Description |
|----------|-------------|
| [index.md](index.md) | This file — installation, build, project structure |
| [machines.md](machines.md) | Every machine: I/O faces, tank sizes, energy costs, mechanics |
| [power_networks.md](power_networks.md) | Energy carriers (KF, Heat, Fluid, EU), cross-tier conversion, fluids |
| [materials.md](materials.md) | All 12 metals: item forms, processing chain, worldgen |
| [planets.md](planets.md) | Dimensions, rocket, navigation GUI, sky renderer, fuel system |
| [space_map_api.md](space_map_api.md) | `space_map.json` schema and authoring guide |
| [display_programming.md](display_programming.md) | Logic VM display instructions, BLIT/VRAM reference, 10 example programs |

---

## Installation

### Requirements

| Dependency | Version |
|------------|---------|
| Minecraft  | 26.1 |
| NeoForge   | 26.1.0.17-beta or later |
| Java       | 25 |

### Player Installation

1. Install [NeoForge](https://neoforged.net) for Minecraft 26.1.
2. Drop the OmniTech `.jar` into your `mods/` folder.
3. Launch the game.

### Modpack Inclusion

OmniTech may be freely included in modpacks distributed at no cost.  
The [LICENSE](../LICENSE) and [CREDITS.md](../CREDITS.md) files must be
preserved in full alongside the mod. Asset reuse, code reuse, paid
distribution, and rebranding are prohibited — see the license for complete
terms.

---

## Building from Source

```bash
# Clone the repository
git clone <repo-url>
cd OmniTech

# Build the mod JAR
./gradlew build

# Run the game client for testing
./gradlew runClient

# Run the dedicated server
./gradlew runServer

# Regenerate data (models, lang files, worldgen JSON)
./gradlew runData

# Run game tests
./gradlew runGameTestServer

# Refresh Gradle dependency cache
./gradlew --refresh-dependencies

# Clean build artefacts
./gradlew clean
```

The compiled `.jar` is placed in `build/libs/`.

Mapping names follow the official Mojang mappings. See the
[NeoForge mapping licence](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)
for details.

---

## Project Structure

```
OmniTech/
├── LICENSE                        Proprietary source-available licence
├── CREDITS.md                     Author and contributor credits
├── README.md                      Project overview
├── Documentation/
│   ├── index.md                   This file
│   ├── machines.md                Machine reference
│   ├── planets.md                 Space exploration reference
│   └── space_map_api.md           space_map.json authoring guide
├── gradle.properties              Mod version, MC version, NeoForge version
├── build.gradle                   Gradle build configuration
└── src/
    ├── main/
    │   ├── java/com/dev1lroot/mcmods/omnitech/
    │   │   ├── OmniTech.java              Mod entry point, common registration
    │   │   ├── OmniTechClient.java        Client-only setup (renderers, commands)
    │   │   ├── OmniTechBlocks.java        Block registry
    │   │   ├── OmniTechItems.java         Item registry
    │   │   ├── OmniTechFluids.java        Fluid registry
    │   │   ├── OmniTechBlockEntities.java Block entity registry
    │   │   ├── Config.java                Mod configuration (NeoForge ModConfigSpec)
    │   │   ├── client/                    Sky renderer, custom renderers
    │   │   ├── entities/                  RocketEntity
    │   │   ├── gui/                       SpaceNavigationScreen, menus
    │   │   ├── mixin/                     Sky suppression mixin
    │   │   ├── network/                   SpaceTravelPacket, OpenRocketGuiPacket
    │   │   ├── recipes/                   Recipe managers (one per machine type)
    │   │   └── space/                     Space map POJOs, loader, calculator
    │   └── resources/
    │       ├── assets/omnitech/
    │       │   ├── space_map.json         Galaxy / system / planet / moon definitions
    │       │   ├── textures/space/        Sky textures (planets, moons, stars)
    │       │   ├── models/                Block and item models
    │       │   └── lang/                  Localisation files
    │       └── data/omnitech/
    │           ├── dimension_type/        Dimension type JSON (skybox registration)
    │           └── worldgen/              Ore placement and biome modifiers
    └── generated/resources/       Datagen output (do not edit manually)
```

---

## Configuration

The mod's runtime configuration is in `config/omnitech-common.toml` (generated on
first launch). Key options:

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `logDirtBlock` | boolean | `true` | Log dirt block on common setup (debug) |
| `magicNumber` | integer | `42` | Configuration test value |
| `itemStrings` | list | `["minecraft:iron_ingot"]` | Items logged on setup |

Mod version, Minecraft version, and NeoForge version are defined in
`gradle.properties` and injected into `META-INF/neoforge.mods.toml` via
template substitution at build time.

---

## External Resources

- NeoForge documentation: https://docs.neoforged.net
- NeoForge Discord: https://discord.neoforged.net
- Author website: https://dev1lroot.com
