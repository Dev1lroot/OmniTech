#!/usr/bin/env python3
"""
recipe_generator.py
===================
Generates all OmniTech material JSON files from materials.yml.

Covers:
  Crafting    — mote↔dust (shapeless), ingot↔block (shaped), tools, armor
  Macerator   — ingot → dust
  Smelter     — any meltable item → molten fluid
  Foundry     — molten fluid → cog / ingot / rod
  Fluid       — data/omnitech/fluid/molten_<name>.json
  Tool mats   — data/omnitech/tool_material/<name>.json
  Armor mats  — data/omnitech/armor_material/<name>.json

Rules:
  • JSON files are ALWAYS overwritten (schema may change with new parameters).

Usage:
    python3 recipe_generator.py
"""

import json
from pathlib import Path
import yaml

SCRIPT_DIR   = Path(__file__).parent
RES_DIR      = SCRIPT_DIR / "src/main/resources"
RECIPE_DIR   = RES_DIR / "data/omnitech/recipe"
FLUID_DIR    = RES_DIR / "data/omnitech/fluid"
TOOL_MAT_DIR = RES_DIR / "data/omnitech/tool_material"
ARMOR_MAT_DIR = RES_DIR / "data/omnitech/armor_material"
MOD          = "omnitech"

TIER_TO_TAG = {
    "stone":   "minecraft:incorrect_for_stone_tool",
    "iron":    "minecraft:incorrect_for_iron_tool",
    "diamond": "minecraft:incorrect_for_diamond_tool",
}


# ── Load config ───────────────────────────────────────────────────────────────

def load_config():
    with open(SCRIPT_DIR / "materials.yml", encoding="utf-8") as f:
        return yaml.safe_load(f)

_config   = load_config()
MATERIALS = _config["materials"]
MELT_MB   = _config.get("melt_values", {})


# ── ID helpers ────────────────────────────────────────────────────────────────

def ingot_id(mat: dict) -> str:
    return mat.get("vanilla_id") or f"{MOD}:{mat['name']}_ingot"

def fluid_id(name: str) -> str:
    return f"{MOD}:molten_{name}"

def item_id(name: str) -> str:
    return f"{MOD}:{name}"


# ── Recipe builders ───────────────────────────────────────────────────────────

def r_mote_to_dust(mote: str, dust: str) -> dict:
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(mote)] * 9,
        "result": {"count": 1, "id": item_id(dust)},
    }

def r_dust_to_mote(dust: str, mote: str) -> dict:
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(dust)],
        "result": {"count": 9, "id": item_id(mote)},
    }

def r_ingot_to_block(ingot_item_id: str, block: str) -> dict:
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"I": ingot_item_id},
        "pattern": ["III", "III", "III"],
        "result": {"count": 1, "id": item_id(block)},
    }

def r_block_to_ingot(block: str, ingot_item_id: str) -> dict:
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [item_id(block)],
        "result": {"count": 9, "id": ingot_item_id},
    }

def r_macerator(input_item_id: str, dust: str) -> dict:
    return {
        "requiredKineticForce": 30,
        "input": input_item_id,
        "output": [{"item": item_id(dust), "count": 1, "chance": 1.0}],
    }

def r_smelter_melt(input_item_id: str, material_name: str, temp: int, amount_mb: int) -> dict:
    return {
        "requiredMinimalTemperature": temp,
        "input": [input_item_id],
        "output": {"fluid": fluid_id(material_name), "amount": amount_mb},
    }

def r_foundry(material_name: str, temp: int, amount_mb: int, template: str, output: str) -> dict:
    return {
        "requiredMinimalTemperature": temp,
        "input": {"fluid": fluid_id(material_name), "amount": amount_mb},
        "template": f"{MOD}:{template}",
        "output": item_id(output),
    }

def r_tool(mat_name: str, tool: str, pattern: list) -> dict:
    return {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"X": item_id(f"{mat_name}_ingot"), "S": "minecraft:stick"},
        "pattern": pattern,
        "result": {"count": 1, "id": item_id(f"{mat_name}_{tool}")},
    }

def r_armor(mat_name: str, piece: str, pattern: list) -> dict:
    return {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"X": item_id(f"{mat_name}_ingot")},
        "pattern": pattern,
        "result": {"count": 1, "id": item_id(f"{mat_name}_{piece}")},
    }


