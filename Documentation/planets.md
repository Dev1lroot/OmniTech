# OmniTech — Space Exploration Reference

This document covers every aspect of OmniTech's space exploration system:
dimensions, the rocket, the navigation GUI, fuel mechanics, and the sky renderer.

---

## Table of Contents

1. [Dimensions](#1-dimensions)
2. [The Rocket](#2-the-rocket)
3. [Space Navigation GUI](#3-space-navigation-gui)
4. [Fuel System](#4-fuel-system)
5. [Sky Renderer](#5-sky-renderer)
6. [Celestial Bodies](#6-celestial-bodies)
7. [Commands](#7-commands)

---

## 1. Dimensions

OmniTech currently implements two playable space dimensions:

| Dimension ID | Body | Parent | Description |
|---|---|---|---|
| `minecraft:overworld` | Earth | Sol | Starting dimension (vanilla) |
| `omnitech:moon` | Moon | Earth | Frozen, airless lunar surface |
| `omnitech:europa` | Europa | Jupiter | Ice-covered ocean moon of Jupiter |

Additional dimensions (Mars, Io, Ganymede, Titan, and exoplanet systems) are
modelled in the space map and planned for future releases.

### Europa World Generation

Europa generates three distinct geological layers:

| Block | Layer (Y) | Description |
|-------|-----------|-------------|
| `omnitech:europa_stone` | Y 3–22 | Deep rocky basement |
| `omnitech:europa_ice` | Y 103–122 | Main frozen crust |
| `omnitech:cracked_ice` | Surface | Topmost cracked ice and ice spikes |

---

## 2. The Rocket

The Rocket is the vehicle used for interplanetary travel.

| Property | Value |
|----------|-------|
| Entity type | `omnitech:rocket` |
| Max fuel | 16,000 mB |
| Fuel consumption | 10 mB per tick while thrusting |
| Thrust key | Jump (Space) |
| Fuel accepted | Hydrazine (and other configured propellants) |
| Refuelling | Right-click with a fuel bucket: +1,000 mB per bucket |

### Boarding

Right-click the Rocket to open its inventory/GUI. The player automatically
becomes a passenger and is teleported alongside the Rocket when it travels.

### Flight

Hold Jump to burn fuel and gain altitude. Once sufficient altitude is reached,
open the Space Navigation GUI (`/spacemap` or the Rocket's GUI button) to
select a destination and initiate travel.

### Travel

Travel is triggered server-side via `SpaceTravelPacket`:

1. The server validates the player is riding a Rocket.
2. The target dimension is resolved and checked for existence.
3. A safe landing position is computed at the same XZ coordinates as the Rocket,
   at the surface `Y` from the `MOTION_BLOCKING` heightmap + 1.5 blocks.
4. The Rocket (and its passenger) are teleported using `TeleportTransition`.

> **Note:** There is currently no fuel deduction on the server side during
> the teleport. Fuel is consumed during the thrust phase only.

---

## 3. Space Navigation GUI

Open with `/spacemap` (requires cheats / operator level).

The GUI displays an animated orrery — a real-time simulation of orbital motion —
at four navigation levels:

```
Galaxy  →  Star System  →  Planet  →  Moon
```

### Layout

| Region | Content |
|--------|---------|
| Top bar | Breadcrumb trail, zoom level indicator |
| Main area | Animated orbital scene with clickable bodies |
| Bottom bar | Selected destination info: name, fuel cost, distance, availability |
| Right side | Back / Close / Zoom +/− / Launch / Explore Moons buttons |

### Controls

| Action | Input |
|--------|-------|
| Select body | Left-click on any orbiting body |
| Navigate deeper | Left-click on a Galaxy or Star System |
| Go back | "< Back" button or `Escape` |
| Zoom | Scroll wheel or +/− buttons |
| Close | "X" button or `Escape` |
| Launch | "LAUNCH" button (only active when valid destination is selected) |

### Bottom Bar

When a destination is selected, the bottom bar shows:

- **Destination** — localised display name
- **Fuel** — `current mB / required mB` (green = sufficient, red = insufficient)
- **Distance** — formatted travel distance:
  - `< 1,000,000 km` → `384,400 km`
  - `< 1,000,000,000 km` → `149.60 M km`
  - `≥ 1,000,000,000 km` → `4.498 B km`
- **Availability notice** — warns if the dimension does not yet exist

### Orbital Animation

- Angular speed comes from `orbital_speed` in `space_map.json`.  
- If `orbital_speed` is `0`, a Kepler fallback formula is used:  
  `speed = ORBIT_SPEED × (SPEED_REF_R / radius)^1.5`  
  where `ORBIT_SPEED = 2π / 30,000 rad/ms` and `SPEED_REF_R = 200`.

### Localisation

Body names are resolved from the active language file using keys:

| Key pattern | Example |
|-------------|---------|
| `omnitech.space.galaxy.<id>` | `omnitech.space.galaxy.milky_way` |
| `omnitech.space.system.<id>` | `omnitech.space.system.sol` |
| `omnitech.space.body.<id>` | `omnitech.space.body.earth` |

---

## 4. Fuel System

Fuel costs are computed dynamically from real astronomical distances.

### Distance Model

```
total_km = escape_cost + interplanetary + entry_cost
```

| Component | Description |
|-----------|-------------|
| `escape_cost` | `moon.parent_distance_km` if departing from a moon; else 0 |
| `interplanetary` | `| from_star_dist_km − to_star_dist_km |` (conjunction approximation) |
| `entry_cost` | `target.parent_distance_km` if target is a moon; else 0 |

### Fuel Formula

```
fuel_mB = 560 + 39 × √(distance_km / 384,400)
```

Calibrated reference points:

| Trip | Distance | Fuel Cost |
|------|----------|-----------|
| Earth → Moon | 384,400 km | ≈ 600 mB |
| Earth → Europa | ≈ 629,400,000 km | ≈ 2,150 mB |
| Earth → Jupiter | ≈ 628,701,000 km | ≈ 2,148 mB |
| Earth → Saturn | ≈ 1,279,796,000 km | ≈ 2,825 mB |

The square-root formula gives diminishing marginal cost at extreme distances,
keeping interstellar travel within reach while still rewarding proximity.

### Fallback

If either the source or destination dimension has no km data in `space_map.json`,
the calculator returns `−1` and the GUI falls back to the deprecated `fuel_cost`
field in the JSON (kept for backwards compatibility).

---

## 5. Sky Renderer

Each OmniTech space dimension uses the unified `SpaceMapSkyboxRenderer`, registered
as `omnitech:space_sky` and referenced from the dimension type JSON:

```json
{
  "neoforge:custom_skybox": "omnitech:space_sky"
}
```

### What Is Rendered

1. **Vanilla star field** — rendered through the standard star pass.
2. **Star disc** — replaces the vanilla sun with a distance-scaled disc of the
   actual star texture.
3. **Parent planet** — if the player is on a moon, the parent planet is rendered
   prominently (e.g. Earth from the Moon, Jupiter from Europa).
4. **Distant bodies** — all other planets in the same star system rendered as
   small discs at their correct relative positions along the ecliptic.

### Depth Ordering (Z-Index)

Bodies are sorted by their distance from the viewer and rendered **farthest first**
(painter's algorithm), so closer bodies correctly occlude farther ones:

| From | Close (renders on top) | Far (renders behind) |
|------|------------------------|----------------------|
| Moon | Earth (384,400 km) | Sun (149,598,000 km) |
| Europa | Jupiter (671,100 km) | Sun (778,299,000 km) |
| Earth | Venus (≈41 M km) | Neptune (≈4,349 M km) |

This means Jupiter can transit in front of the Sun from Europa, and Earth can
occlude the Sun from the Moon, exactly as in reality.

### Scale Formulas

**Star disc:**
```
scale = clamp(30 × (starSize / 109) × (149,597,871 / viewerStarDistKm), 4, 70)
```
Baseline: Sol (size=109) at 1 AU → scale 30 (matching vanilla sun).

**Parent planet:**
```
scale = clamp(40 × parentSize × 384,400 / moon.parent_distance_km, 18, 120)
```
Baseline: Earth (size=1.0) at 384,400 km → scale 40.

**Distant planets:**
```
scale = clamp(3 × size^0.7 × (100,000,000 / orbitDeltaKm)^0.4, 1, 15)
```
Power exponents < 1 keep gas giants visible without overwhelming the screen.

### Orbital Inclination

Every body is rendered in its real inclined orbital plane. Two parameters from
`space_map.json` control this:

| Parameter | Meaning |
|-----------|---------|
| `orbital_inclination` | Degrees the orbital plane is tilted from the ecliptic |
| `ascending_node` | Longitude (°) of the ascending node — the compass direction of the tilt |

Real values are used for all Solar System bodies (Mercury 7.00°, Moon 5.14°,
Venus 3.39°, etc.). Exoplanet values are estimated.

### Texture Registration

Sky body textures are registered via the Minecraft celestials atlas.  
Source declaration: `assets/minecraft/atlases/celestials.json`

```json
{
  "sources": [
    { "type": "minecraft:directory", "source": "space", "prefix": "space/" }
  ]
}
```

All files under `assets/omnitech/textures/space/` are registered automatically
with sprite IDs of the form `omnitech:space/<subpath>`.  
Example: `textures/space/planet/earth.png` → sprite ID `omnitech:space/planet/earth`.

---

## 6. Celestial Bodies

All bodies currently modelled in `space_map.json`:

### Sol System

| Body | Type | Dimension | Distance from Sol | Notes |
|------|------|-----------|-------------------|-------|
| Mercury | Planet | — | 57,909,000 km | Inclination 7.00° |
| Venus | Planet | — | 108,209,000 km | Inclination 3.39° |
| Earth | Planet | `minecraft:overworld` | 149,598,000 km | Ecliptic reference |
| Moon | Moon of Earth | `omnitech:moon` | 384,400 km from Earth | Inclination 5.14° |
| Mars | Planet | — | 227,939,000 km | Inclination 1.85° |
| Jupiter | Planet | — | 778,299,000 km | Inclination 1.30° |
| Io | Moon of Jupiter | — | 421,700 km from Jupiter | Inclination 0.04° |
| Europa | Moon of Jupiter | `omnitech:europa` | 671,100 km from Jupiter | Inclination 0.47° |
| Ganymede | Moon of Jupiter | — | 1,070,400 km from Jupiter | Inclination 0.20° |
| Saturn | Planet | — | 1,429,394,000 km | Inclination 2.49° |
| Titan | Moon of Saturn | — | 1,221,870 km from Saturn | Inclination 0.35° |
| Uranus | Planet | — | 2,870,990,000 km | Inclination 0.77° |
| Neptune | Planet | — | 4,498,252,000 km | Inclination 1.77° |

### Tau Ceti System (Milky Way)

| Body | Type | Dimension | Distance from Tau Ceti |
|------|------|-----------|------------------------|
| Tau Ceti e | Planet | — | 82,541,000 km |
| Tau Ceti f | Planet | — | 201,957,000 km |

### Alpha Centauri System (Milky Way)

| Body | Type | Dimension | Distance from Alpha Centauri |
|------|------|-----------|------------------------------|
| Proxima b | Planet | — | 7,254,000 km |

### Galaxies

| Galaxy ID | Notes |
|-----------|-------|
| `milky_way` | Contains Sol, Tau Ceti, Alpha Centauri |
| `andromeda` | Defined, no systems yet |
| `pegasus` | Defined, no systems yet |

---

## 7. Commands

| Command | Permission | Effect |
|---------|------------|--------|
| `/spacemap` | Cheats enabled (level 2 / singleplayer cheats) | Opens the Space Navigation GUI |

---

*See also: [machines.md](machines.md) for machine documentation and
[space_map_api.md](space_map_api.md) for the JSON authoring guide.*
