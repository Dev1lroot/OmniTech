#!/usr/bin/env python3
"""
gem_factory.py
==============
Crystallized mineral gems + mineral block drop tables.

  • Gem items: data JSON, retinted-emerald texture, item model/reference, lang.
  • Missing mineral dusts (items + tinted default_dust texture).
  • Loot tables for every mineral block:
      silk touch  → the block itself
      otherwise   → 1-3 <mineral>_dust (fortune raises the count, capped at 3)
                    + per-gem rare roll (GEM_CHANCES, indexed by fortune level)
  • Loot tables for metal ores (METAL_ORES, e.g. tin_ore, native_platinum):
      silk touch  → the ore block
      otherwise   → raw_<metal> (vanilla ore_drops fortune bonus, like iron)
    plus raw_<metal> ⇄ raw_<metal>_block crafting recipes.
  • Adds the new items to the minerals creative tab.

Rules:
  • PNG files are NEVER overwritten (texture may have been hand-edited).
  • Loot tables of listed minerals ARE overwritten (they're fully generated).

Usage:
    python3 gem_factory.py
"""

import glob
import json
from pathlib import Path
from PIL import Image

from asset_factory import tint_image

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
ASSETS     = RES / "assets/omnitech"
EMERALD    = ROOT_DIR / "decompiled/minecraft-neoforge-26.3.0.1-beta/assets/minecraft/textures/item/emerald.png"
DUST_TMPL  = ROOT_DIR / "templates/assets/default_dust.png"

# name: (formula, colour, en_us, ru_ru)
GEMS = {
    "ruby":       ("Al\u2082O\u2083:Cr",           "#D0103A", "Ruby",       "Рубин"),
    "sapphire":   ("Al\u2082O\u2083:Fe,Ti",        "#1F4FD8", "Sapphire",   "Сапфир"),
    "chrysolite": ("(Mg,Fe)\u2082SiO\u2084",       "#A8C83A", "Chrysolite", "Хризолит"),
    "topaz":      ("Al\u2082SiO\u2084(F,OH)\u2082", "#F2A93B", "Topaz",      "Топаз"),
    "aquamarine": ("Be\u2083Al\u2082Si\u2086O\u2081\u2088:Fe", "#6FD6E0", "Aquamarine", "Аквамарин"),
    "hyacinth":   ("ZrSiO\u2084",                  "#B5462A", "Hyacinth",   "Гиацинт"),
    "garnet":     ("Fe\u2083Al\u2082(SiO\u2084)\u2083", "#7A1426", "Garnet", "Гранат"),
    "tourmaline": ("Na(Li,Al)\u2083Al\u2086(BO\u2083)\u2083Si\u2086O\u2081\u2088(OH)\u2084", "#E0609A", "Tourmaline", "Турмалин"),
}

# Minerals that had no dust item yet: name: (formula, dark, light, en_us, ru_ru)
NEW_DUSTS = {
    "baddeleyite": ("ZrO\u2082",                         "#6E5E48", "#C9B99A", "Baddeleyite Dust", "Бадделеитовая пыль"),
    "borax":       ("Na\u2082B\u2084O\u2087\u00b710H\u2082O", "#A8A89E", "#F4F4EE", "Borax Dust",       "Пыль буры"),
    "cinnabar":    ("HgS",                               "#6A1512", "#E0473A", "Cinnabar Dust",    "Киноварная пыль"),
    "pyrite":      ("FeS\u2082",                        "#6B5A1E", "#E0C860", "Pyrite Dust",      "Пиритовая пыль"),
    "cryolite":    ("Na\u2083AlF\u2086",               "#8FA4AC", "#F0F6F8", "Cryolite Dust",    "Криолитовая пыль"),
    "stibnite":    ("Sb\u2082S\u2083",                 "#3A3D44", "#9AA0AA", "Stibnite Dust",    "Антимонитовая пыль"),
    "apatite":     ("Ca\u2085(PO\u2084)\u2083F",      "#2E6A5E", "#8FD3C3", "Apatite Dust",     "Апатитовая пыль"),
    "sperrylite":  ("PtAs\u2082",                       "#4D5156", "#C9CED3", "Sperrylite Dust",  "Сперрилитовая пыль"),
}

