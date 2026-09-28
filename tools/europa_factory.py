#!/usr/bin/env python3
"""
europa_factory.py
=================
Generates Europa's worldgen data: five biomes over one "province" noise, per-biome
seafloor shape, per-biome surface cracks carved into the ice shell, surface/seafloor
materials, floor-anchored decoration and the crack/sulfur blocks.

    province (T) + variant (H) noise ─┬─ multi_noise biome source (temperature / humidity slots)
                                      ├─ seafloor height + relief     (nested splines)
                                      └─ crack pattern + border fade   (interval_select)

Biome layout: T splits into a rugged / plains / warm group; H splits the rugged group
into peaks | hills and the warm group into reef | sulfur vents.

    europa               stone peaks   lineae: long reddish crevasses
    europa_hills         rolling hills double ridges: raised crest flanked by twin grooves
    europa_plains        flat plains   hairline cracks of refrozen blue ice
    europa_coral_reef    reef shelf    chaos terrain: broken plates at different heights
    europa_sulfur_vents  vent field    sulfur-stained rifts, open to the ocean where two cross

Everything here is fully generated and overwritten on each run, except textures
(never overwritten, may be hand-edited).

Usage:
    python3 europa_factory.py
"""

import json
from pathlib import Path

from PIL import Image

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
WG         = DATA / "worldgen"
ASSETS     = RES / "assets/omnitech"
NS         = "omnitech"

# ── Biome layout ──────────────────────────────────────────────────────────────
# (id, en_us, ru_ru), indexed by the constants below.
BIOMES = [
    ("europa",              "Europa Stone Peaks",  "Каменные пики Европы"),
    ("europa_hills",        "Europa Hills",        "Холмы Европы"),
    ("europa_plains",       "Europa Plains",       "Равнины Европы"),
    ("europa_coral_reef",   "Europa Coral Reef",   "Коралловый риф Европы"),
    ("europa_sulfur_vents", "Europa Sulfur Vents", "Серные источники Европы"),
]
PEAKS, HILLS, PLAINS, REEF, SULFUR = range(5)
T_SPLIT = [-0.08, 0.08]   # province: rugged | plains | warm
H_SPLIT = 0.0             # variant: peaks | hills and reef | sulfur
EDGE = 0.04               # half-width of the smooth terrain transition between biomes
CRACK_FADE = 25.0         # cracks fade out within 1/CRACK_FADE noise units of a border

# Seafloor: height = base + relief * seafloor_noise (blocks)
FLOOR_BASE   = [26, 34, 27, 38, 22]
FLOOR_RELIEF = [3, 14, 1.5, 5, 7]
STONE_BELOW  = 60    # below this Y the material rules are seafloor, above it the ice shell

# Crack carving: density -= W * CRACK_DEPTH * (y-CRACK_FLOOR)/(126-CRACK_FLOOR), W in [0, 1.4].
# Carving is confined above CRACK_FLOOR so cracks never break through the shell; only the
# sulfur-vent holes use the full shell height (VENT_FLOOR).
CRACK_DEPTH = 8.0
CRACK_FLOOR = 110
VENT_FLOOR = 100

NOISES = {
    "europa_province":      (-9, [1.0, 0.5]),
    "europa_variant":       (-9, [1.0, 0.5]),
    "europa_seafloor":      (-6, [1.0, 0.5, 0.25]),
    "europa_lineae":        (-7, [1.0, 0.35]),
    "europa_lineae_cross":  (-6, [1.0, 0.35]),
    "europa_hairline":      (-6, [1.0]),
    "europa_chaos_a":       (-5, [1.0]),
    "europa_chaos_b":       (-5, [1.0]),
    "europa_chaos_plates":  (-4, [1.0]),
    "europa_floor_patches": (-4, [1.0, 0.5]),
}

# ── New blocks ────────────────────────────────────────────────────────────────
# name: (block json, en, ru, texture recipe: (source texture, dark, mid, light) or None)
ICE_TEXTURE = ASSETS / "textures/block/europa_ice.png"
BLOCKS = {
    "lineae_ice": ({"map_color": "dirt", "strength": [1.2, 1.2], "sound": "glass", "requires_tool": False},
                   "Lineae Ice", "Лёд линий", (ICE_TEXTURE, "#3A160C", "#8C4A2C", "#D8A080")),
    "sulfur_ice": ({"map_color": "sand", "strength": [1.2, 1.2], "sound": "glass", "requires_tool": False},
                   "Sulfur-Stained Ice", "Лёд с серой", (ICE_TEXTURE, "#5A4A10", "#C8A830", "#F4E890")),
}