# ── Shape constants ───────────────────────────────────────────────────────────

TOOL_SHAPES = {
    "pickaxe": ["XXX", " S ", " S "],
    "shovel":  [" X ", " S ", " S "],
    "sword":   [" X ", " X ", " S "],
    "axe":     ["XX ", "XS ", " S "],
    "hoe":     ["XX ", " S ", " S "],
}

ARMOR_SHAPES = {
    "helmet":     ["XXX", "X X", "   "],
    "chestplate": ["X X", "XXX", "XXX"],
    "leggings":   ["XXX", "X X", "X X"],
    "boots":      ["   ", "X X", "X X"],
}

ARMOR_MELT = {
    "helmet": 2500, "chestplate": 4000, "leggings": 3500, "boots": 2000,
}
TOOL_MELT = {
    "pickaxe": 1500, "shovel": 500, "sword": 1000, "axe": 1500, "hoe": 1000,
}


# ── File I/O ──────────────────────────────────────────────────────────────────

def write(dir_path: Path, rel_path: str, data: dict):
    path = dir_path / rel_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


# ── Fluid JSON ────────────────────────────────────────────────────────────────

def generate_fluid_json(mat: dict):
    name = mat["name"]
    # temperature in Kelvin: melt_temp is in Celsius
    temp_c = mat.get("melt_temp", 1000)
    temp_k = temp_c + 273

    data = {
        "density":     mat.get("density", 7000),
        "viscosity":   mat.get("viscosity", 5000),
        "temperature": temp_k,
        "light_level": 3,
    }
    write(FLUID_DIR, f"molten_{name}.json", data)


# ── Tool material JSON ────────────────────────────────────────────────────────

def generate_tool_material_json(mat: dict):
    name  = mat["name"]
    stats = mat["tool_stats"]
    tier  = stats.get("tier", "iron")
    data = {
        "incorrect_for_drops_tag": TIER_TO_TAG.get(tier, TIER_TO_TAG["iron"]),
        "durability":              stats["durability"],
        "speed":                   float(stats["speed"]),
        "attack_damage_bonus":     float(stats["attack_bonus"]),
        "enchantability":          stats["enchantability"],
        "repair_ingredient":       f"{MOD}:{name}_ingot",
    }
    write(TOOL_MAT_DIR, f"{name}.json", data)


# ── Armor material JSON ───────────────────────────────────────────────────────

def generate_armor_material_json(mat: dict):
    name  = mat["name"]
    stats = mat["armor_stats"]
    defense = stats["defense"]  # [boots, leggings, chestplate, helmet, body]
    data = {
        "durability_multiplier":  stats["durability_mult"],
        "defense_boots":          defense[0],
        "defense_leggings":       defense[1],
        "defense_chestplate":     defense[2],
        "defense_helmet":         defense[3],
        "defense_body":           defense[4],
        "enchantability":         stats["enchantability"],
        "toughness":              float(stats["toughness"]),
        "knockback_resistance":   float(stats["knockback_resistance"]),
        "repair_ingredient":      f"{MOD}:{name}_ingot",
    }
    write(ARMOR_MAT_DIR, f"{name}.json", data)


# ── Main generator ────────────────────────────────────────────────────────────