# Mineral blocks registered by this script (assets come from mineral_assets_creator.py): name → en_us
NEW_MINERAL_BLOCKS = {
    "wolframite": "Wolframite", "cobaltite": "Cobaltite", "tantalite": "Tantalite",
    "chalcopyrite": "Chalcopyrite", "stannite": "Stannite", "native_silver": "Native Silver",
    "magnesite": "Magnesite", "zircon": "Zircon", "pyrite": "Pyrite", "cryolite": "Cryolite",
    "stibnite": "Stibnite", "apatite": "Apatite", "graphite": "Graphite", "sperrylite": "Sperrylite",
}

# mineral block → (gems it can yield, pickaxe tier needed)
# gem ids without namespace are omnitech:, others as-is
MINERALS = {
    "apatite":         ([],                                  "stone"),
    "baddeleyite":     (["hyacinth"],                        "diamond"),
    "barite":          ([],                                  "stone"),
    "bastnasite":      ([],                                  "iron"),
    "bauxite":         (["ruby", "sapphire"],                "stone"),
    "beryl":           (["aquamarine", "minecraft:emerald"], "iron"),
    "bismuthinite":    ([],                                  "iron"),
    "borax":           ([],                                  "stone"),
    "carnotite":       ([],                                  "stone"),
    "celestine":       ([],                                  "stone"),
    "chalcopyrite":    ([],                                  "stone"),
    "chromite":        (["ruby", "chrysolite"],              "iron"),
    "cinnabar":        ([],                                  "stone"),
    "cobaltite":       ([],                                  "iron"),
    "columbite":       ([],                                  "iron"),
    "cryolite":        ([],                                  "stone"),
    "fluorite":        (["topaz"],                           "stone"),
    "galena":          ([],                                  "stone"),
    "graphite":        ([],                                  "stone"),
    "halite":          ([],                                  "stone"),
    "ilmenite":        (["sapphire", "garnet"],              "iron"),
    "lepidolite":      (["tourmaline", "topaz"],             "iron"),
    "magnesite":       ([],                                  "stone"),
    "molybdenite":     ([],                                  "iron"),
    "monazite":        (["hyacinth"],                        "iron"),
    "native_silver":   ([],                                  "iron"),
    "pentlandite":     (["chrysolite"],                      "iron"),
    "pyrite":          ([],                                  "stone"),
    "pyrolusite":      ([],                                  "iron"),
    "skutterudite":    ([],                                  "iron"),
    "sperrylite":      ([],                                  "diamond"),
    "sphalerite":      (["garnet"],                          "stone"),
    "stannite":        ([],                                  "iron"),
    "stibnite":        ([],                                  "stone"),
    "tantalite":       ([],                                  "diamond"),
    "uraninite":       ([],                                  "diamond"),
    "vanadinite":      ([],                                  "iron"),
    "wolframite":      ([],                                  "iron"),
    "xenotime":        (["hyacinth"],                        "diamond"),
    "zircon":          (["hyacinth"],                        "iron"),
}

GEM_CHANCES         = [0.02, 0.03, 0.04, 0.05]   # fortune 0, I, II, III+
MAX_DUST            = 3

# metal → (its ore block, pickaxe tier); the ore drops raw_<metal>
METAL_ORES = {
    "tin":      ("tin_ore",         "stone"),
    "chromium": ("chromium_ore",    "iron"),
    "tungsten": ("tungsten_ore",    "diamond"),
    "platinum": ("native_platinum", "diamond"),
}

