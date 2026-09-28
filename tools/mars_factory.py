#!/usr/bin/env python3
"""
mars_factory.py
===============
Generates the Mars dimension: blocks, dimension type, noise settings, terrain density
functions, material rules, two biomes, rare plains craters and snow caps, and links
the dimension to the Mars celestial body so rockets can fly there.

    province noise ── mars_plains | mars_hills   (multi_noise temperature slot)
    style noise    ── inside the hills: rolling hills | ridged mountains | flat-topped mesas

Terrain height is a 2-D field; the final density is (height - y) plus a little 3-D
roughness so cliffs aren't perfectly smooth. Everything above ~SNOW_Y gets a snow cap,
a band of frosty regolith sits just below it, and steep faces expose bare stone.

Craters reuse the Moon's `omnitech:moon_crater` carver (very rare, plains only).

Underground: vanilla cave carver below ~y62 in both biomes, dry (no aquifers, sea
level 0). Cave walls carry chunks of Martian minerals: ore blobs centred on a rock
block that touches cave air, so every chunk sits in a cave wall/floor/ceiling and is
exposed, never buried blind.

Everything here is overwritten on each run, except textures (never overwritten).

Usage:
    python3 mars_factory.py
"""

import json
from pathlib import Path

from PIL import Image

from planet_geology import mars_strata_rule

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
WG         = DATA / "worldgen"
ASSETS     = RES / "assets/omnitech"
VANILLA    = ROOT_DIR / "decompiled/minecraft-neoforge-26.3.0.1-beta/assets/minecraft/textures/block"
NS         = "omnitech"

BIOMES = [
    ("mars_plains", "Mars Plains", "Равнины Марса"),
    ("mars_hills",  "Mars Hills",  "Холмы Марса"),
]
PROVINCE_SPLIT = 0.0     # province < split → plains
BLEND = 0.05             # province half-width of the plains ↔ hills height blend

# Heights (blocks). The snow/frost lines are raised per column by the surface-depth noise
# (0..~6 blocks), so snow is patchy over y≈SNOW_Y..SNOW_Y+6 — right where the mesa tops
# (MESA_BASE + MESA_RISE = 118) sit — and solid on the mountain peaks above that.
PLAINS_Y, PLAINS_RELIEF = 70, 2.5
ROLLING_Y, ROLLING_RELIEF = 76, 14
MOUNTAIN_Y, MOUNTAIN_PEAK = 78, 95      # ridged: MOUNTAIN_Y + MOUNTAIN_PEAK * ridge (ridge ≤ 0.55)
MESA_BASE, MESA_RISE = 74, 44
SNOW_Y, FROST_Y, BASALT_Y = 116, 106, 40
SURFACE_Y = 70                          # for the celestial body (rocket landing / HUD pressure)
DENSITY_PER_BLOCK = 0.1
ROUGHNESS = 0.25                        # 3-D noise amplitude in density units (≈ ±2.5 blocks)

CRATER_CHANCE = 0.003                   # per chunk, plains only
CAVE_CHANCE, CAVE_TOP = 0.25, 62   # roomier than the Overworld: old lava tubes

# mineral chunk in cave walls: block → (attempts per chunk, blob size)
# attempts mostly miss (the origin must be rock touching cave air); measured
# 2026-09-28 at ~110 mineral blocks per chunk in total (Overworld iron ≈ 77)
CAVE_MINERALS = {
    "magnetite":   (24, 24),
    "ilmenite":    (16, 20),
    "pyrite":      (16, 20),
    "chromite":    (12, 18),
    "pentlandite": (12, 18),
    "apatite":     (10, 18),
}
MARS_ROCK = ["mars_stone", "mars_basalt", "mars_mudstone", "mars_gypsum"]
CRATER_Y = PLAINS_Y + 1

NOISES = {
    "mars_province":  (-9, [1.0, 0.5]),
    "mars_style":     (-8, [1.0, 0.5]),
    "mars_plains":    (-5, [1.0, 0.5]),
    "mars_hills":     (-6, [1.0, 0.5, 0.25]),
    "mars_mountains": (-7, [1.0, 0.5, 0.25, 0.125]),
    "mars_mesa":      (-7, [1.0, 0.5]),
    "mars_rough":     (-4, [1.0, 0.5]),
}

