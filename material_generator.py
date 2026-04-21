#!/usr/bin/env python3
"""
material_generator.py
=====================
Generates JSON resources for every standalone material item defined in materials.yml.

For each standalone item (items list minus blocks list) this script writes:
  data/omnitech/item/<name>.json        — ItemLoader data (stack_size, durability,
                                          formula, fire_resistant)
  assets/omnitech/models/item/<name>.json — flat model (handheld for tools,
                                            generated for everything else)
  assets/omnitech/items/<name>.json     — item renderer reference

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
RES_DIR      = SCRIPT_DIR / "src/main/resources"
DATA_ITEM    = RES_DIR / "data/omnitech/item"
MODEL_ITEM   = RES_DIR / "assets/omnitech/models/item"
ITEMS_REF    = RES_DIR / "assets/omnitech/items"
CREATIVE_TAB = RES_DIR / "data/omnitech/creative_tab"

MOD = "omnitech"

TOOL_SUFFIXES = {"_pickaxe", "_shovel", "_sword", "_axe", "_hoe"}
ROD_DURABILITY = 100


def load_materials():
    with open(SCRIPT_DIR / "materials.yml", encoding="utf-8") as f:
        return yaml.safe_load(f)["materials"]


def is_tool(name: str) -> bool:
    return any(name.endswith(s) for s in TOOL_SUFFIXES)


def standalone_items(material: dict):
    """Yield item names for every standalone item in this material."""
    block_set = set(material.get("blocks", []))
    for pattern in material.get("items", []):
        if pattern not in block_set:
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
    written = []

    for mat in materials:
        for name in standalone_items(mat):
            data = item_data(name, mat)
            write_json(DATA_ITEM / f"{name}.json", data)
            write_json(MODEL_ITEM / f"{name}.json", model_json(name))
            write_json(ITEMS_REF / f"{name}.json", item_ref_json(name))
            written.append(name)

    tab_path = generate_creative_tab(materials)
    print(f"Written {len(written)} item sets (data + model + ref).")
    print(f"Updated creative tab: {tab_path.name}")


if __name__ == "__main__":
    main()
