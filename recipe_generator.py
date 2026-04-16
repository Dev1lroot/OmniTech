#!/usr/bin/env python3
"""
recipe_generator.py
===================
Single source of truth for all OmniTech material recipe JSON files.

Covers every recipe category:
  Crafting    — mote↔dust (shapeless), ingot↔block (shaped), tool sets, armor sets
  Macerator   — ingot → dust + motes
  Smelter     — ANY meltable item → molten fluid  (new: all item types)
  Foundry     — molten fluid → cog

Melt values follow one rule: processed items refund at most 50% of their ingot
cost (1 ingot = 1000 mb).  Exceptions:
  - ingot / raw ore: 1000 mb (they ARE the base unit — no discount)
  - cog: 200 mb  (exact refund — it was cast from 200 mb in the foundry)

Usage:
    python3 recipe_generator.py

Idempotent — existing files are never overwritten.
Re-run after adding a new material or changing material definitions.
"""

import json
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
RECIPE_DIR = SCRIPT_DIR / "src/main/resources/data/omnitech/recipe"
MOD        = "omnitech"

# ── Material definitions ─────────────────────────────────────────────────────
# Mirrors OmniTechMaterials.java exactly.
# Tuple: (name, item_patterns, block_patterns)
# Patterns in item_patterns that also appear in block_patterns are block items — skipped here.