# Ore spawn configs written when a mineral has none (never overwritten).
# (biomes, min_y, max_y, vein_size, min_count, max_count) + optional overrides.
# Rare-metal minerals only generate in mountains.
MOUNTAIN = "#minecraft:is_mountain"
OVERWORLD = "#minecraft:is_overworld"
SPAWNS = {
    # rare metals — mountains only
    "native_platinum": ((MOUNTAIN, -32, 96, 3, 0, 1), []),
    "sperrylite":      ((MOUNTAIN, -48, 64, 4, 0, 1), []),
    "tantalite":       ((MOUNTAIN, -16, 120, 4, 0, 1), []),
    "columbite":       ((MOUNTAIN, -16, 120, 4, 0, 2), []),
    "xenotime":        ((MOUNTAIN, -32, 96, 3, 0, 1), []),
    "molybdenite":     ((MOUNTAIN, -16, 120, 5, 0, 2), []),
    "beryl":           ((MOUNTAIN, 0, 160, 4, 0, 2), []),
    "wolframite":      ((MOUNTAIN, -16, 120, 5, 0, 2), []),
    "bismuthinite":    ((MOUNTAIN, -16, 96, 4, 0, 1), []),
    "native_silver":   ((MOUNTAIN, -16, 120, 3, 0, 1), []),
    "zircon":          ((MOUNTAIN, -16, 120, 4, 0, 1), []),
    # widespread
    "barite":          ((OVERWORLD, 0, 64, 8, 0, 2), []),
    "celestine":       ((OVERWORLD, 0, 60, 6, 0, 1), [("minecraft:desert", 20, 80, 8, 0, 2)]),
    "bastnasite":      ((OVERWORLD, -16, 48, 4, 0, 1), [(MOUNTAIN, 0, 120, 6, 0, 2)]),
    "vanadinite":      ((OVERWORLD, 32, 96, 4, 0, 1), [("#minecraft:is_badlands", 32, 120, 6, 1, 3)]),
    "magnesite":       ((OVERWORLD, 0, 64, 8, 0, 2), []),
    "chalcopyrite":    ((OVERWORLD, -16, 64, 10, 1, 3), []),
    "stannite":        ((OVERWORLD, 0, 48, 6, 0, 1), [(MOUNTAIN, 0, 120, 6, 0, 2)]),
    "cobaltite":       ((OVERWORLD, -32, 32, 5, 0, 1), []),
    "pyrite":          ((OVERWORLD, -32, 64, 8, 1, 3), []),
    "cryolite":        ((OVERWORLD, 0, 48, 4, 0, 1), [("#minecraft:is_taiga", 0, 80, 6, 0, 2)]),
    "stibnite":        ((OVERWORLD, -16, 48, 5, 0, 1), [(MOUNTAIN, 0, 120, 6, 0, 2)]),
    "apatite":         ((OVERWORLD, 0, 64, 7, 0, 2), []),
    "graphite":        ((OVERWORLD, -32, 48, 8, 0, 2), []),
}

