import os
import json
from PIL import Image, ImageChops, ImageEnhance, ImageDraw

# ── Paths ────────────────────────────────────────────────────────────────────
TEMPLATE_DIR        = "./templates/assets"
ITEM_OUTPUT_DIR     = "./src/main/resources/assets/omnitech/textures/item"
# Humanoid layer textures (main body — helmet/chest/boots)
HUMANOID_OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/entity/equipment/humanoid"
# Leggings use a separate layer texture
LEGGINGS_OUTPUT_DIR = "./src/main/resources/assets/omnitech/textures/entity/equipment/humanoid_leggings"
RECIPE_DIR          = "./src/main/resources/data/omnitech/recipe"

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

# ── Armor piece item icons ────────────────────────────────────────────────────
# Template basename → output name pattern
# Templates: ./templates/assets/default_helmet.png, etc.
ARMOR_PIECES = [
    "helmet",
    "chestplate",
    "leggings",
    "boots",
]

# ── Armor layer textures ──────────────────────────────────────────────────────
# These are the 64×32 (or 64×64 in 1.8+) humanoid armor overlay textures.
# Template: ./templates/assets/default_armor_layer1.png  (main body layer)
#           ./templates/assets/default_armor_layer2.png  (leggings layer)
ARMOR_LAYERS = [
    # (template_suffix, output_suffix, output_directory)
    ("armor_layer1", "_armor",          HUMANOID_OUTPUT_DIR),
    ("armor_layer2", "_armor_leggings", LEGGINGS_OUTPUT_DIR),
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


def tint_image(template_path, c_base, c_high, brightness, contrast):
    """Apply material tint to template, return PIL Image or None on error."""
    with Image.open(template_path).convert("RGBA") as img:
        tint   = create_metal_gradient(img.size, c_base, c_high)
        result = ImageChops.multiply(img, tint)

        if brightness != 1.0:
            result = ImageEnhance.Brightness(result).enhance(brightness)
        if contrast != 1.0:
            result = ImageEnhance.Contrast(result).enhance(contrast)

        result.putalpha(img.getchannel('A'))
        return result.copy()


def process_textures():
    for d in [ITEM_OUTPUT_DIR, HUMANOID_OUTPUT_DIR, LEGGINGS_OUTPUT_DIR]:
        os.makedirs(d, exist_ok=True)

    generated = 0
    skipped   = 0
    missing   = 0

    for mat_name, (c_base, c_high, brightness, contrast) in MATERIALS.items():

        # ── Item icon textures (inventory sprites) ────────────────────────────
        for piece in ARMOR_PIECES:
            file_name     = f"{mat_name}_{piece}.png"
            save_path     = os.path.join(ITEM_OUTPUT_DIR, file_name)
            template_path = os.path.join(TEMPLATE_DIR, f"default_{piece}.png")

            if os.path.exists(save_path):
                skipped += 1
                continue

            if not os.path.exists(template_path):
                print(f"[!] Template missing: {template_path}")
                missing += 1
                continue

            try:
                img = tint_image(template_path, c_base, c_high, brightness, contrast)
                img.save(save_path)
                print(f"[+] {mat_name.upper()} -> {file_name}")
                generated += 1
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")

        # ── Humanoid layer textures (worn-on-body overlays) ───────────────────
        for tpl_suffix, out_suffix, out_dir in ARMOR_LAYERS:
            file_name     = f"{mat_name}{out_suffix}.png"
            save_path     = os.path.join(out_dir, file_name)
            template_path = os.path.join(TEMPLATE_DIR, f"default_{tpl_suffix}.png")

            if os.path.exists(save_path):
                skipped += 1
                continue

            if not os.path.exists(template_path):
                print(f"[!] Template missing: {template_path}")
                missing += 1
                continue

            try:
                img = tint_image(template_path, c_base, c_high, brightness, contrast)
                img.save(save_path)
                print(f"[+] {mat_name.upper()} -> {file_name}")
                generated += 1
            except Exception as e:
                print(f"[X] Error {file_name}: {e}")

    print(f"\nDone. Generated: {generated}  Skipped: {skipped}  Missing templates: {missing}")
    if missing:
        print("\nRequired templates (in ./templates/assets/):")
        print("  Item icons (16×16 PNG):")
        for p in ARMOR_PIECES:
            print(f"    default_{p}.png")
        print("  Armor layer textures (64×32 or 64×64 PNG):")
        print("    default_armor_layer1.png  — main body layer (helmet/chest/boots)")
        print("    default_armor_layer2.png  — leggings layer")


# ── Crafting recipe patterns ──────────────────────────────────────────────────
# X = ingot
ARMOR_RECIPES = {
    "helmet":     {"pattern": ["XXX", "X X", "   "]},
    "chestplate": {"pattern": ["X X", "XXX", "XXX"]},
    "leggings":   {"pattern": ["XXX", "X X", "X X"]},
    "boots":      {"pattern": ["   ", "X X", "X X"]},
}


def generate_recipes():
    os.makedirs(RECIPE_DIR, exist_ok=True)

    generated = 0
    skipped   = 0

    for mat_name in MATERIALS:
        for piece, recipe in ARMOR_RECIPES.items():
            file_name = f"{mat_name}_{piece}.json"
            save_path = os.path.join(RECIPE_DIR, file_name)

            if os.path.exists(save_path):
                skipped += 1
                continue

            data = {
                "type": "minecraft:crafting_shaped",
                "category": "equipment",
                "key": {
                    "X": f"omnitech:{mat_name}_ingot"
                },
                "pattern": recipe["pattern"],
                "result": {"count": 1, "id": f"omnitech:{mat_name}_{piece}"}
            }

            with open(save_path, "w") as f:
                json.dump(data, f, indent=2)
            print(f"[+] Recipe: {file_name}")
            generated += 1

    print(f"\nRecipes — Generated: {generated}  Skipped: {skipped}")


if __name__ == "__main__":
    process_textures()
    generate_recipes()
