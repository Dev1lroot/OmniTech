#!/usr/bin/env python3
"""
planet_geology.py
=================
Makes the space dimensions worth mining: underground rock layers (sedimentary where
that is realistic) and a processing chain for every planet rock, regolith and ice.

  Moon    below y≈48: mare basalt (under plains) / anorthosite (under craters),
          rare KREEP pockets (K, rare earths, P, Th, U)
  Mars    plains (old lake beds): banded mudstone + gypsum strata at y≈38–66 with
          hematite concretions ("blueberries") in the mudstone
  Europa  ~9 blocks of ocean sediment under the seafloor: carbonate, evaporite patches
          (laid by the Java europa_seafloor feature — see EuropaSeafloorFeature)
  Kuiper  asteroid rock → iron, nickel, cobalt, platinum-group motes

  macerator: rock → <rock>_dust      centrifuge: dust → elements
  extractor: ices / Mars frost → water (+ salt or sulfur residue)

This module owns the Moon's material rule (data/omnitech/worldgen/material_rule/moon.json).
The Mars rule is written by mars_factory.py, which imports `mars_strata_rule()` from
here — rerun it after changing the strata below.

Textures are never overwritten.

Usage:
    python3 planet_geology.py && python3 mars_factory.py
"""

import json
from pathlib import Path

from PIL import Image

from asset_factory import tint_image
from mineral_texture_factory import render_version

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
ASSETS     = RES / "assets/omnitech"
VANILLA    = ROOT_DIR / "decompiled/minecraft-neoforge-26.3.0.1-beta/assets/minecraft/textures/block"
DUST_TMPL  = ROOT_DIR / "templates/assets/default_dust.png"
NS         = "omnitech"

MACERATOR_FORCE, CENTRIFUGE_FORCE, EXTRACTOR_FORCE = 20, 100, 3.0

# ── Rock blocks ───────────────────────────────────────────────────────────────
# name: (en, ru, texture, drops); MAP_COLORS gives each its in-game map colour
#   texture: ("vanilla.png", dark, mid, light) retint, or "mineral" (minerals.yml via mineral_texture_factory)
#   drops:   None = itself, or (dust item, min, max) with silk touch → itself (ore-like, needs stone pickaxe)
ROCKS = {
    "lunar_anorthosite":  ("Lunar Anorthosite", "Лунный анортозит",
                           ("calcite.png", "#6E6E6A", "#B8B8B2", "#ECECE6"), None),
    "lunar_mare_basalt":  ("Lunar Mare Basalt", "Лунный базальт морей",
                           ("smooth_basalt.png", "#18181A", "#3A3A3E", "#6A6A70"), None),
    "lunar_kreep":        ("KREEP Basalt", "KREEP-базальт", "mineral", ("kreep_dust", 1, 3)),
    "mars_mudstone":      ("Martian Mudstone", "Марсианский аргиллит",
                           ("packed_mud.png", "#3E2218", "#84523C", "#B8886A"), None),
    "mars_gypsum":        ("Martian Gypsum Rock", "Марсианский гипс",
                           ("calcite.png", "#8A7A68", "#D4C6B0", "#F4EEE2"), None),
    "mars_hematite_concretion": ("Hematite Concretions", "Гематитовые конкреции", "mineral",
                                 ("hematite_dust", 1, 3)),
    "europa_carbonate":   ("Europan Carbonate Sediment", "Карбонатный осадок Европы",
                           ("calcite.png", "#5E5A54", "#A29C92", "#D6D0C6"), None),
    "europa_evaporite":   ("Europan Evaporite", "Эвапорит Европы",
                           ("calcite.png", "#6A7C88", "#BFD0DA", "#F0F8FC"), None),
}