def generate():
    count_recipes = 0
    count_fluids  = 0
    count_tools   = 0
    count_armors  = 0

    for mat in MATERIALS:
        name       = mat["name"]
        items      = mat.get("items", [])
        blocks     = mat.get("blocks", [])
        tools_list = mat.get("tools", [])
        armor_list = mat.get("armor", [])
        temp       = mat.get("melt_temp")
        ing_id     = ingot_id(mat)

        block_set  = set(blocks)
        standalone = [p for p in items if p not in block_set]

        def has(pattern):
            return pattern in standalone or pattern in block_set

        def resolved(pattern):
            return pattern.replace("%", name)

        dust  = resolved("%_dust")  if has("%_dust")  else None
        mote  = resolved("%_mote")  if has("%_mote")  else None
        ingot = resolved("%_ingot") if has("%_ingot") else None
        block = resolved("%_block") if has("%_block") else None
        cog   = resolved("%_cog")   if has("%_cog")   else None
        rod   = resolved("%_rod")   if has("%_rod")   else None

        # ── Mote ↔ Dust ───────────────────────────────────────────────────────
        if dust and mote:
            write(RECIPE_DIR, f"{name}_mote_to_dust.json", r_mote_to_dust(mote, dust))
            write(RECIPE_DIR, f"{name}_dust_to_mote.json", r_dust_to_mote(dust, mote))
            count_recipes += 2

        # ── Ingot ↔ Block ─────────────────────────────────────────────────────
        if ingot and block:
            write(RECIPE_DIR, f"{name}_ingot_to_block.json", r_ingot_to_block(ing_id, block))
            write(RECIPE_DIR, f"{name}_block_to_ingot.json", r_block_to_ingot(block, ing_id))
            count_recipes += 2

        # ── Macerator: ingot → dust ───────────────────────────────────────────
        if ingot and dust:
            write(RECIPE_DIR, f"manual_macerator/{name}_ingot.json", r_macerator(ing_id, dust))
            count_recipes += 1

        # ── Smelter melt recipes ──────────────────────────────────────────────
        if temp is not None:
            for pattern in standalone:
                mb = MELT_MB.get(pattern)
                if mb is None:
                    continue
                inp = ing_id if pattern == "%_ingot" else item_id(resolved(pattern))
                write(RECIPE_DIR, f"smelting/{resolved(pattern)}_melt.json",
                      r_smelter_melt(inp, name, temp, mb))
                count_recipes += 1

            for pattern in blocks:
                mb = MELT_MB.get(pattern)
                if mb is None:
                    continue
                write(RECIPE_DIR, f"smelting/{resolved(pattern)}_melt.json",
                      r_smelter_melt(item_id(resolved(pattern)), name, temp, mb))
                count_recipes += 1

            if armor_list:
                for piece, mb in ARMOR_MELT.items():
                    write(RECIPE_DIR, f"smelting/{name}_{piece}_melt.json",
                          r_smelter_melt(item_id(f"{name}_{piece}"), name, temp, mb))
                    count_recipes += 1

            if tools_list:
                for tool, mb in TOOL_MELT.items():
                    write(RECIPE_DIR, f"smelting/{name}_{tool}_melt.json",
                          r_smelter_melt(item_id(f"{name}_{tool}"), name, temp, mb))
                    count_recipes += 1

        # ── Foundry ───────────────────────────────────────────────────────────
        if temp is not None:
            if cog:
                write(RECIPE_DIR, f"foundry/{name}_cog.json",
                      r_foundry(name, temp, 200, "cog_template", cog))
                count_recipes += 1
            if ingot:
                write(RECIPE_DIR, f"foundry/{name}_ingot.json",
                      r_foundry(name, temp, 1000, "ingot_template", ingot))
                count_recipes += 1
            if rod:
                write(RECIPE_DIR, f"foundry/{name}_rod.json",
                      r_foundry(name, temp, 1000, "rod_template", rod))
                count_recipes += 1

        # ── Tool crafting recipes ─────────────────────────────────────────────
        if tools_list:
            for tool, shape in TOOL_SHAPES.items():
                write(RECIPE_DIR, f"{name}_{tool}.json", r_tool(name, tool, shape))
                count_recipes += 1

        # ── Armor crafting recipes ────────────────────────────────────────────
        if armor_list:
            for piece, shape in ARMOR_SHAPES.items():
                write(RECIPE_DIR, f"{name}_{piece}.json", r_armor(name, piece, shape))
                count_recipes += 1

        # ── Fluid JSON ────────────────────────────────────────────────────────
        if temp is not None:
            generate_fluid_json(mat)
            count_fluids += 1

        # ── Tool material JSON ────────────────────────────────────────────────
        if tools_list and mat.get("tool_stats"):
            generate_tool_material_json(mat)
            count_tools += 1

        # ── Armor material JSON ───────────────────────────────────────────────
        if armor_list and mat.get("armor_stats"):
            generate_armor_material_json(mat)
            count_armors += 1

    return count_recipes, count_fluids, count_tools, count_armors


def main():
    r, f, t, a = generate()
    print(f"Written {r} recipe(s), {f} fluid JSON(s), {t} tool material(s), {a} armor material(s).")


if __name__ == "__main__":
    main()