MACERATOR_FORCE, CENTRIFUGE_FORCE = 20, 100
# block → [(item, count)] macerator recipes written when missing
MACERATE = {
    "borax": "borax_dust", "baddeleyite": "baddeleyite_dust", "halite": "halite_dust",
    "pyrite": "pyrite_dust", "cryolite": "cryolite_dust", "stibnite": "stibnite_dust",
    "apatite": "apatite_dust", "sperrylite": "sperrylite_dust", "graphite": "graphite_dust",
}
REGOLITH = ["surface_regolith", "stratified_regolith", "paleoregolith", "megaregolith"]
# dust → [(item, count, chance)] centrifuge recipes written when missing
CENTRIFUGE = {
    "borax_dust":       [("stone_dust", 1, 1.0), ("boron_dust", 1, 1.0)],
    "fluorite_dust":    [("stone_dust", 1, 1.0), ("calcium_dust", 1, 1.0)],
    "baddeleyite_dust": [("stone_dust", 1, 1.0), ("zirconium_dust", 1, 1.0), ("hafnium_mote", 1, 0.15)],
    "halite_dust":      [("sodium_chlorine_dust", 1, 1.0)],
    "pyrite_dust":      [("stone_dust", 1, 1.0), ("iron_dust", 1, 1.0), ("sulfur_dust", 1, 1.0), ("gold_mote", 1, 0.05)],
    "cryolite_dust":    [("stone_dust", 1, 1.0), ("aluminium_dust", 1, 1.0)],
    "stibnite_dust":    [("stone_dust", 1, 1.0), ("antimony_dust", 1, 1.0), ("sulfur_dust", 1, 0.5)],
    "apatite_dust":     [("stone_dust", 1, 1.0), ("calcium_dust", 1, 1.0), ("phosphorus_dust", 1, 1.0), ("cerium_mote", 1, 0.05)],
    "sperrylite_dust":  [("stone_dust", 1, 1.0), ("platinum_dust", 1, 1.0), ("arsenic_dust", 1, 0.5),
                         ("palladium_mote", 1, 0.2), ("rhodium_mote", 1, 0.1), ("ruthenium_mote", 1, 0.08),
                         ("iridium_mote", 1, 0.06), ("osmium_mote", 1, 0.06)],
}
# extra trace outputs appended to existing centrifuge recipes
CENTRIFUGE_EXTRA = {
    "monazite_dust":   [("europium_mote", 1, 0.03), ("gadolinium_mote", 1, 0.04)],
    "bastnasite_dust": [("europium_mote", 1, 0.04), ("gadolinium_mote", 1, 0.02)],
    "xenotime_dust":   [("terbium_mote", 1, 0.04), ("holmium_mote", 1, 0.03),
                        ("thulium_mote", 1, 0.02), ("lutetium_mote", 1, 0.02)],
}


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def retint_gem(color: str) -> Image.Image:
    """Gradient-map the emerald's luminance onto shadow → colour → highlight."""
    c = hex_to_rgb(color)
    dark  = tuple(int(v * 0.25) for v in c)
    light = tuple(int(v + (255 - v) * 0.85) for v in c)
    src = Image.open(EMERALD).convert("RGBA")
    lums = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b, a in src.getdata() if a]
    lo, hi = min(lums), max(lums)
    out = Image.new("RGBA", src.size)
    for (x, y) in ((x, y) for y in range(src.height) for x in range(src.width)):
        r, g, b, a = src.getpixel((x, y))
        if not a:
            continue
        t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / (hi - lo)
        if t < 0.5:
            k, a0, a1 = t / 0.5, dark, c
        else:
            k, a0, a1 = (t - 0.5) / 0.5, c, light
        out.putpixel((x, y), tuple(int(a0[i] + (a1[i] - a0[i]) * k) for i in range(3)) + (a,))
    return out


def write_json(path: Path, obj, overwrite=False):
    if path.exists() and not overwrite:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def item_assets(name: str):
    write_json(ASSETS / f"items/{name}.json",
               {"model": {"type": "minecraft:model", "model": f"omnitech:item/{name}"}})
    write_json(ASSETS / f"models/item/{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"omnitech:item/{name}"}})


def update_lang(file: str, entries: dict):
    path = ASSETS / f"lang/{file}"
    text = path.read_text(encoding="utf-8")
    lang = json.loads(text)
    missing = {k: v for k, v in entries.items() if k not in lang}
    if not missing:
        return
    # Append textually so the file's hand-made grouping/blank lines survive.
    body = text.rstrip().removesuffix("}").rstrip()
    lines = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in missing.items())
    path.write_text(f"{body},\n\n{lines}\n}}\n", encoding="utf-8")


