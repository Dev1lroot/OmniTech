import os
import json
from PIL import Image, ImageChops, ImageEnhance, ImageDraw

# ── Paths ────────────────────────────────────────────────────────────────────
TEMPLATE_DIR  = "./templates/assets"
OUTPUT_DIR    = "./src/main/resources/assets/omnitech/textures/item"
RECIPE_DIR    = "./src/main/resources/data/omnitech/recipe"

# ── Materials: "name": (base_color, highlight_color, brightness, contrast) ──
# Matching the color palette from asset_factory.py for consistency.
MATERIALS = {
    "zinc":      ("#8D9FA3", "#C7D6D9", 1.0, 1.0),
    "lead":      ("#3D4259", "#7D87B0", 0.9, 1.1),
    "tin":       ("#9BA9AB", "#DDE9EB", 1.0, 1.1),
    "nickel":    ("#A8B59A", "#E6EFD3", 1.0, 1.1),
    "aluminium": ("#B2BFC2", "#FFFFFF", 1.1, 1.3),
    "brass":     ("#A68521", "#FDE68A", 1.1, 1.3),
    "cobalt":    ("#1E8A2B", "#006AFF", 1.0, 1.4),
    "steel":     ("#525252", "#A1A1A1", 1.0, 1.2),
    "uranium":   ("#3E523A", "#9DFF00", 1.1, 1.4),
    "titanium":  ("#6D6375", "#D9D0E3", 1.1, 1.2),
    "chromium":  ("#C0C9CC", "#FFFFFF", 1.2, 1.4),
    "tungsten":  ("#31363B", "#5E6770", 0.9, 1.2),
}

# Template basename → tool type output name pattern
# Templates should be placed in ./templates/assets/ as:
#   default_pickaxe.png, default_shovel.png, default_sword.png,
#   default_axe.png, default_hoe.png
TOOLS = [
    "pickaxe",
    "shovel",
    "sword",
    "axe",
    "hoe",
]


def hex_to_rgb(hex_str):
    hex_str = hex_str.lstrip('#')
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))


def create_metal_gradient(size, color_base, color_highlight):
    """Diagonal gradient from highlight (top-left) to base (bottom-right)."""
    rgb_base = hex_to_rgb(color_base)
    rgb_high = hex_to_rgb(color_highlight)

    gradient = Image.new('RGBA', size)
    draw = ImageDraw.Draw(gradient)

    max_dist = size[0] + size[1]
    for i in range(max_dist):
        ratio = i / max_dist
        curr_rgb = tuple(int(rgb_high[j] * (1 - ratio) + rgb_base[j] * ratio) for j in range(3))
        draw.line([(i, 0), (0, i)], fill=curr_rgb + (255,))

    return gradient


def process_textures():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    generated = 0
    skipped   = 0
    missing   = 0

    for mat_name, (c_base, c_high, brightness, contrast) in MATERIALS.items():
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

                    result.putalpha(img.getchannel('A'))
                    result.save(save_path)
                    print(f"[+] {mat_name.upper()} -> {file_name}")
                    generated += 1
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")

    print(f"\nDone. Generated: {generated}  Skipped: {skipped}  Missing templates: {missing}")
    if missing:
        print(f"Place tool template PNGs (16×16) at: {TEMPLATE_DIR}/default_<tool>.png")


# ── Crafting recipe patterns ──────────────────────────────────────────────────
# X = ingot, S = stick
TOOL_RECIPES = {
    "pickaxe":  {"pattern": ["XXX", " S ", " S "], "ingots": 3, "sticks": 2},
    "axe":      {"pattern": ["XX ", "XS ", " S "], "ingots": 3, "sticks": 2},
    "shovel":   {"pattern": [" X ", " S ", " S "], "ingots": 1, "sticks": 2},
    "hoe":      {"pattern": ["XX ", " S ", " S "], "ingots": 2, "sticks": 2},
    "sword":    {"pattern": [" X ", " X ", " S "], "ingots": 2, "sticks": 1},
}


def generate_recipes():
    os.makedirs(RECIPE_DIR, exist_ok=True)

    generated = 0
    skipped   = 0

    for mat_name in MATERIALS:
        for tool, recipe in TOOL_RECIPES.items():
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
                    "S": "minecraft:stick"
                },
                "pattern": recipe["pattern"],
                "result": {"count": 1, "id": f"omnitech:{mat_name}_{tool}"}
            }

            with open(save_path, "w") as f:
                json.dump(data, f, indent=2)
            print(f"[+] Recipe: {file_name}")
            generated += 1

    print(f"\nRecipes — Generated: {generated}  Skipped: {skipped}")


if __name__ == "__main__":
    process_textures()
    generate_recipes()
