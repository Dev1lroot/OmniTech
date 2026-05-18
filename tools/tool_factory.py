#!/usr/bin/env python3
"""
tool_factory.py
===============
Generates tool item textures and crafting recipes for every material in
materials.yml that has a non-empty `tools` list.

Usage:
    python3 tool_factory.py
"""

import os
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance, ImageDraw
import yaml

SCRIPT_DIR   = Path(__file__).parent
ROOT_DIR     = SCRIPT_DIR.parent
TEMPLATE_DIR = str(ROOT_DIR / "templates/assets")
OUTPUT_DIR   = str(ROOT_DIR / "src/main/resources/assets/omnitech/textures/item")
RECIPE_DIR   = str(ROOT_DIR / "src/main/resources/data/omnitech/recipe")

TOOLS = ["pickaxe", "shovel", "sword", "axe", "hoe"]

TOOL_RECIPES = {
    "pickaxe": ["XXX", " S ", " S "],
    "axe":     ["XX ", "XS ", " S "],
    "shovel":  [" X ", " S ", " S "],
    "hoe":     ["XX ", " S ", " S "],
    "sword":   [" X ", " X ", " S "],
}


def load_materials():
    with open(SCRIPT_DIR / "materials.yml", encoding="utf-8") as f:
        return yaml.safe_load(f)["materials"]


def hex_to_rgb(hex_str):
    hex_str = hex_str.lstrip("#")
    return tuple(int(hex_str[i:i + 2], 16) for i in (0, 2, 4))


def create_metal_gradient(size, color_base, color_highlight):
    rgb_base = hex_to_rgb(color_base)
    rgb_high = hex_to_rgb(color_highlight)
    gradient = Image.new("RGBA", size)
    draw = ImageDraw.Draw(gradient)
    max_dist = size[0] + size[1]
    for i in range(max_dist):
        ratio = i / max_dist
        curr = tuple(int(rgb_high[j] * (1 - ratio) + rgb_base[j] * ratio) for j in range(3))
        draw.line([(i, 0), (0, i)], fill=curr + (255,))
    return gradient


def process_textures(materials):
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    generated = skipped = missing = 0

    for mat in materials:
        mat_name   = mat["name"]
        colors     = mat["colors"]
        c_base     = colors["dark"]
        c_high     = colors["light"]
        brightness = colors.get("lum", 1.0)
        contrast   = colors.get("sat", 1.0)

        for tool in TOOLS:
            file_name     = f"{mat_name}_{tool}.png"
            save_path     = os.path.join(OUTPUT_DIR, file_name)
            template_path = os.path.join(TEMPLATE_DIR, f"default_{tool}.png")

            if os.path.exists(save_path):
                skipped += 1
                continue
            if not os.path.exists(template_path):
                print(f"[!] Template missing: {template_path}")
                missing += 1
                continue
            try:
                with Image.open(template_path).convert("RGBA") as img:
                    tint   = create_metal_gradient(img.size, c_base, c_high)
                    result = ImageChops.multiply(img, tint)
                    if brightness != 1.0:
                        result = ImageEnhance.Brightness(result).enhance(brightness)
                    if contrast != 1.0:
                        result = ImageEnhance.Contrast(result).enhance(contrast)
                    result.putalpha(img.getchannel("A"))
                    result.save(save_path)
                    print(f"[+] {mat_name.upper()} -> {file_name}")
                    generated += 1
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")

    print(f"\nTextures — Generated: {generated}  Skipped: {skipped}  Missing templates: {missing}")


def generate_recipes(materials):
    os.makedirs(RECIPE_DIR, exist_ok=True)
    generated = skipped = 0

    for mat in materials:
        mat_name = mat["name"]
        for tool, pattern in TOOL_RECIPES.items():
            file_name = f"{mat_name}_{tool}.json"
            save_path = os.path.join(RECIPE_DIR, file_name)
            if os.path.exists(save_path):
                skipped += 1
                continue
            data = {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "key": {
                    "X": f"omnitech:{mat_name}_ingot",
                    "S": "minecraft:stick",
                },
                "pattern": pattern,
                "result": {"count": 1, "id": f"omnitech:{mat_name}_{tool}"},
            }
            with open(save_path, "w") as f:
                json.dump(data, f, indent=2)
            print(f"[+] Recipe: {file_name}")
            generated += 1

    print(f"Recipes   — Generated: {generated}  Skipped: {skipped}")


if __name__ == "__main__":
    all_materials = load_materials()
    tool_mats     = [m for m in all_materials if m.get("tools") and m.get("colors")]
    process_textures(tool_mats)
    generate_recipes(tool_mats)
