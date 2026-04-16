#!/usr/bin/env python3
"""
material_generator.py
=====================
Generates data/omnitech/item/<name>.json for every standalone item that belongs
to a MaterialSet defined in OmniTechMaterials.java.

These files are read by ItemLoader at mod startup to register plain Item instances.
Blocks and block-items are still registered inside MaterialSet in Java.

Usage:
    python3 material_generator.py

Run this script whenever you add a new material or change item patterns in
OmniTechMaterials.java.  It is idempotent — existing files are never overwritten.

JSON schema written per item:
    {}                          plain ingredient (stack 64, no durability)
    { "durability": 100 }       rod-type item (stack 1, 100 uses)
"""

import json
import os
from pathlib import Path

# ---------------------------------------------------------------------------
# Output directory (relative to this script)
# ---------------------------------------------------------------------------
SCRIPT_DIR  = Path(__file__).parent
OUTPUT_DIR  = SCRIPT_DIR / "src/main/resources/data/omnitech/item"

# ---------------------------------------------------------------------------
# Rules: which item name suffix → extra JSON properties
# ---------------------------------------------------------------------------
ROD_DURABILITY = 100

def item_props(name: str) -> dict:
    """Return the JSON property dict for a given item name."""
    if name.endswith("_rod"):
        return {"durability": ROD_DURABILITY}
    return {}

# ---------------------------------------------------------------------------
# Material definitions  (mirrors OmniTechMaterials.java exactly)
#
# Each entry:  (material_name,  item_patterns,  block_patterns)
#
# item_patterns that also appear in block_patterns are BLOCK ITEMS (skipped here).
# The rest become standalone items registered via ItemLoader.
# ---------------------------------------------------------------------------
MATERIALS = [
    # name              item patterns                                                              block patterns
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

    # Vanilla materials — ore/ingot/block exist in vanilla, only the missing variants
    ("iron",            ["%_dust", "%_plate", "%_mote", "%_reductor", "%_wire", "%_coil", "%_rod"],
                        []),

    ("copper",          ["%_dust", "%_plate", "%_mote", "%_reductor", "%_wire", "%_coil", "%_rod"],
                        []),

    # Chemical compounds / minerals — dust/mote only
    ("sodium_chlorine", ["%_dust", "%_mote"],                       []),
    ("graphite",        ["%_dust", "%_mote", "%_rod"],              []),
    ("regolith",        ["%_dust", "%_mote"],                       ["surface_%", "stratified_%", "paleo%", "mega%"]),
    ("skutterudite",    ["%_dust"],                                  ["%"]),
    ("galena",          ["%_dust"],                                  ["%"]),
    ("halite",          ["%_dust"],                                  ["%"]),
    ("sphalerite",      ["%_dust"],                                  ["%"]),
    ("lepidolite",      ["%_dust"],                                  ["%"]),
]

# ---------------------------------------------------------------------------
# Generator
# ---------------------------------------------------------------------------

def standalone_items(material: str, item_patterns: list, block_patterns: list):
    """Yield (name, props) for every standalone item in this material."""
    block_set = set(block_patterns)
    for pattern in item_patterns:
        if pattern in block_set:
            continue  # this is a block item, not a standalone item
        name = pattern.replace("%", material)
        yield name, item_props(name)


def write_item(name: str, props: dict, dry_run: bool = False) -> bool:
    """Write data/omnitech/item/<name>.json. Returns True if written, False if skipped."""
    path = OUTPUT_DIR / f"{name}.json"
    if path.exists():
        return False  # never overwrite

    content = json.dumps(props, indent=2) if props else "{}"
    content += "\n"

    if not dry_run:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    return True


def main():
    written  = []
    skipped  = []

    for material, item_patterns, block_patterns in MATERIALS:
        for name, props in standalone_items(material, item_patterns, block_patterns):
            if write_item(name, props):
                written.append(name)
            else:
                skipped.append(name)

    # Summary
    if written:
        print(f"Generated {len(written)} item JSON(s):")
        for name in written:
            print(f"  + {name}.json")
    if skipped:
        print(f"Skipped {len(skipped)} (already exist):")
        for name in skipped:
            print(f"  ~ {name}.json")
    if not written and not skipped:
        print("Nothing to do.")


if __name__ == "__main__":
    main()
