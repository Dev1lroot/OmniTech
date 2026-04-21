#!/usr/bin/env python3
"""
asset_factory.py
================
Generates item and block textures for all materials defined in materials.yml
that have a `colors` entry.

Applies a diagonal metallic gradient using the material's dark/light colours,
then adjusts brightness (lum) and contrast (sat).

Usage:
    python3 asset_factory.py
"""

import os
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance, ImageDraw
import yaml

SCRIPT_DIR       = Path(__file__).parent
TEMPLATE_DIR     = str(SCRIPT_DIR / "templates/assets")
OUTPUT_DIR       = str(SCRIPT_DIR / "src/main/resources/assets/omnitech/textures/item")
BLOCK_OUTPUT_DIR = str(SCRIPT_DIR / "src/main/resources/assets/omnitech/textures/block")

VARIATIONS = [
    "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote",
    "%_nugget", "%_reductor", "%_cog", "%_wire",
    "%_coil", "%_rod", "%_ore", "%_block", "raw_%_block",
]


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


def process_textures():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(BLOCK_OUTPUT_DIR, exist_ok=True)

    materials = load_materials()

    for mat in materials:
        colors = mat.get("colors")
        if not colors:
            continue

        mat_name = mat["name"]
        c_base   = colors["dark"]
        c_high   = colors["light"]
        brightness = colors.get("lum", 1.0)
        contrast   = colors.get("sat", 1.0)

        for pattern in VARIATIONS:
            file_name = pattern.replace("%", mat_name) + ".png"
            is_block  = any(x in pattern for x in ["ore", "_block"])
            save_path = os.path.join(BLOCK_OUTPUT_DIR if is_block else OUTPUT_DIR, file_name)

            if os.path.exists(save_path):
                continue

            template_suffix = pattern.replace("%", "default")
            template_path   = os.path.join(TEMPLATE_DIR, f"{template_suffix}.png")

            if not os.path.exists(template_path):
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
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")


if __name__ == "__main__":
    process_textures()