MAP_COLORS = {
    "lunar_anorthosite": "quartz", "lunar_mare_basalt": "deepslate", "lunar_kreep": "terracotta_yellow",
    "mars_mudstone": "brown", "mars_gypsum": "terracotta_white", "mars_hematite_concretion": "black",
    "europa_carbonate": "light_gray", "europa_evaporite": "quartz",
}

# minerals.yml entries for the ore-like rocks (textures via the template generator)
MINERAL_VISUALS = {
    "lunar_kreep": ("KREEP-базальт", "#3A3A40", [("#C88A3A", "cubic", "glossy"), ("#E8D8B0", None, "bright")]),
    "mars_hematite_concretion": ("Гематитовые конкреции", "#84523C",
                                 [("#2A2A30", "cubic", "metallic"), ("#6A6A74", None, "bright")]),
}

# ── Dusts ─────────────────────────────────────────────────────────────────────
# name: (formula, dark, light, en, ru)
DUSTS = {
    "mars_regolith_dust": ("Fe₂O₃·SiO₂", "#5A2414", "#E09A66", "Martian Regolith Dust", "Пыль марсианского реголита"),
    "mars_basalt_dust":   ("(Mg,Fe)₂SiO₄", "#2A1814", "#8A5A4E", "Martian Basalt Dust", "Пыль марсианского базальта"),
    "mars_mudstone_dust": ("Al₂Si₂O₅(OH)₄", "#4A2A1E", "#C0907A", "Mudstone Dust", "Аргиллитовая пыль"),
    "gypsum_dust":        ("CaSO₄·2H₂O", "#8A7C6C", "#F6F0E4", "Gypsum Dust", "Гипсовая пыль"),
    "europa_stone_dust":  ("(Mg,Fe)SiO₃", "#3A3A40", "#9A9AA4", "Europan Stone Dust", "Пыль камня Европы"),
    "dolomite_dust":      ("CaMg(CO₃)₂", "#6A665E", "#E0DAD0", "Dolomite Dust", "Доломитовая пыль"),
    "epsomite_dust":      ("MgSO₄·7H₂O", "#7A8A94", "#F4FAFC", "Epsomite Dust", "Эпсомитовая пыль"),
    "anorthosite_dust":   ("CaAl₂Si₂O₈", "#7A7A76", "#F0F0EA", "Anorthosite Dust", "Анортозитовая пыль"),
    "mare_basalt_dust":   ("FeTiO₃·SiO₂", "#1E1E22", "#76767E", "Mare Basalt Dust", "Пыль базальта морей"),
    "kreep_dust":         ("(K,REE)PO₄", "#4A3A28", "#D8B070", "KREEP Dust", "KREEP-пыль"),
    "asteroid_dust":      ("(Fe,Ni)", "#2E2A26", "#9A948C", "Asteroid Dust", "Астероидная пыль"),
}

# block → (dust, count)
MACERATE = {
    "mars_regolith": ("mars_regolith_dust", 2), "mars_frost": ("mars_regolith_dust", 2),
    "mars_stone": ("mars_basalt_dust", 2), "mars_basalt": ("mars_basalt_dust", 3),
    "mars_mudstone": ("mars_mudstone_dust", 3), "mars_gypsum": ("gypsum_dust", 3),
    "mars_hematite_concretion": ("hematite_dust", 4),
    "europa_stone": ("europa_stone_dust", 2), "europa_carbonate": ("dolomite_dust", 3),
    "europa_evaporite": ("epsomite_dust", 3), "minecraft:potent_sulfur": ("sulfur_dust", 4),
    "lunar_anorthosite": ("anorthosite_dust", 2), "lunar_mare_basalt": ("mare_basalt_dust", 2),
    "lunar_kreep": ("kreep_dust", 3),
    "asteroid_block": ("asteroid_dust", 2),
}