MATERIALS = [
    ("tungsten",        ["raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_ore", "%_block", "raw_%_block"]),
    ("chromium",        ["raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_ore", "%_block", "raw_%_block"]),
    ("tin",             ["raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_ore", "%_block", "raw_%_block"]),
    ("aluminium",       ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("cobalt",          ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("nickel",          ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("zinc",            ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("lead",            ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("uranium",         ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("titanium",        ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("steel",           ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    ("brass",           ["%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget",
                         "%_reductor", "%_cog", "%_wire", "%_coil", "%_rod"],
                        ["%_block"]),
    # Vanilla metals — ore/ingot/block exist in vanilla, only the missing variants
    ("iron",            ["%_dust", "%_plate", "%_mote", "%_reductor", "%_wire", "%_coil", "%_rod"],
                        []),
    ("copper",          ["%_dust", "%_plate", "%_mote", "%_reductor", "%_wire", "%_coil", "%_rod"],
                        []),
    # No-metal / chemical — no smelting recipes
    ("sodium_chlorine", ["%_dust", "%_mote"], []),
    ("graphite",        ["%_dust", "%_mote", "%_rod"], []),
    ("regolith",        ["%_dust", "%_mote"], ["surface_%", "stratified_%", "paleo%", "mega%"]),
    ("skutterudite",    ["%_dust"], ["%" ]),
    ("galena",          ["%_dust"], ["%"]),
    ("halite",          ["%_dust"], ["%"]),
    ("sphalerite",      ["%_dust"], ["%"]),
    ("lepidolite",      ["%_dust"], ["%"]),
]

# ── Melt temperatures (°C) ───────────────────────────────────────────────────
# Materials absent from this dict produce no smelter recipes.
MELT_TEMP = {
    "tin":       300,
    "lead":      400,
    "zinc":      500,
    "aluminium": 700,
    "brass":     1000,
    "cobalt":    1500,
    "nickel":    1500,
    "steel":     1500,
    "uranium":   1200,
    "titanium":  1700,
    "chromium":  2000,
    "tungsten":  3500,
    "iron":      1600,
    "copper":    1100,
}

# ── Vanilla ingot overrides ──────────────────────────────────────────────────
# For materials whose ingot lives in minecraft: namespace.
VANILLA_INGOT = {
    "iron":   "minecraft:iron_ingot",
    "copper": "minecraft:copper_ingot",
}

def ingot_id(material: str) -> str:
    return VANILLA_INGOT.get(material, f"{MOD}:{material}_ingot")

def fluid_id(material: str) -> str:
    return f"{MOD}:molten_{material}"

def item_id(name: str) -> str:
    return f"{MOD}:{name}"

# ── Melt amounts (mb) per item pattern ───────────────────────────────────────
# Rule: ingot = 1000 mb (base). Processed items ≤ 50% of ingot cost.
# Cog exception: 200 mb exact cast refund (it was made from 200 mb in foundry).
#
#  Pattern               Ingot cost   Melt output   Reasoning
#  raw_%                 1            1000 mb        raw ore = pre-smelting ingot
#  %_ingot               1            1000 mb        base unit
#  %_nugget              1/9          100  mb        floor(1/9 * 1000), rounded
#  %_dust                1            500  mb        macerator output, 50%
#  %_mote                1/9 of dust  50   mb        ~1/9 * 500, rounded
#  %_plate               1            500  mb        pressed ingot, 50%
#  %_cog                 200 mb       200  mb        foundry cast — exact refund
#  %_wire                1            500  mb        drawn ingot, 50%
#  %_coil                3 wires      1500 mb        50% of 3 × 1000
#  %_rod                 1            500  mb        rod stock, 50%
#  %_reductor            1            500  mb        ~1 ingot, 50%
#  %_block               9            4500 mb        50% of 9 × 1000
#  raw_%_block           9            4500 mb        50% of 9 × 1000
#  %_helmet              5            2500 mb        50% of 5 × 1000  (vanilla recipe)
#  %_chestplate          8            4000 mb        50% of 8 × 1000  (user confirmed)
#  %_leggings            7            3500 mb        50% of 7 × 1000
#  %_boots               4            2000 mb        50% of 4 × 1000  (user confirmed)
#  %_pickaxe             3            1500 mb        50% of 3 × 1000  (ignore sticks)
#  %_shovel              1            500  mb        50% of 1 × 1000
#  %_sword               2            1000 mb        50% of 2 × 1000
#  %_axe                 3            1500 mb        50% of 3 × 1000
#  %_hoe                 2            1000 mb        50% of 2 × 1000

MELT_MB = {
    "raw_%":        1000,
    "%_ingot":      1000,
    "%_nugget":     100,
    "%_dust":       500,
    "%_mote":       50,
    "%_plate":      500,
    "%_cog":        200,
    "%_wire":       500,
    "%_coil":       1500,
    "%_rod":        500,
    "%_reductor":   500,
    "%_block":      4500,
    "raw_%_block":  4500,
    # armor
    "%_helmet":     2500,
    "%_chestplate": 4000,
    "%_leggings":   3500,
    "%_boots":      2000,
    # tools
    "%_pickaxe":    1500,
    "%_shovel":     500,
    "%_sword":      1000,
    "%_axe":        1500,
    "%_hoe":        1000,
}

# ── Tool + armor set materials ────────────────────────────────────────────────
# Mirrors OmniTechTools.java / OmniTechArmors.java.
# Only these 12 metals have crafted tool and armor sets.
TOOL_ARMOR_METALS = [
    "zinc", "lead", "tin", "nickel", "aluminium", "brass",
    "cobalt", "steel", "uranium", "titanium", "chromium", "tungsten",
]

# ── Recipe builders ───────────────────────────────────────────────────────────

def r_mote_to_dust(material: str, mote: str, dust: str) -> dict:
    """9 motes → 1 dust (shapeless crafting)."""
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(mote)] * 9,
        "result": {"count": 1, "id": item_id(dust)},
    }

def r_dust_to_mote(material: str, dust: str, mote: str) -> dict:
    """1 dust → 9 motes (shapeless crafting)."""
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(dust)],
        "result": {"count": 9, "id": item_id(mote)},
    }

def r_ingot_to_block(ingot: str, block: str) -> dict:
    """9 ingots → 1 block (shaped crafting)."""
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"I": item_id(ingot) if not ingot.startswith("minecraft:") else ingot},
        "pattern": ["III", "III", "III"],
        "result": {"count": 1, "id": item_id(block)},
    }

def r_block_to_ingot(block: str, ingot: str) -> dict:
    """1 block → 9 ingots (shapeless crafting)."""
    result_id = ingot_id_raw(ingot)
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(block)],
        "result": {"count": 9, "id": result_id},
    }