# name: (block json, en, ru, (vanilla source texture, dark, mid, light), mine with)
BLOCKS = {
    "mars_regolith": ({"map_color": "terracotta_orange", "strength": [0.6, 0.6], "sound": "sand",
                       "requires_tool": False},
                      "Martian Regolith", "Марсианский реголит",
                      ("red_sand.png", "#4A1C10", "#A8502C", "#E09A66"), "shovel"),
    "mars_frost":    ({"map_color": "terracotta_brown", "strength": [0.7, 0.7], "sound": "sand",
                       "requires_tool": False},
                      "Frosted Martian Regolith", "Заиндевелый марсианский реголит",
                      None, "shovel"),
    "mars_stone":    ({"map_color": "terracotta_red", "strength": [1.5, 6.0], "sound": "stone"},
                      "Martian Stone", "Марсианский камень",
                      ("stone.png", "#3A1A12", "#7A3A28", "#B8765A"), "pickaxe"),
    "mars_basalt":   ({"map_color": "terracotta_brown", "strength": [1.25, 4.2], "sound": "stone"},
                      "Martian Basalt", "Марсианский базальт",
                      ("smooth_basalt.png", "#1C1212", "#4A2C26", "#7E5248"), "pickaxe"),
}


# ── JSON helpers ──────────────────────────────────────────────────────────────

def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def ref(name):
    return f"{NS}:{name}"


def noise(name, y_scale=0.0):
    return {"type": "minecraft:noise", "noise": ref(name), "xz_scale": 1.0, "y_scale": y_scale}


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def abs_(a):
    return {"type": "minecraft:abs", "input": a}


def clamp(a, lo, hi):
    return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}


def lerp(alpha, first, second):
    return {"type": "minecraft:lerp", "alpha": alpha, "first": first, "second": second}


def step(coordinate, at, width):
    """Smooth 0 → 1 around `at` (cubic spline, flat outside [at-width, at+width])."""
    return {"type": "minecraft:spline", "spline": {"coordinate": coordinate, "points": [
        {"location": at - width, "value": 0.0, "derivative": 0.0},
        {"location": at + width, "value": 1.0, "derivative": 0.0}]}}


def gradient_y(y0, y1, v0, v1):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "to_coordinate": y1,
            "from_value": v0, "to_value": v1}


# ── Worldgen ──────────────────────────────────────────────────────────────────

def write_noises():
    for name, (octave, amps) in NOISES.items():
        write(WG / f"noise/{name}.json",
              {"base_octave": octave, "octave_count": len(amps), "amplitude_modifiers": amps})


def write_density_functions():
    df = WG / "density_function/mars"
    write(df / "province.json", noise("mars_province"))
    write(df / "style.json", noise("mars_style"))

    plains = add(PLAINS_Y, mul(PLAINS_RELIEF, noise("mars_plains")))
    rolling = add(ROLLING_Y, mul(ROLLING_RELIEF, noise("mars_hills")))
    ridge = clamp(add(0.55, mul(-1.0, abs_(noise("mars_mountains")))), 0.0, 0.55)
    mountains = add(add(MOUNTAIN_Y, mul(MOUNTAIN_PEAK, ridge)), mul(6.0, noise("mars_hills")))
    # steep sigmoid-ish ramp → flat base, cliff, flat top
    mesa = add(add(MESA_BASE, mul(MESA_RISE, clamp(mul(6.0, add(noise("mars_mesa"), -0.05)), 0.0, 1.0))),
               mul(1.5, noise("mars_plains")))
    style = ref("mars/style")
    hills = lerp(step(style, -0.15, 0.05), rolling, lerp(step(style, 0.2, 0.05), mountains, mesa))
    height = lerp(step(ref("mars/province"), PROVINCE_SPLIT, BLEND), plains, hills)
    write(df / "height.json", {"type": "minecraft:cache", "input": height})

    k = DENSITY_PER_BLOCK
    terrain = add(add(mul(k, ref("mars/height")), gradient_y(0, 256, 0.0, -k * 256)),
                  mul(ROUGHNESS, noise("mars_rough", y_scale=1.0)))
    write(df / "final_density.json", {"type": "minecraft:squeeze", "input": mul(0.64, {
        "type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8,
        "input": {"type": "minecraft:blend_density", "input": terrain}})})