# dust → [(item, count, chance)]
CENTRIFUGE = {
    "mars_regolith_dust": [("stone_dust", 1, 1.0), ("iron_dust", 1, 0.5), ("magnesium_mote", 1, 0.3),
                           ("sulfur_mote", 1, 0.25), ("sodium_chlorine_dust", 1, 0.1), ("titanium_mote", 1, 0.05)],
    "mars_basalt_dust":   [("stone_dust", 1, 1.0), ("iron_dust", 1, 0.4), ("magnesium_dust", 1, 0.35),
                           ("titanium_mote", 1, 0.1), ("chromium_mote", 1, 0.06), ("nickel_mote", 1, 0.05)],
    "mars_mudstone_dust": [("stone_dust", 1, 1.0), ("aluminium_dust", 1, 0.5), ("iron_mote", 1, 0.3),
                           ("potassium_mote", 1, 0.2), ("boron_mote", 1, 0.08)],
    "gypsum_dust":        [("calcium_dust", 1, 1.0), ("sulfur_dust", 1, 1.0)],
    "europa_stone_dust":  [("stone_dust", 1, 1.0), ("magnesium_dust", 1, 0.4), ("iron_mote", 1, 0.4),
                           ("nickel_mote", 1, 0.1)],
    "dolomite_dust":      [("calcium_dust", 1, 1.0), ("magnesium_dust", 1, 1.0), ("graphite_mote", 1, 0.1)],
    "epsomite_dust":      [("magnesium_dust", 1, 1.0), ("sulfur_dust", 1, 1.0), ("sodium_chlorine_dust", 1, 0.3)],
    "anorthosite_dust":   [("calcium_dust", 1, 1.0), ("aluminium_dust", 1, 1.0), ("stone_dust", 1, 0.5)],
    "mare_basalt_dust":   [("stone_dust", 1, 1.0), ("iron_dust", 1, 0.5), ("titanium_dust", 1, 0.35),
                           ("magnesium_mote", 1, 0.2), ("chromium_mote", 1, 0.05)],
    "kreep_dust":         [("potassium_dust", 1, 1.0), ("phosphorus_dust", 1, 0.6), ("cerium_mote", 1, 0.3),
                           ("lanthanum_mote", 1, 0.25), ("neodymium_mote", 1, 0.15), ("thorium_mote", 1, 0.1),
                           ("uranium_mote", 1, 0.05)],
    "asteroid_dust":      [("iron_dust", 1, 1.0), ("nickel_dust", 1, 0.5), ("cobalt_mote", 1, 0.25),
                           ("graphite_mote", 1, 0.2), ("platinum_mote", 1, 0.06), ("iridium_mote", 1, 0.04),
                           ("palladium_mote", 1, 0.04)],
}

# block → (water mB, residue item or None)
EXTRACT_WATER = {
    "europa_ice": (1000, None), "cracked_ice": (750, None),
    "lineae_ice": (750, "sodium_chlorine_dust"), "sulfur_ice": (750, "sulfur_dust"),
    "mars_frost": (250, "mars_regolith_dust"),
}

# Mars hematite concretions: ore veins inside the mudstone
HEMATITE_VEIN, HEMATITE_COUNT = 8, 10


# ── Helpers ───────────────────────────────────────────────────────────────────

def ref(name):
    return name if ":" in name else f"{NS}:{name}"


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


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


# ── Material-rule fragments (shared with the planet factories) ────────────────

def block(b):
    return {"type": "minecraft:block", "result_state": ref(b)}


def when(cond, then):
    return {"type": "minecraft:condition", "if_true": cond, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def y_above(y, depth_mult=1):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y},
            "surface_depth_multiplier": depth_mult, "add_stone_depth": False}


def not_(cond):
    return {"type": "minecraft:not", "invert": cond}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": [ref(i) for i in ids]}


