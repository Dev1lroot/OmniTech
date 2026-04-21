#!/usr/bin/env python3
"""
material_generator.py
=====================
Generates data/omnitech/item/<name>.json for every standalone item that belongs
to a material defined in materials.yml.

These files are read by ItemLoader at mod startup to register plain Item instances.
Blocks and block-items are registered inside MaterialSet in Java.

Usage:
    python3 material_generator.py

Idempotent — existing files are never overwritten.
"""

import json
from pathlib import Path
import yaml

SCRIPT_DIR = Path(__file__).parent
OUTPUT_DIR = SCRIPT_DIR / "src/main/resources/data/omnitech/item"

ROD_DURABILITY = 100


def load_materials():
    with open(SCRIPT_DIR / "materials.yml", encoding="utf-8") as f:
        return yaml.safe_load(f)["materials"]


def item_props(name: str) -> dict:
    if name.endswith("_rod"):
        return {"durability": ROD_DURABILITY}
    return {}


def standalone_items(material: dict):
    """Yield (item_name, props) for every standalone item in this material."""
    block_set = set(material.get("blocks", []))
    for pattern in material.get("items", []):
        if pattern in block_set:
            continue
        name = pattern.replace("%", material["name"])
        yield name, item_props(name)


def write_item(name: str, props: dict) -> bool:
    path = OUTPUT_DIR / f"{name}.json"
    if path.exists():
        return False
    content = (json.dumps(props, indent=2) if props else "{}") + "\n"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return True


def main():
    materials = load_materials()
    written, skipped = [], []

    for mat in materials:
        for name, props in standalone_items(mat):
            if write_item(name, props):
                written.append(name)
            else:
                skipped.append(name)

    if written:
        print(f"Generated {len(written)} item JSON(s):")
        for n in written:
            print(f"  + {n}.json")
    if skipped:
        print(f"Skipped {len(skipped)} (already exist):")
        for n in skipped:
            print(f"  ~ {n}.json")
    if not written and not skipped:
        print("Nothing to do.")


if __name__ == "__main__":
    main()
