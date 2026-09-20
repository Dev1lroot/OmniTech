# OmniTech Roadmap

A live node-graph of every item, fluid, and recipe in the mod (plus one hop
into vanilla Minecraft for anything the mod references) — so you can trace a
resource's whole production cycle both directions: what makes it, and
everything it feeds into. `dust → ingot ↔ block`, `ore → dust → element →
enriched fuel → …`, all of it.

It reads the mod's actual data files directly off disk (nothing hand-maintained
to go stale) and rebuilds automatically whenever they change, so adding an
item/recipe/fluid in the game project shows up in the graph within a second or
two, no restart.

## Run it

```bash
cd roadmap
npm install
npm run dev
```

Then open **http://localhost:4100**.

By default it assumes it's sitting in `<mod root>/roadmap` and reads
`../src/main/resources`. To point it at a different location or mod ID, set
env vars (a `.env` file works too, same as `helpers/mcviewer`):

```bash
PROJECT_PATH=/path/to/OmniTech MODID=omnitech PORT=4100 npm run dev
```

## What it parses

- **Items & blocks** — every `data/<modid>/item/*.json` and `block/*.json`
  (blocks show up as their item form, since that's how every recipe schema
  actually references them).
- **Fluids** — every `data/<modid>/fluid/*.json`.
- **Recipes** — vanilla-style crafting/smelting under `data/<modid>/recipe/**`,
  `data/<modid>/assembler/*.json`, and every machine type under
  `data/<modid>/machine_recipe/<type>/*.json` (alloy_furnace, manual_macerator,
  manual_centrifuge, smelting, foundry, chemical_reactor, electrolysis,
  fractional_distillation, solvation, chemical_infuser, coking, extractor,
  fluid_collector). A machine type the parser doesn't recognize yet still
  shows up as an unconnected node instead of silently vanishing, so new
  machine types are visible immediately.
- **One hop into vanilla** — for every vanilla item the mod graph touches
  (e.g. `minecraft:redstone_block` in the nuclear bomb recipe), it looks for
  `data/minecraft/recipe/<name>.json` in the decompiled vanilla sources
  (`decompiled/minecraft-neoforge-*/`, already extracted elsewhere in this
  repo) and adds that recipe too, so the graph doesn't just dead-end at the
  mod's own boundary.

Tag ingredients (`{"tag": "minecraft:planks"}`) are deliberately **not**
expanded to every matching item — a handful of very common tags would explode
the graph. They're just skipped for that ingredient slot.

## Reading the graph

- **Circles** = items/fluids (their real in-game icon, sampled from the mod's
  own generated textures — or the decompiled vanilla ones for vanilla items).
  Dashed border = vanilla, solid = mod.
- **Diamonds** = recipes — a process node, not a thing you hold. Colour is
  hashed from the machine type, so `chemical_reactor` is always the same
  colour, `solvation` another, etc. — no hardcoded palette to keep updating.
- **Edges**: blue = input, green = output, dashed green = byproduct (a
  `manual_centrifuge`/`extractor` result with `chance < 1`), dotted orange =
  catalyst/template (a durability item like an electrode or foundry template,
  not stoichiometrically consumed).

On load (and whenever "Show entire graph" is clicked) everything is arranged
in a plain **circle** — a fast overview with no single focus. Search for an
item, or click any item/fluid node, and it re-centers as a **concentric**
layout instead: the clicked/searched node sits in the middle, and everything
else rings around it by BFS distance — closer nodes (direct inputs/outputs)
land on the inner rings, farther ones further out. That's "arrange around it":
click any node in the result to make *that* the new center and keep walking
the chain outward in either direction. The hop slider controls how many rings
get pulled in before it draws.

## Files

- `graph.js` — pure data layer: walks the resource directories, parses every
  recipe schema, returns `{ nodes, edges, stats }`. No Express/HTTP in here,
  so it's easy to run standalone or extend with a new recipe type.
- `server.js` — Express server: serves the graph + textures, watches the data
  directories with `chokidar`, and pushes a reload event over
  Server-Sent Events (`/api/events`) on every change.
- `public/` — the frontend: plain HTML/CSS/JS + Cytoscape.js (loaded from CDN,
  no build step).