def write_noise_settings():
    write(WG / "noise_settings/mars.json", {
        "aquifers_enabled": False,
        "default_block": ref("mars_stone"),
        "default_fluid": "minecraft:air",
        "disable_mob_generation": True,
        "legacy_random_source": False,
        "material_rule": ref("mars"),
        "noise": {"height": 256, "min_y": 0, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": {
            "chunk_surface_level": float(SURFACE_Y), "continents": 0.0, "depth": 0.0, "erosion": 0.0,
            "final_density": ref("mars/final_density"), "ridges": 0.0,
            "temperature": ref("mars/province"), "vegetation": 0.0,
        },
        "ore_veins_enabled": False,
        "sea_level": 0,
        "spawn_target": [],
    })


def block(b):
    return {"type": "minecraft:block", "result_state": b}


def when(cond, then):
    return {"type": "minecraft:condition", "if_true": cond, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def y_above(y, depth_mult=0):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y},
            "surface_depth_multiplier": depth_mult, "add_stone_depth": False}


def write_material_rule():
    steep = {"type": "minecraft:steep"}
    top = {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0,
           "secondary_depth_range": 0, "surface_type": "floor"}
    under = {"type": "minecraft:stone_depth", "add_surface_depth": True, "offset": 0,
             "secondary_depth_range": 0, "surface_type": "floor"}
    write(WG / "material_rule/mars.json", seq(
        when({"type": "minecraft:vertical_gradient", "random_name": ref("mars_bedrock_floor"),
              "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 3}},
             block("minecraft:bedrock")),
        when(top, seq(
            when(y_above(SNOW_Y, 1), block("minecraft:snow_block")),
            when(steep, block(ref("mars_stone"))),
            when(y_above(FROST_Y, 1), block(ref("mars_frost"))),
            block(ref("mars_regolith")),
        )),
        when(under, seq(
            when(y_above(SNOW_Y + 2, 1), block("minecraft:snow_block")),
            when(steep, block(ref("mars_stone"))),
            block(ref("mars_regolith")),
        )),
        mars_strata_rule(),   # plains: sedimentary mudstone/gypsum bands (planet_geology.py)
        when({"type": "minecraft:vertical_gradient", "random_name": ref("mars_basalt"),
              "true_at_and_below": {"absolute": BASALT_Y - 6}, "false_at_and_above": {"absolute": BASALT_Y + 2}},
             block(ref("mars_basalt"))),
        block(ref("mars_stone")),
    ))


def write_caves():
    write(WG / "carver/mars_cave.json", {
        "type": "minecraft:cave",
        "probability": CAVE_CHANCE,
        "count": {"type": "minecraft:very_biased_to_bottom", "min_inclusive": 0, "max_inclusive": 12},
        "y": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8}, "max_inclusive": {"absolute": CAVE_TOP}},
        "horizontal_radius_multiplier": {"type": "minecraft:uniform", "min_inclusive": 1.0, "max_exclusive": 2.2},
        "vertical_radius_multiplier": {"type": "minecraft:uniform", "min_inclusive": 1.0, "max_exclusive": 1.8},
        "floor_level": {"type": "minecraft:uniform", "min_inclusive": -1.0, "max_exclusive": -0.4},
        "room_vertical_radius_multiplier": {"type": "minecraft:uniform", "min_inclusive": 0.2, "max_exclusive": 0.9},
        "thickness": {"type": "minecraft:trapezoid", "min": 0.0, "max": 3.0, "plateau": 1.0},
        "weird_thickness_bias": True,
    })
    write(DATA / "tags/block/mars_rock.json", {"values": [ref(b) for b in MARS_ROCK]})

    # carved caves hold cave_air, so match the whole #air tag
    touches_cave = {"type": "minecraft:any_of", "predicates": [
        {"type": "minecraft:matching_block_tag", "tag": "minecraft:air", "offset": off}
        for off in ([0, -1, 0], [0, 1, 0], [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1])]}
    for mineral, (attempts, size) in CAVE_MINERALS.items():
        name = f"mars_cave_{mineral}"
        write(WG / f"feature/{name}.json", {
            "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": size,
            "targets": [{"state": ref(mineral),
                         "target": {"predicate_type": "minecraft:tag_match", "tag": ref("mars_rock")}}]})
        write(WG / f"placed_feature/{name}.json", {"feature": ref(name), "placement": [
            {"type": "minecraft:count", "count": attempts},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"above_bottom": 6},
                                                          "max_inclusive": {"absolute": CAVE_TOP - 2}}},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_block_tag", "tag": ref("mars_rock")}, touches_cave]}},
            {"type": "minecraft:biome"},
        ]})


