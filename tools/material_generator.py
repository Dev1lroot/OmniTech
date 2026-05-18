#!/usr/bin/env python3
"""
material_generator.py
=====================
Generates JSON resources for every material item defined in materials.yml.

For each standalone item (items list minus blocks list) this script writes:
  data/omnitech/item/<name>.json          — ItemLoader data (stack_size, durability,
                                            formula, fire_resistant)
  assets/omnitech/models/item/<name>.json — model (handheld for tools, generated otherwise)
  assets/omnitech/items/<name>.json       — item renderer reference

For each tool item (tools list) and armor item (armor list) — registered by
ToolSetLoader/ArmorSetLoader, not ItemLoader — this script writes only:
  assets/omnitech/models/item/<name>.json — model (handheld for tools, generated for armor)
  assets/omnitech/items/<name>.json       — item renderer reference

Also regenerates:
  data/omnitech/creative_tab/omnitech.materials.json — sorted standalone items

Rules:
  • JSON files are ALWAYS overwritten (schema may change with new parameters).
  • PNG files are NEVER written by this script.

Usage:
    python3 material_generator.py
"""

import json
from pathlib import Path
import yaml

SCRIPT_DIR   = Path(__file__).parent
ROOT_DIR     = SCRIPT_DIR.parent
RES_DIR      = ROOT_DIR / "src/main/resources"
DATA_ITEM    = RES_DIR / "data/omnitech/item"
MODEL_ITEM   = RES_DIR / "assets/omnitech/models/item"
ITEMS_REF    = RES_DIR / "assets/omnitech/items"
CREATIVE_TAB = RES_DIR / "data/omnitech/creative_tab"

MOD = "omnitech"

TOOL_SUFFIXES  = {"_pickaxe", "_shovel", "_sword", "_axe", "_hoe"}
ARMOR_SUFFIXES = {"_helmet", "_chestplate", "_leggings", "_boots"}
ROD_DURABILITY = 100


def load_materials():
    with open(SCRIPT_DIR / "materials.yml", encoding="utf-8") as f:
        return yaml.safe_load(f)["materials"]


def is_tool(name: str) -> bool:
    return any(name.endswith(s) for s in TOOL_SUFFIXES)


def is_armor(name: str) -> bool:
    return any(name.endswith(s) for s in ARMOR_SUFFIXES)


def standalone_items(material: dict):
    """Yield item names for every standalone item (items list minus blocks list)."""
    block_set = set(material.get("blocks", []))
    for pattern in material.get("items", []):
        if pattern not in block_set:
            yield pattern.replace("%", material["name"])


def tool_items(material: dict):
    """Yield resolved item names from the tools list."""
    for pattern in material.get("tools", []):
        yield pattern.replace("%", material["name"])


def armor_items(material: dict):
    """Yield resolved item names from the armor list."""
    for pattern in material.get("armor", []):
        yield pattern.replace("%", material["name"])


def item_data(name: str, material: dict) -> dict:
    d = {}
    if name.endswith("_rod"):
        d["durability"] = ROD_DURABILITY
    formula = material.get("formula")
    if formula:
        d["formula"] = formula
    if not material.get("burns", True):
        d["fire_resistant"] = True
    return d


def model_json(name: str) -> dict:
    parent = "minecraft:item/handheld" if is_tool(name) else "minecraft:item/generated"
    return {
        "parent": parent,
        "textures": {"layer0": f"{MOD}:item/{name}"}
    }


def item_ref_json(name: str) -> dict:
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"{MOD}:item/{name}"
        }
    }


def write_json(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def generate_creative_tab(materials):
    """Build a sorted standalone-item list for the materials creative tab."""
    entries = []
    for mat in materials:
        for name in standalone_items(mat):
            entries.append(f"{MOD}:{name}")

    tab = {
        "icon": f"{MOD}:tungsten_ingot",
        "after": "minecraft:spawn_eggs",
        "data": ["$material_sets"] + entries + [
            f"{MOD}:granite_dust",
            f"{MOD}:andesite_dust",
            f"{MOD}:diorite_dust",
            f"{MOD}:stone_dust",
            f"{MOD}:hematite_dust",
            f"{MOD}:deepslate_dust",
            f"{MOD}:wooden_cog",
            f"{MOD}:wooden_reductor",
            f"{MOD}:coal_dust",
            f"{MOD}:coal_mote",
        ]
    }
    path = CREATIVE_TAB / "omnitech.materials.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(tab, indent=2) + "\n", encoding="utf-8")
    return path


def main():
    materials = load_materials()
    n_standalone = 0
    n_tools      = 0
    n_armor      = 0

    for mat in materials:
        # ── Standalone items: data + model + ref ──────────────────────────────
        for name in standalone_items(mat):
            write_json(DATA_ITEM  / f"{name}.json", item_data(name, mat))
            write_json(MODEL_ITEM / f"{name}.json", model_json(name))
            write_json(ITEMS_REF  / f"{name}.json", item_ref_json(name))
            n_standalone += 1

        # ── Tool items: model + ref only (ToolSetLoader handles registration) ─
        for name in tool_items(mat):
            write_json(MODEL_ITEM / f"{name}.json", model_json(name))
            write_json(ITEMS_REF  / f"{name}.json", item_ref_json(name))
            n_tools += 1

        # ── Armor items: model + ref only (ArmorSetLoader handles registration)
        for name in armor_items(mat):
            write_json(MODEL_ITEM / f"{name}.json", model_json(name))
            write_json(ITEMS_REF  / f"{name}.json", item_ref_json(name))
            n_armor += 1

    tab_path = generate_creative_tab(materials)
    print(f"Standalone items : {n_standalone} (data + model + ref)")
    print(f"Tool items       : {n_tools} (model + ref)")
    print(f"Armor items      : {n_armor} (model + ref)")
    print(f"Creative tab     : {tab_path.name}")


if __name__ == "__main__":
    main()