def mars_strata_rule():
    """Mars plains: flat mudstone/gypsum bands, wobbled per column by the surface-depth noise."""
    bands = seq(
        when(y_above(60), block("mars_mudstone")),
        when(y_above(57), block("mars_gypsum")),
        when(y_above(50), block("mars_mudstone")),
        when(y_above(47), block("mars_gypsum")),
        when(y_above(43), block("mars_mudstone")),
        when(y_above(41), block("mars_gypsum")),
        block("mars_mudstone"),
    )
    return when(biome_is("mars_plains"), when(not_(y_above(66)), when(y_above(38), bands)))


def moon_material_rule():
    def depth(rng):
        return {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0,
                "secondary_depth_range": rng, "surface_type": "floor"}
    rock = seq(
        when({"type": "minecraft:noise_threshold", "noise": ref("lunar_kreep"), "min_threshold": 0.62,
              "max_threshold": 9.0, "is_3d": True}, block("lunar_kreep")),
        when(biome_is("moon_plains"), block("lunar_mare_basalt")),
        block("lunar_anorthosite"),
    )
    return seq(
        when({"type": "minecraft:vertical_gradient", "random_name": ref("bedrock_floor"),
              "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 3}},
             block("minecraft:bedrock")),
        when(depth(14), block("surface_regolith")),
        when(depth(44), block("stratified_regolith")),
        when(not_(y_above(48)), rock),
        when(depth(59), block("paleoregolith")),
        block("megaregolith"),
    )


# ── Writers ───────────────────────────────────────────────────────────────────

def machine_outputs(outputs):
    return [{"item": ref(i), "count": c, "chance": ch} for i, c, ch in outputs]


def ore_loot(name, dust, lo, hi):
    return {"type": "minecraft:block", "random_sequence": ref(f"blocks/{name}"), "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": ref(name)},
            {"type": "minecraft:item", "name": ref(dust), "modifier": [
                {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}},
                {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                 "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}},
                {"type": "minecraft:explosion_decay"}]}]}]}]}


def self_loot(name):
    return {"type": "minecraft:block", "random_sequence": ref(f"blocks/{name}"), "pools": [{
        "rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": ref(name)}]}]}


def ensure_mineral_visuals():
    """Append yml entries for the ore-like rocks so mineral_texture_factory can render them."""
    import yaml
    path = SCRIPT_DIR / "minerals.yml"
    have = {m["name"] for m in yaml.safe_load(path.read_text(encoding="utf-8"))["minerals"]}
    text = ""
    for name, (comment, base, incs) in MINERAL_VISUALS.items():
        if name in have:
            continue
        text += (f'\n  - name: "{name}" # {comment}\n    visuals:\n      base_layer:\n'
                 f'        color: "{base}"\n        texture_type: "stony"\n      inclusions:\n')
        for i, (color, shape, shading) in enumerate(incs):
            text += (f'        - color: "{color}"\n          size: {{ min: 0.1, max: 0.25 }}\n'
                     f'          density: {0.35 if i == 0 else 0.12}\n          shading: "{shading}"\n')
            if shape:
                text += f'          shape: "{shape}"\n'
        text += "      effects:\n        emissive: false\n        reflectivity: 0.5\n"
    if text:
        with path.open("a", encoding="utf-8") as f:
            f.write(text)
    return {m["name"]: m for m in yaml.safe_load(path.read_text(encoding="utf-8"))["minerals"]}


def write_rocks(en, ru):
    minerals = ensure_mineral_visuals()
    tex_dir = ASSETS / "textures/block"
    ore_like = []
    for name, (en_name, ru_name, tex, drops) in ROCKS.items():
        block_json = {"strength": [1.5, 6.0], "sound": "stone", "map_color": MAP_COLORS[name]}
        if drops:
            block_json = {"ore": True, "map_color": MAP_COLORS[name]}
            ore_like.append(ref(name))
        write(DATA / f"block/{name}.json", block_json)
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": ref(f"block/{name}")}}})
        write(ASSETS / f"models/block/{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": ref(f"block/{name}")}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ref(f"block/{name}")}})
        write(DATA / f"loot_table/blocks/{name}.json", ore_loot(name, *drops) if drops else self_loot(name))
        png = tex_dir / f"{name}.png"
        if not png.exists():
            img = render_version(minerals[name], 0) if tex == "mineral" else retint(VANILLA / tex[0], *tex[1:])
            img.save(png)
        en[f"block.{NS}.{name}"] = en_name
        ru[f"block.{NS}.{name}"] = ru_name
    merge_tag("mineable/pickaxe", [ref(n) for n in ROCKS])
    merge_tag("needs_stone_tool", ore_like)


