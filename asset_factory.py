#!/usr/bin/env python3
"""
asset_factory.py
================
Generates item, block, and molten-fluid textures for all materials defined in
materials.yml that have a `colors` entry.

Applies a diagonal metallic gradient using the material's dark/light colours,
then adjusts brightness (lum) and contrast (sat).

Rules:
  • PNG files are NEVER overwritten (texture may have been hand-edited).
  • MCMeta files are also never overwritten.

Usage:
    python3 asset_factory.py
"""

import os
import shutil
from pathlib import Path
from PIL import Image, ImageChops, ImageEnhance, ImageDraw
import yaml

SCRIPT_DIR       = Path(__file__).parent
TEMPLATE_DIR     = SCRIPT_DIR / "templates/assets"
OUTPUT_DIR       = SCRIPT_DIR / "src/main/resources/assets/omnitech/textures/item"
BLOCK_OUTPUT_DIR = SCRIPT_DIR / "src/main/resources/assets/omnitech/textures/block"
FLUID_OUTPUT_DIR = SCRIPT_DIR / "src/main/resources/assets/omnitech/textures/block/fluid"

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


def tint_image(template_path: Path, c_base: str, c_high: str,
               brightness: float, contrast: float) -> Image.Image:
    with Image.open(template_path).convert("RGBA") as img:
        tint   = create_metal_gradient(img.size, c_base, c_high)
        result = ImageChops.multiply(img, tint)
        if brightness != 1.0:
            result = ImageEnhance.Brightness(result).enhance(brightness)
        if contrast != 1.0:
            result = ImageEnhance.Contrast(result).enhance(contrast)
        result.putalpha(img.getchannel("A"))
        return result.copy()


def process_item_and_block_textures(materials):
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    BLOCK_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    for mat in materials:
        colors = mat.get("colors")
        if not colors:
            continue

        mat_name   = mat["name"]
        c_base     = colors["dark"]
        c_high     = colors["light"]
        brightness = colors.get("lum", 1.0)
        contrast   = colors.get("sat", 1.0)

        for pattern in VARIATIONS:
            file_name  = pattern.replace("%", mat_name) + ".png"
            is_block   = any(x in pattern for x in ["ore", "_block"])
            save_path  = (BLOCK_OUTPUT_DIR if is_block else OUTPUT_DIR) / file_name

            if save_path.exists():
                continue

            template_suffix = pattern.replace("%", "default")
            template_path   = TEMPLATE_DIR / f"{template_suffix}.png"

            if not template_path.exists():
                continue

            try:
                result = tint_image(template_path, c_base, c_high, brightness, contrast)
                result.save(str(save_path))
                print(f"[+] {mat_name.upper()} -> {file_name}")
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")


def process_fluid_textures(materials):
    FLUID_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    still_template = TEMPLATE_DIR / "default_still.png"
    flow_template  = TEMPLATE_DIR / "default_flow.png"
    still_mcmeta   = TEMPLATE_DIR / "default_still.png.mcmeta"
    flow_mcmeta    = TEMPLATE_DIR / "default_flow.png.mcmeta"

    for mat in materials:
        if mat.get("melt_temp") is None:
            continue
        colors = mat.get("colors")
        if not colors:
            continue

        mat_name   = mat["name"]
        fluid_name = f"molten_{mat_name}"
        c_base     = colors["dark"]
        c_high     = colors["light"]
        brightness = colors.get("lum", 1.0)
        contrast   = colors.get("sat", 1.0)

        for template, suffix in [(still_template, "still"), (flow_template, "flow")]:
            png_path   = FLUID_OUTPUT_DIR / f"{fluid_name}_{suffix}.png"
            mcmeta_src = TEMPLATE_DIR / f"default_{suffix}.png.mcmeta"
            mcmeta_dst = FLUID_OUTPUT_DIR / f"{fluid_name}_{suffix}.png.mcmeta"

            if not png_path.exists():
                if template.exists():
                    try:
                        result = tint_image(template, c_base, c_high, brightness, contrast)
                        result.save(str(png_path))
                        print(f"[+] fluid {fluid_name}_{suffix}.png")
                    except Exception as e:
                        print(f"[X] Error fluid {fluid_name}_{suffix}: {e}")

            if not mcmeta_dst.exists() and mcmeta_src.exists():
                shutil.copy2(mcmeta_src, mcmeta_dst)
                print(f"[+] fluid {fluid_name}_{suffix}.png.mcmeta")


if __name__ == "__main__":
    materials = load_materials()
    process_item_and_block_textures(materials)
    process_fluid_textures(materials)