# ── JSON helpers ──────────────────────────────────────────────────────────────

def write(path: Path, obj, overwrite=True):
    if path.exists() and not overwrite:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def ref(name):
    return f"{NS}:{name}"


def noise(name):
    return {"type": "minecraft:noise", "noise": ref(name), "xz_scale": 1.0, "y_scale": 0.0}


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def max_(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def min_(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def abs_(a):
    return {"type": "minecraft:abs", "input": a}


def clamp(a, lo, hi):
    return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}


def gradient_y(y0, y1, v0, v1):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "to_coordinate": y1,
            "from_value": v0, "to_value": v1}


def line(n, width):
    """1 on the zero-contour of noise n, falling to 0 at |n| = width."""
    return clamp(add(1.0, mul(-1.0 / width, abs_(noise(n)))), 0.0, 1.0)


def ring(n, center, width):
    """1 where |n| = center (a contour pair either side of the zero line)."""
    return clamp(add(1.0, mul(-1.0 / width, abs_(add(abs_(noise(n)), -center)))), 0.0, 1.0)


def plateau_spline(coordinate, splits, values):
    """Cubic spline that is flat at values[i] between splits, easing across each split."""
    edges = [-1.5] + splits + [1.5]
    points = []
    for i, v in enumerate(values):
        lo = edges[i] + (EDGE if i > 0 else 0)
        hi = edges[i + 1] - (EDGE if i < len(values) - 1 else 0)
        points += [{"location": lo, "value": v, "derivative": 0.0},
                   {"location": hi, "value": v, "derivative": 0.0}]
    return {"coordinate": coordinate, "points": points}


def biome_spline(values):
    """Per-biome value → smooth 2-D field over (province, variant)."""
    variant = ref("europa/variant")
    rugged = plateau_spline(variant, [H_SPLIT], [values[PEAKS], values[HILLS]])
    warm = plateau_spline(variant, [H_SPLIT], [values[REEF], values[SULFUR]])
    outer = plateau_spline(ref("europa/province"), T_SPLIT, [rugged, values[PLAINS], warm])
    return {"type": "minecraft:spline", "spline": outer}


# ── Crack patterns ────────────────────────────────────────────────────────────
# Each returns (carve W >= 0, ridge R >= 0). Material rules colour the same contours.

# Line widths (noise units) are shared with the material rules that colour the cracks.
LINEAE_W, CROSS_W, HAIR_W, CHAOS_W, RIFT_W = 0.03, 0.03, 0.03, 0.035, 0.025
RING_C, RING_W, CREST_W = 0.035, 0.012, 0.015


def pattern_lineae():
    return max_(line("europa_lineae", LINEAE_W), mul(0.8, line("europa_lineae_cross", CROSS_W))), 0.0


def pattern_double_ridge():
    return mul(0.55, ring("europa_lineae", RING_C, RING_W)), line("europa_lineae", CREST_W)


def pattern_hairline():
    return mul(0.3, max_(line("europa_hairline", HAIR_W), line("europa_lineae_cross", 0.02))), 0.0


def pattern_chaos():
    fractures = mul(0.6, max_(line("europa_chaos_a", CHAOS_W), line("europa_chaos_b", CHAOS_W)))
    plates = clamp(add(0.2, mul(0.35, {"type": "minecraft:round", "input": noise("europa_chaos_plates"),
                                        "multiple": 0.5})), 0.0, 0.55)
    return max_(fractures, plates), 0.0


def pattern_sulfur_rifts():
    return max_(mul(1.2, line("europa_lineae", RIFT_W)), mul(0.4, line("europa_hairline", HAIR_W))), 0.0


PATTERNS = [pattern_lineae, pattern_double_ridge, pattern_hairline, pattern_chaos, pattern_sulfur_rifts]


def per_biome(values):
    """Hard per-biome selection over (province, variant)."""
    def by_variant(a, b):
        return {"type": "minecraft:interval_select", "input": ref("europa/variant"),
                "thresholds": [H_SPLIT], "functions": [a, b]}
    return {"type": "minecraft:interval_select", "input": ref("europa/province"), "thresholds": T_SPLIT,
            "functions": [by_variant(values[PEAKS], values[HILLS]), values[PLAINS],
                          by_variant(values[REEF], values[SULFUR])]}