def write_features():
    write(WG / "carver/mars_crater.json", {
        "type": ref("moon_crater"), "probability": CRATER_CHANCE,
        "y": {"type": "minecraft:constant", "value": {"absolute": CRATER_Y}}})
    write(WG / "feature/mars_snow_layer.json", {
        "type": "minecraft:simple_block",
        "to_place": {"type": "minecraft:randomized_int", "property": "layers",
                     "source": {"id": "minecraft:snow", "properties": {"layers": "1"}},
                     "values": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3}}})
    write(WG / "placed_feature/mars_snow_layer.json", {
        "feature": ref("mars_snow_layer"),
        "placement": [
            {"type": "minecraft:count", "count": 96},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:block_predicate_filter", "predicate": {
                "type": "minecraft:matching_blocks", "blocks": "minecraft:snow_block", "offset": [0, -1, 0]}},
            {"type": "minecraft:biome"},
        ]})


def write_biomes():
    for bid, *_ in BIOMES:
        steps = [[] for _ in range(11)]
        steps[6] = [ref(f"mars_cave_{m}") for m in CAVE_MINERALS]   # mineral chunks in cave walls
        if bid == "mars_hills":
            steps[10] = [ref("mars_snow_layer")]
        else:
            steps[6].append(ref("mars_hematite_concretion"))   # ore veins in the plains mudstone
        write(WG / f"biome/{bid}.json", {
            "attributes": {"minecraft:visual/sky_color": "#c89a6e"},
            "carvers": ([ref("mars_crater")] if bid == "mars_plains" else []) + [ref("mars_cave")],
            "downfall": 0.0,
            "effects": {"water_color": "#8a6a5a"},
            "features": steps,
            "has_precipitation": False,
            "temperature": -0.6,
        })


def write_dimension():
    write(DATA / "dimension/mars.json", {
        "type": ref("mars"),
        "generator": {"type": "minecraft:noise", "settings": ref("mars"), "biome_source": {
            "type": "minecraft:multi_noise", "biomes": [
                {"biome": ref(bid), "parameters": {
                    "continentalness": 0.0, "depth": 0.0, "erosion": 0.0, "humidity": 0.0, "offset": 0.0,
                    "temperature": [-2.0, PROVINCE_SPLIT] if i == 0 else [PROVINCE_SPLIT, 2.0],
                    "weirdness": 0.0}}
                for i, (bid, *_) in enumerate(BIOMES)]}}})
    dim_type = json.loads((DATA / "dimension_type/moon.json").read_text(encoding="utf-8"))
    dim_type["ambient_light"] = 0.05
    dim_type["attributes"]["minecraft:visual/sky_color"] = "#c89a6e"
    dim_type["attributes"]["minecraft:visual/fog_color"] = "#b8835a"
    write(DATA / "dimension_type/mars.json", dim_type)


def link_celestial_body():
    """Adds dimension / gravity and sets surface_y as text edits, so the hand-written
    file keeps its formatting (json.dumps would turn 0.0000363806 into 3.63806e-05)."""
    path = ASSETS / "celestial_bodies/mars.json"
    text = path.read_text(encoding="utf-8")
    fields = {"dimension": json.dumps(ref("mars")), "surface_y": str(SURFACE_Y), "surface_gravity": "3.721"}
    after = {"dimension": '"type"', "surface_gravity": '"surface_y"'}
    for key, value in fields.items():
        lines = text.splitlines()
        idx = next((i for i, l in enumerate(lines) if l.strip().startswith(f'"{key}"')), None)
        if idx is not None:
            indent = lines[idx][:len(lines[idx]) - len(lines[idx].lstrip())]
            comma = "," if lines[idx].rstrip().endswith(",") else ""
            lines[idx] = f'{indent}"{key}": {value}{comma}'
        else:
            anchor = next(i for i, l in enumerate(lines) if l.strip().startswith(after[key]))
            indent = lines[anchor][:len(lines[anchor]) - len(lines[anchor].lstrip())]
            if not lines[anchor].rstrip().endswith(","):
                lines[anchor] += ","
                lines.insert(anchor + 1, f'{indent}"{key}": {value}')
            else:
                lines.insert(anchor + 1, f'{indent}"{key}": {value},')
        text = "\n".join(lines) + "\n"
    json.loads(text)
    path.write_text(text, encoding="utf-8")