def loot_table(mineral: str, gems: list) -> dict:
    no_silk = {"type": "minecraft:inverted", "term": "minecraft:tool/can_silk_touch"}
    pools = [{
        "rolls": 1,
        "entries": [{
            "type": "minecraft:alternatives",
            "children": [
                {
                    "type": "minecraft:item",
                    "condition": "minecraft:tool/can_silk_touch",
                    "name": f"omnitech:{mineral}",
                },
                {
                    "type": "minecraft:item",
                    "modifier": [
                        {"type": "minecraft:set_count", "count": 1},
                        {
                            "type": "minecraft:apply_bonus",
                            "enchantment": "minecraft:fortune",
                            "formula": "minecraft:uniform_bonus_count",
                            "parameters": {"bonusMultiplier": 1},
                        },
                        {"type": "minecraft:limit_count", "limit": {"min": 1, "max": MAX_DUST}},
                        {"type": "minecraft:explosion_decay"},
                    ],
                    "name": f"omnitech:{mineral}_dust",
                },
            ],
        }],
    }]
    for gem in gems:
        gem_id = gem if ":" in gem else f"omnitech:{gem}"
        pools.append({
            "rolls": 1,
            "condition": {
                "type": "minecraft:all_of",
                "terms": [
                    no_silk,
                    {"type": "minecraft:survives_explosion"},
                    {
                        "type": "minecraft:table_bonus",
                        "enchantment": "minecraft:fortune",
                        "chances": GEM_CHANCES,
                    },
                ],
            },
            "entries": [{"type": "minecraft:item", "name": gem_id}],
        })
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"omnitech:blocks/{mineral}"}


def raw_ore_loot_table(metal: str, ore: str) -> dict:
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "condition": "minecraft:tool/can_silk_touch",
                        "name": f"omnitech:{ore}",
                    },
                    {
                        "type": "minecraft:item",
                        "modifier": [
                            {
                                "type": "minecraft:apply_bonus",
                                "enchantment": "minecraft:fortune",
                                "formula": "minecraft:ore_drops",
                            },
                            {"type": "minecraft:explosion_decay"},
                        ],
                        "name": f"omnitech:raw_{metal}",
                    },
                ],
            }],
        }],
        "random_sequence": f"omnitech:blocks/{ore}",
    }


def raw_block_recipes(metal: str):
    raw, block = f"omnitech:raw_{metal}", f"omnitech:raw_{metal}_block"
    write_json(DATA / f"recipe/raw_{metal}_to_block.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"R": raw},
        "pattern": ["RRR", "RRR", "RRR"],
        "result": {"count": 1, "id": block},
    })
    write_json(DATA / f"recipe/raw_{metal}_from_block.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [block],
        "result": {"count": 9, "id": raw},
    })


def spawn_json(default, overrides) -> dict:
    keys = ("biomes", "min_y", "max_y", "vein_size", "min_count", "max_count")
    out = {"default": dict(zip(keys, default))}
    if overrides:
        out["overrides"] = [dict(zip(keys, o)) for o in overrides]
    return out


def machine_outputs(outputs):
    return [{"item": f"omnitech:{i}", "count": c, "chance": ch} for i, c, ch in outputs]


def merge_tag(tag: str, values):
    path = RES / f"data/minecraft/tags/block/{tag}.json"
    cur = json.loads(path.read_text(encoding="utf-8"))["values"] if path.exists() else []
    merged = cur + [v for v in values if v not in cur]
    if merged != cur:
        write_json(path, {"values": merged}, overwrite=True)