def border_fade():
    """0 on a biome border, 1 once CRACK_FADE⁻¹ away from every border."""
    province, variant = ref("europa/province"), ref("europa/variant")
    to_t = min_(abs_(add(province, -T_SPLIT[0])), abs_(add(province, -T_SPLIT[1])))
    to_h = abs_(add(variant, -H_SPLIT))
    # the variant split only exists outside the plains band
    to_h = {"type": "minecraft:range_choice", "input": province, "min_inclusive": T_SPLIT[0],
            "max_exclusive": T_SPLIT[1], "when_in_range": 1.0, "when_out_of_range": to_h}
    return clamp(mul(CRACK_FADE, min_(to_t, to_h)), 0.0, 1.0)


def vent_holes():
    """Sulfur vents only: where a rift crosses a hairline the shell opens to the ocean."""
    cross = mul(line("europa_lineae", RIFT_W * 1.6), line("europa_hairline", HAIR_W * 1.6))
    return per_biome([0.0, 0.0, 0.0, 0.0, mul(2.2, cross)])


# ── Writers ───────────────────────────────────────────────────────────────────

def write_noises():
    for name, (octave, amps) in NOISES.items():
        write(WG / f"noise/{name}.json",
              {"base_octave": octave, "octave_count": len(amps), "amplitude_modifiers": amps})


def write_density_functions():
    df = WG / "density_function/europa"
    write(df / "province.json", noise("europa_province"))
    write(df / "variant.json", noise("europa_variant"))

    floor_height = add(biome_spline(FLOOR_BASE), mul(biome_spline(FLOOR_RELIEF), noise("europa_seafloor")))
    write(df / "seafloor.json", add(mul(0.8, floor_height), gradient_y(0, 256, 0.0, -204.8)))

    shell = add(add(gradient_y(100, 106, -2.0, 2.0), gradient_y(120, 126, 0.0, -4.0)),
                mul(1.5, {"type": "minecraft:noise", "noise": ref("europa_surface"), "xz_scale": 0.8, "y_scale": 0.0}))
    write(df / "ice_shell.json", shell)

    carve = [p()[0] for p in PATTERNS]
    ridge = [p()[1] for p in PATTERNS]
    write(df / "crack_carve.json", {"type": "minecraft:cache", "input": clamp(
        mul(border_fade(), per_biome(carve)), 0.0, 1.4)})
    write(df / "crack_ridge.json", {"type": "minecraft:cache", "input": clamp(
        mul(border_fade(), per_biome(ridge)), 0.0, 1.0)})
    write(df / "vent_holes.json", {"type": "minecraft:cache", "input": clamp(
        mul(border_fade(), vent_holes()), 0.0, 2.2)})

    cracks = add(add(mul(ref("europa/crack_carve"), gradient_y(CRACK_FLOOR, 126, 0.0, -CRACK_DEPTH)),
                     # ridge bump peaks at y=116 and vanishes below 108 / above 130 (gradients clamp,
                     # so a one-sided gradient would extend the ridge down to the seafloor)
                     mul(ref("europa/crack_ridge"), min_(gradient_y(108, 116, 0.0, 6.0),
                                                         gradient_y(116, 130, 6.0, 0.0)))),
                 mul(ref("europa/vent_holes"), gradient_y(VENT_FLOOR, 126, 0.0, -CRACK_DEPTH)))
    terrain = {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8,
               "input": {"type": "minecraft:blend_density",
                         "input": max_(ref("europa/seafloor"), ref("europa/ice_shell"))}}
    write(df / "final_density.json",
          {"type": "minecraft:squeeze", "input": mul(0.64, add(terrain, cracks))})


def write_noise_settings():
    path = WG / "noise_settings/europa.json"
    settings = json.loads(path.read_text(encoding="utf-8"))
    router = settings["noise_router"]
    router["final_density"] = ref("europa/final_density")
    router["temperature"] = ref("europa/province")
    router["vegetation"] = ref("europa/variant")
    write(path, settings)


def biome_condition(*ids):
    return {"type": "minecraft:biome", "biome_is": [ref(i) for i in ids]}