# ── Blocks ────────────────────────────────────────────────────────────────────

def rgb(h):
    return tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))


def retint(src: Path, dark, mid, light) -> Image.Image:
    ramp = [rgb(dark), rgb(mid), rgb(light)]
    img = Image.open(src).convert("RGBA")
    lums = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b, a in img.get_flattened_data() if a]
    lo, hi = min(lums), max(lums)
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / (hi - lo) if hi > lo else 0.5
            k, (c0, c1) = (t * 2, ramp[:2]) if t < 0.5 else ((t - 0.5) * 2, ramp[1:])
            out.putpixel((x, y), tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3)) + (a,))
    return out


def frost_texture(regolith: Image.Image) -> Image.Image:
    """Regolith with the brightest snow grains frosted over it."""
    snow = Image.open(VANILLA / "snow.png").convert("RGBA").resize(regolith.size)
    out = regolith.copy()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = out.getpixel((x, y))
            s = snow.getpixel((x, y))
            k = 0.75 if (x * 7 + y * 13) % 5 < 2 else 0.35   # frost grains vs light dusting
            out.putpixel((x, y), tuple(int(c + (sc - c) * k) for c, sc in zip((r, g, b), s[:3])) + (a,))
    return out


def update_lang(file, entries):
    path = ASSETS / f"lang/{file}"
    text = path.read_text(encoding="utf-8")
    lang = json.loads(text)
    missing = {k: v for k, v in entries.items() if k not in lang}
    if not missing:
        return
    body = text.rstrip().removesuffix("}").rstrip()
    lines = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in missing.items())
    path.write_text(f"{body},\n\n{lines}\n}}\n", encoding="utf-8")


def merge_tag(tag, values):
    path = RES / f"data/minecraft/tags/block/{tag}.json"
    cur = json.loads(path.read_text(encoding="utf-8"))["values"] if path.exists() else []
    merged = cur + [v for v in values if v not in cur]
    if merged != cur:
        write(path, {"values": merged})


def write_blocks():
    en, ru, tools = {}, {}, {"pickaxe": [], "shovel": []}
    tex_dir = ASSETS / "textures/block"
    for name, (data, en_name, ru_name, tex, tool) in BLOCKS.items():
        write(DATA / f"block/{name}.json", data)
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": ref(f"block/{name}")}}})
        write(ASSETS / f"models/block/{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": ref(f"block/{name}")}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ref(f"block/{name}")}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": ref(f"blocks/{name}"),
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": ref(name)}]}]})
        png = tex_dir / f"{name}.png"
        if tex and not png.exists():
            retint(VANILLA / tex[0], *tex[1:]).save(png)
        tools[tool].append(ref(name))
        en[f"block.{NS}.{name}"] = en_name
        ru[f"block.{NS}.{name}"] = ru_name
    frost = tex_dir / "mars_frost.png"
    if not frost.exists():
        frost_texture(Image.open(tex_dir / "mars_regolith.png").convert("RGBA")).save(frost)
    merge_tag("mineable/pickaxe", tools["pickaxe"])
    merge_tag("mineable/shovel", tools["shovel"])

    for bid, en_name, ru_name in BIOMES:
        en[f"biome.{NS}.{bid}"] = en_name
        ru[f"biome.{NS}.{bid}"] = ru_name
    update_lang("en_us.json", en)
    update_lang("ru_ru.json", ru)


def main():
    write_blocks()
    write_noises()
    write_density_functions()
    write_noise_settings()
    write_material_rule()
    write_features()
    write_caves()
    write_biomes()
    write_dimension()
    link_celestial_body()
    print(f"{len(BIOMES)} biomes, {len(NOISES)} noises, {len(BLOCKS)} blocks")


if __name__ == "__main__":
    main()