def main():
    tex_dir = ASSETS / "textures/item"
    en, ru = {}, {}

    for name, (formula, color, en_name, ru_name) in GEMS.items():
        write_json(DATA / f"item/{name}.json", {"formula": formula})
        item_assets(name)
        png = tex_dir / f"{name}.png"
        if not png.exists():
            retint_gem(color).save(png)
        en[f"item.omnitech.{name}"] = en_name
        ru[f"item.omnitech.{name}"] = ru_name

    for name, (formula, dark, light, en_name, ru_name) in NEW_DUSTS.items():
        dust = f"{name}_dust"
        write_json(DATA / f"item/{dust}.json", {"formula": formula})
        item_assets(dust)
        png = tex_dir / f"{dust}.png"
        if not png.exists():
            tint_image(DUST_TMPL, dark, light, 1.0, 1.0).save(png)
        en[f"item.omnitech.{dust}"] = en_name
        ru[f"item.omnitech.{dust}"] = ru_name

    for name, en_name in NEW_MINERAL_BLOCKS.items():
        write_json(DATA / f"block/{name}.json", {"ore": True})
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"omnitech:block/{name}"}}})
        en[f"block.omnitech.{name}"] = en_name

    for name, (default, overrides) in SPAWNS.items():
        write_json(DATA / f"worldgen/ore/{name}.json", spawn_json(default, overrides))

    for block, dust in MACERATE.items():
        write_json(DATA / f"machine_recipe/manual_macerator/{block}.json", {
            "requiredKineticForce": MACERATOR_FORCE, "input": f"omnitech:{block}",
            "output": [{"item": f"omnitech:{dust}", "count": 4, "chance": 1.0}]})
    for block in REGOLITH:
        write_json(DATA / f"machine_recipe/manual_macerator/{block}.json", {
            "requiredKineticForce": MACERATOR_FORCE, "input": f"omnitech:{block}",
            "output": [{"item": "omnitech:regolith_dust", "count": 2, "chance": 1.0}]})
    for dust, outputs in CENTRIFUGE.items():
        write_json(DATA / f"machine_recipe/manual_centrifuge/{dust}.json", {
            "requiredKineticForce": CENTRIFUGE_FORCE, "input": f"omnitech:{dust}",
            "output": machine_outputs(outputs)})
    for dust, extra in CENTRIFUGE_EXTRA.items():
        path = DATA / f"machine_recipe/manual_centrifuge/{dust}.json"
        recipe = json.loads(path.read_text(encoding="utf-8"))
        have = {o["item"] for o in recipe["output"]}
        add = [o for o in machine_outputs(extra) if o["item"] not in have]
        if add:
            recipe["output"] += add
            write_json(path, recipe, overwrite=True)

    tiers = {"stone": [], "iron": [], "diamond": []}
    for name, (_, tier) in MINERALS.items():
        tiers[tier].append(f"omnitech:{name}")
    for _, (ore, tier) in METAL_ORES.items():
        tiers[tier].append(f"omnitech:{ore}")
    # metal / raw-metal storage blocks mine like vanilla iron blocks
    tiers["stone"] += sorted(f"omnitech:{Path(f).stem}" for f in glob.glob(str(DATA / "block/*_block.json")))
    for tier, blocks in tiers.items():
        merge_tag(f"needs_{tier}_tool", blocks)
    merge_tag("mineable/pickaxe", [b for bs in tiers.values() for b in bs])
    merge_tag("mineable/shovel", [f"omnitech:{b}" for b in REGOLITH])

    for mineral, (gems, _) in MINERALS.items():
        if not (DATA / f"item/{mineral}_dust.json").exists():
            print(f"!! {mineral}: no {mineral}_dust item, skipping loot table")
            continue
        write_json(DATA / f"loot_table/blocks/{mineral}.json", loot_table(mineral, gems), overwrite=True)

    for metal, (ore, _) in METAL_ORES.items():
        if not (DATA / f"item/raw_{metal}.json").exists():
            print(f"!! {ore}: no raw_{metal} item, skipping")
            continue
        write_json(DATA / f"loot_table/blocks/{ore}.json", raw_ore_loot_table(metal, ore), overwrite=True)
        if (DATA / f"block/raw_{metal}_block.json").exists():
            raw_block_recipes(metal)

    update_lang("en_us.json", en)
    update_lang("ru_ru.json", ru)

    tab_path = DATA / "creative_tab/omnitech.minerals.json"
    tab = json.loads(tab_path.read_text(encoding="utf-8"))
    for item in [f"{n}_dust" for n in NEW_DUSTS] + list(GEMS):
        if f"omnitech:{item}" not in tab["data"]:
            tab["data"].append(f"omnitech:{item}")
    tab_path.write_text(json.dumps(tab, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"{len(GEMS)} gems, {len(NEW_DUSTS)} dusts, {len(MINERALS)} loot tables")


if __name__ == "__main__":
    main()