def ingot_id_raw(name: str) -> str:
    """Return full item ID — may be minecraft: for vanilla ingots."""
    for vanilla in VANILLA_INGOT.values():
        if vanilla.endswith(":" + name):
            return vanilla
    return item_id(name)

def r_macerator(ingot: str, dust: str, mote: str | None, has_mote: bool) -> dict:
    """1 ingot → 1 dust + optional 2 motes at 40% chance (macerator)."""
    outputs = [{"item": item_id(dust), "count": 1, "chance": 1.0}]
    # if has_mote and mote:
    #     outputs.append({"item": item_id(mote), "count": 2, "chance": 0.4})
    input_id = VANILLA_INGOT.get(ingot.removesuffix("_ingot"), item_id(ingot))
    # ingot argument is the bare item name like "tungsten_ingot"
    return {
        "requiredKineticForce": 30,
        "input": input_id if ingot.startswith("minecraft:") else item_id(ingot),
        "output": outputs,
    }

def r_smelter_melt(input_item: str, material: str, amount_mb: int) -> dict:
    """item → molten fluid (smelter)."""
    temp = MELT_TEMP[material]
    # input_item may be a vanilla ID (e.g. minecraft:iron_ingot) or a mod item
    inp = input_item if ":" in input_item else item_id(input_item)
    return {
        "requiredMinimalTemperature": temp,
        "input": [inp],
        "output": {"fluid": fluid_id(material), "amount": amount_mb},
    }

def r_foundry_cog(material: str) -> dict:
    """200 mb molten → 1 cog (foundry)."""
    temp = MELT_TEMP[material]
    return {
        "requiredMinimalTemperature": temp,
        "input": {"fluid": fluid_id(material), "amount": 200},
        "template": f"{MOD}:cog_template",
        "output": item_id(f"{material}_cog"),
    }

def r_tool(material: str, tool: str, pattern: list[str]) -> dict:
    """Shaped crafting recipe for a tool."""
    return {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"X": item_id(f"{material}_ingot"), "S": "minecraft:stick"},
        "pattern": pattern,
        "result": {"count": 1, "id": item_id(f"{material}_{tool}")},
    }

def r_armor(material: str, piece: str, pattern: list[str]) -> dict:
    """Shaped crafting recipe for an armor piece."""
    return {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"X": item_id(f"{material}_ingot")},
        "pattern": pattern,
        "result": {"count": 1, "id": item_id(f"{material}_{piece}")},
    }

# ── Tool crafting patterns (standard vanilla shapes) ─────────────────────────
TOOL_SHAPES = {
    "pickaxe": ["XXX", " S ", " S "],
    "shovel":  [" X ", " S ", " S "],
    "sword":   [" X ", " X ", " S "],
    "axe":     ["XX ", "XS ", " S "],
    "hoe":     ["XX ", " S ", " S "],
}

# ── Armor crafting patterns (standard vanilla shapes) ─────────────────────────
ARMOR_SHAPES = {
    "helmet":     ["XXX", "X X", "   "],
    "chestplate": ["X X", "XXX", "XXX"],
    "leggings":   ["XXX", "X X", "X X"],
    "boots":      ["   ", "X X", "X X"],
}

# ── File I/O ──────────────────────────────────────────────────────────────────

def write(rel_path: str, data: dict) -> bool:
    """Write JSON to RECIPE_DIR / rel_path. Returns True if written, False if skipped."""
    path = RECIPE_DIR / rel_path
    if path.exists():
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return True

# ── Generator ─────────────────────────────────────────────────────────────────