def write_dusts(en, ru):
    tex_dir = ASSETS / "textures/item"
    for name, (formula, dark, light, en_name, ru_name) in DUSTS.items():
        write(DATA / f"item/{name}.json", {"formula": formula})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": ref(f"item/{name}")}})
        write(ASSETS / f"models/item/{name}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": ref(f"item/{name}")}})
        png = tex_dir / f"{name}.png"
        if not png.exists():
            tint_image(DUST_TMPL, dark, light, 1.0, 1.0).save(png)
        en[f"item.{NS}.{name}"] = en_name
        ru[f"item.{NS}.{name}"] = ru_name

    tab_path = DATA / "creative_tab/omnitech.minerals.json"
    tab = json.loads(tab_path.read_text(encoding="utf-8"))
    for name in DUSTS:
        if ref(name) not in tab["data"]:
            tab["data"].append(ref(name))
    write(tab_path, tab)


def write_recipes():
    mr = DATA / "machine_recipe"
    for blk, (dust, count) in MACERATE.items():
        write(mr / f"manual_macerator/{blk.replace(':', '_')}.json", {
            "requiredKineticForce": MACERATOR_FORCE, "input": ref(blk),
            "output": [{"item": ref(dust), "count": count, "chance": 1.0}]})
    for dust, outputs in CENTRIFUGE.items():
        write(mr / f"manual_centrifuge/{dust}.json", {
            "requiredKineticForce": CENTRIFUGE_FORCE, "input": ref(dust), "output": machine_outputs(outputs)})
    for blk, (water, residue) in EXTRACT_WATER.items():
        recipe = {"requiredKineticForce": EXTRACTOR_FORCE,
                  "inputItem": {"item": ref(blk), "amount": 1},
                  "outputFluid": {"fluid": "minecraft:water", "amount": water}}
        if residue:
            recipe["residueItem"] = {"item": ref(residue), "amount": 1}
        write(mr / f"extractor/{blk}_melting.json", recipe)


def write_worldgen():
    wg = DATA / "worldgen"
    write(wg / "noise/lunar_kreep.json", {"base_octave": -4, "octave_count": 2, "amplitude_modifiers": [1.0, 0.5]})
    write(wg / "material_rule/moon.json", moon_material_rule())
    write(wg / "feature/mars_hematite_concretion.json", {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": HEMATITE_VEIN,
        "targets": [{"state": ref("mars_hematite_concretion"),
                     "target": {"predicate_type": "minecraft:block_match", "block": ref("mars_mudstone")}}]})
    write(wg / "placed_feature/mars_hematite_concretion.json", {
        "feature": ref("mars_hematite_concretion"),
        "placement": [
            {"type": "minecraft:count", "count": HEMATITE_COUNT},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": 38},
                                                          "max_inclusive": {"absolute": 66}}},
            {"type": "minecraft:biome"},
        ]})


def main():
    en, ru = {}, {}
    write_rocks(en, ru)
    write_dusts(en, ru)
    write_recipes()
    write_worldgen()
    update_lang("en_us.json", en)
    update_lang("ru_ru.json", ru)
    print(f"{len(ROCKS)} rocks, {len(DUSTS)} dusts, {len(MACERATE)} macerator, "
          f"{len(CENTRIFUGE)} centrifuge, {len(EXTRACT_WATER)} extractor recipes")


if __name__ == "__main__":
    main()