def noise_cond(name, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": ref(name), "min_threshold": lo, "max_threshold": hi}


def block(b):
    return {"type": "minecraft:block", "result_state": b}


def when(cond, then):
    return {"type": "minecraft:condition", "if_true": cond, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def floor_depth(depth):
    return {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": depth,
            "secondary_depth_range": 0, "surface_type": "floor"}


def write_material_rule():
    # Seafloor surface blocks and sediments are placed by the europa_seafloor feature:
    # the material system's stone depth doesn't reset at water, so under the ice shell
    # "N blocks below the floor" can't be expressed as a material rule.
    seafloor = block(ref("europa_stone"))

    def crack_colour(biome, rules):
        return when(biome_condition(biome), seq(*rules))

    top = seq(
        crack_colour("europa", [
            when(noise_cond("europa_lineae", -LINEAE_W * .8, LINEAE_W * .8), block(ref("lineae_ice"))),
            when(noise_cond("europa_lineae_cross", -CROSS_W * .7, CROSS_W * .7), block(ref("lineae_ice"))),
        ]),
        crack_colour("europa_hills", [
            when(noise_cond("europa_lineae", -CREST_W * .7, CREST_W * .7), block(ref("europa_ice"))),
            when(noise_cond("europa_lineae", -(RING_C + RING_W), RING_C + RING_W), block(ref("lineae_ice"))),
        ]),
        crack_colour("europa_plains", [
            when(noise_cond("europa_hairline", -HAIR_W * .6, HAIR_W * .6), block("minecraft:blue_ice")),
            when(noise_cond("europa_lineae_cross", -0.012, 0.012), block("minecraft:blue_ice")),
        ]),
        crack_colour("europa_coral_reef", [
            when(noise_cond("europa_chaos_a", -CHAOS_W * .7, CHAOS_W * .7), block("minecraft:packed_ice")),
            when(noise_cond("europa_chaos_b", -CHAOS_W * .7, CHAOS_W * .7), block("minecraft:packed_ice")),
            when(noise_cond("europa_chaos_plates", 0.5, 9), block(ref("lineae_ice"))),
        ]),
        crack_colour("europa_sulfur_vents", [
            when(noise_cond("europa_lineae", -RIFT_W * .8, RIFT_W * .8), block(ref("sulfur_ice"))),
            when(noise_cond("europa_lineae", -RIFT_W * 2.5, RIFT_W * 2.5), block(ref("lineae_ice"))),
            when(noise_cond("europa_hairline", -HAIR_W * .6, HAIR_W * .6), block(ref("sulfur_ice"))),
        ]),
        block(ref("cracked_ice")),
    )

    rule = seq(
        when({"type": "minecraft:vertical_gradient", "random_name": ref("europa_bedrock_floor"),
              "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 3}},
             block("minecraft:bedrock")),
        when({"type": "minecraft:not", "invert": {"type": "minecraft:y_above", "anchor": {"absolute": STONE_BELOW},
                                                  "surface_depth_multiplier": 0, "add_stone_depth": False}},
             seafloor),
        when(floor_depth(0), top),
        when({"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0,
              "secondary_depth_range": 18, "surface_type": "floor"}, block(ref("europa_ice"))),
        block(ref("europa_ice")),
    )
    write(WG / "material_rule/europa.json", rule)


def floor_placed(feature, count, rarity=None):
    """Placed on the seafloor under the ice shell: start somewhere in the water column and scan
    down (max 32 steps, the codec limit) to solid ground; about half the tries reach the floor,
    so counts are doubled."""
    placement = []
    if rarity:
        placement.append({"type": "minecraft:rarity_filter", "chance": rarity})
    if count:
        placement.append({"type": "minecraft:count", "count": count * 2})
    placement += [
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": 20},
                                                      "max_inclusive": {"absolute": 80}}},
        {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 32,
         "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"},
         "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0},
        {"type": "minecraft:biome"},
    ]
    return {"feature": feature, "placement": placement}


def write_features():
    pf = WG / "placed_feature"
    write(pf / "europa_coral.json", floor_placed("minecraft:warm_ocean_vegetation", 14))
    write(pf / "europa_seagrass.json", floor_placed("minecraft:seagrass_mid", 28))
    write(pf / "europa_seagrass_sparse.json", floor_placed("minecraft:seagrass_short", 6))
    write(pf / "europa_sea_pickle.json", floor_placed("minecraft:sea_pickle", 1, rarity=3))
    write(pf / "europa_kelp.json", floor_placed("minecraft:kelp", 18))
    write(WG / "feature/europa_sulfur_chimney.json", {"type": ref("europa_sulfur_chimney")})
    write(WG / "feature/europa_seafloor.json", {"type": ref("europa_seafloor")})
    write(pf / "europa_seafloor.json", {"feature": ref("europa_seafloor"), "placement": []})
    write(pf / "europa_sulfur_chimney.json", floor_placed(ref("europa_sulfur_chimney"), 2))
    trench = json.loads((pf / "europa_trench.json").read_text(encoding="utf-8"))
    trench["placement"][0]["chance"] = 25
    write(pf / "europa_vent_trench.json", trench)


SPAWN_EEL = {"type": ref("abyssal_eel"), "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3},
             "weight": 10}
BIOME_LOOK = {
    # water colour, water fog, step 4 (local), step 9 (vegetation), step 7 (trenches), extra water_ambient spawns
    "europa":              ("#99c2ff", "#334daa", ["ice_spike", "europa_stone_spire"], [], ["europa_trench"], []),
    "europa_hills":        ("#7fb0f0", "#2d4494", ["ice_spike"], ["europa_kelp", "europa_seagrass_sparse"], [], []),
    "europa_plains":       ("#a8ccff", "#3a58b0", ["ice_spike"], ["europa_seagrass"], [], []),
    "europa_coral_reef":   ("#5fd0e0", "#1f6a88", ["ice_spike"],
                            ["europa_coral", "europa_seagrass_sparse", "europa_sea_pickle"], [],
                            [{"type": "minecraft:tropical_fish", "count": 8, "weight": 25}]),
    "europa_sulfur_vents": ("#b8c27a", "#4a5a2a", ["europa_sulfur_chimney"], [], ["europa_vent_trench"], []),
}


def write_biomes():
    for bid, *_ in BIOMES:
        water, fog, local, veg, trench, ambient = BIOME_LOOK[bid]
        steps = [[] for _ in range(11)]
        steps[0] = [ref("europa_seafloor")]
        steps[4] = [ref(f) for f in local]
        steps[7] = [ref(f) for f in trench]
        steps[9] = [ref(f) for f in veg]
        spawns = {"water_creature": [SPAWN_EEL]}
        if ambient:
            spawns["water_ambient"] = ambient
        write(WG / f"biome/{bid}.json", {
            "attributes": {
                "minecraft:visual/sky_color": "#1a3a8f",
                "minecraft:visual/water_fog_color": fog,
                "minecraft:gameplay/natural_mob_spawns": {
                    "modifier": "overlay",
                    "argument": {"spawn_costs": {}, "spawns_by_category": spawns},
                },
            },
            "carvers": [],
            "downfall": 0.0,
            "effects": {"water_color": water},
            "features": steps,
            "has_precipitation": False,
            "temperature": -1.0,
        })


def write_dimension():
    lo, hi = T_SPLIT
    ranges = {  # biome → (temperature range, humidity range)
        PEAKS: ([-2.0, lo], [-2.0, H_SPLIT]), HILLS: ([-2.0, lo], [H_SPLIT, 2.0]),
        PLAINS: ([lo, hi], [-2.0, 2.0]),
        REEF: ([hi, 2.0], [-2.0, H_SPLIT]), SULFUR: ([hi, 2.0], [H_SPLIT, 2.0]),
    }
    entries = []
    for i, (bid, *_) in enumerate(BIOMES):
        t, h = ranges[i]
        entries.append({"biome": ref(bid), "parameters": {
            "continentalness": 0.0, "depth": 0.0, "erosion": 0.0, "humidity": h, "offset": 0.0,
            "temperature": t, "weirdness": 0.0}})
    write(DATA / "dimension/europa.json", {
        "type": ref("europa"),
        "generator": {"type": "minecraft:noise",
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": entries},
                      "settings": ref("europa")},
    })


def retint(src: Path, dark, mid, light) -> Image.Image:
    def rgb(h):
        return tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
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
    en, ru = {}, {}
    for name, (data, en_name, ru_name, tex) in BLOCKS.items():
        write(DATA / f"block/{name}.json", data)
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": ref(f"block/{name}")}}})
        write(ASSETS / f"models/block/{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": ref(f"block/{name}")}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ref(f"block/{name}")}})
        png = ASSETS / f"textures/block/{name}.png"
        if tex and not png.exists():
            retint(*tex).save(png)
        en[f"block.{NS}.{name}"] = en_name
        ru[f"block.{NS}.{name}"] = ru_name

    for ice in ("lineae_ice", "sulfur_ice"):
        write(DATA / f"loot_table/blocks/{ice}.json", {
            "type": "minecraft:block", "random_sequence": ref(f"blocks/{ice}"),
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": ref(ice)}]}]})
    merge_tag("mineable/pickaxe", [ref(n) for n in BLOCKS])

    for bid, en_name, ru_name in BIOMES:
        en[f"biome.{NS}.{bid}"] = en_name
        ru[f"biome.{NS}.{bid}"] = ru_name
    update_lang("en_us.json", en)
    update_lang("ru_ru.json", ru)


def main():
    write_noises()
    write_density_functions()
    write_noise_settings()
    write_material_rule()
    write_features()
    write_biomes()
    write_dimension()
    write_blocks()
    print(f"{len(BIOMES)} biomes, {len(NOISES)} noises, {len(BLOCKS)} blocks")


if __name__ == "__main__":
    main()