def generate():
    written = []
    skipped = []

    def emit(rel_path: str, data: dict):
        if write(rel_path, data):
            written.append(rel_path)
        else:
            skipped.append(rel_path)

    for material, item_patterns, block_patterns in MATERIALS:
        block_set  = set(block_patterns)
        # standalone patterns only (exclude block items)
        standalone = [p for p in item_patterns if p not in block_set]

        has = {p: True for p in standalone + block_patterns}  # quick membership

        def has_pat(p): return p in has

        # ── Derive resolved names ─────────────────────────────────────────────
        def name(pattern): return pattern.replace("%", material)

        dust  = name("%_dust")  if has_pat("%_dust")  else None
        mote  = name("%_mote")  if has_pat("%_mote")  else None
        ingot = name("%_ingot") if has_pat("%_ingot") else None
        block = name("%_block") if has_pat("%_block") else None
        cog   = name("%_cog")   if has_pat("%_cog")   else None

        # ── Mote ↔ Dust ───────────────────────────────────────────────────────
        if dust and mote:
            emit(f"{material}_mote_to_dust.json",
                 r_mote_to_dust(material, mote, dust))
            emit(f"{material}_dust_to_mote.json",
                 r_dust_to_mote(material, dust, mote))

        # ── Ingot ↔ Block ─────────────────────────────────────────────────────
        if ingot and block:
            raw_ingot = VANILLA_INGOT.get(material, item_id(ingot))
            emit(f"{material}_ingot_to_block.json",
                 r_ingot_to_block(raw_ingot, block))
            emit(f"{material}_block_to_ingot.json",
                 r_block_to_ingot(block, ingot))

        # ── Macerator: ingot → dust ───────────────────────────────────────────
        if ingot and dust:
            raw_ingot_id = VANILLA_INGOT.get(material, item_id(ingot))
            emit(f"manual_macerator/{material}_ingot.json",
                 r_macerator(ingot, dust, mote, bool(mote)))

        # ── Smelter melt: every item that has a known melt amount ─────────────
        temp = MELT_TEMP.get(material)
        if temp is not None:
            # standalone items
            for pattern in standalone:
                mb = MELT_MB.get(pattern)
                if mb is None:
                    continue
                item_name = name(pattern)
                # For %_ingot, use vanilla ingot ID if applicable
                if pattern == "%_ingot" and material in VANILLA_INGOT:
                    input_item = VANILLA_INGOT[material]
                else:
                    input_item = item_id(item_name)
                emit(f"smelting/{item_name}_melt.json",
                     r_smelter_melt(input_item, material, mb))

            # block items (the block-item, i.e. the item form of the block)
            for pattern in block_patterns:
                mb = MELT_MB.get(pattern)
                if mb is None:
                    continue
                item_name = name(pattern)
                emit(f"smelting/{item_name}_melt.json",
                     r_smelter_melt(item_id(item_name), material, mb))

            # armor pieces (only for metals with armor sets)
            if material in TOOL_ARMOR_METALS:
                for piece, mb in [("helmet", 2500), ("chestplate", 4000),
                                   ("leggings", 3500), ("boots", 2000)]:
                    emit(f"smelting/{material}_{piece}_melt.json",
                         r_smelter_melt(item_id(f"{material}_{piece}"), material, mb))

            # tools (only for metals with tool sets)
            if material in TOOL_ARMOR_METALS:
                for tool, mb in [("pickaxe", 1500), ("shovel", 500),
                                  ("sword", 1000), ("axe", 1500), ("hoe", 1000)]:
                    emit(f"smelting/{material}_{tool}_melt.json",
                         r_smelter_melt(item_id(f"{material}_{tool}"), material, mb))

        # ── Foundry: molten → cog ─────────────────────────────────────────────
        if cog and temp is not None:
            emit(f"foundry/{material}_cog.json", r_foundry_cog(material))

        # ── Tool crafting recipes ─────────────────────────────────────────────
        if material in TOOL_ARMOR_METALS:
            for tool, shape in TOOL_SHAPES.items():
                emit(f"{material}_{tool}.json", r_tool(material, tool, shape))

        # ── Armor crafting recipes ────────────────────────────────────────────
        if material in TOOL_ARMOR_METALS:
            for piece, shape in ARMOR_SHAPES.items():
                emit(f"{material}_{piece}.json", r_armor(material, piece, shape))

    return written, skipped


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    written, skipped = generate()

    if written:
        print(f"Generated {len(written)} recipe(s):")
        for f in written:
            print(f"  + {f}")

    if skipped:
        print(f"Skipped  {len(skipped)} (already exist)")

    if not written and not skipped:
        print("Nothing to do.")

if __name__ == "__main__":
    main()
