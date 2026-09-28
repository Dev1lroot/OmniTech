#!/usr/bin/env python3
"""
mineral_texture_factory.py
==========================
Template-based mineral block textures:

    templates/assets/mineral_base.png      grey rock matrix
    templates/assets/mineral_layer{1..6}   speckle masks (flecks, dots, spots,
                                           crosses, dashes, chevrons)

Each mineral in minerals.yml is composed as
    base (kept grey | single-colour tint | multicolour gradient)
  + one or more layers, each gradient-tinted with 1..N colours,
    with its own brightness / contrast and a rotate/mirror transform.

A mineral may spell this out in an optional `texture:` section:

    texture:
      base:   { mode: gray|tint|gradient, colors: ["#..", ...], brightness: 1.0, contrast: 1.0 }
      layers:
        - { layer: 3, colors: ["#..", "#.."], brightness: 1.0, contrast: 1.1,
            transform: 0..7, gradient: luma|diagonal|vertical|horizontal|mixed }

Without one, the recipe is derived from `visuals` (base_layer colour → base,
each inclusion → one layer, `shape` picks the layer mask).

Version 0 is that recipe as-is; every regenerated version k > 0 is a
deterministic variation of it (other masks, transforms, gradient directions,
base mode, colour spread, brightness/contrast), so the same k always gives
the same picture.

Old textures made by mineral_assets_creator.py's noise generator are detected
by their pixel signature; anything else is treated as hand-made.

Usage:
    python3 mineral_texture_factory.py                 review each texture: OLD | NEW, y/n
    python3 mineral_texture_factory.py --auto          replace generated ones, keep hand-made
    python3 mineral_texture_factory.py --sheet out.png write a comparison sheet, change nothing
    python3 mineral_texture_factory.py --variants N    with --sheet: N versions per mineral
    python3 mineral_texture_factory.py name1 name2     restrict to these minerals

Review keys:
    y  replace with the shown version      n  keep the old texture
    r  regenerate (new version)            b  back to the previous version
    0-9 / number  jump to that version     a  accept all remaining generated ones
    q  quit
"""

import hashlib
import random
import sys
from pathlib import Path

import yaml
from PIL import Image, ImageEnhance

SCRIPT_DIR   = Path(__file__).parent
ROOT_DIR     = SCRIPT_DIR.parent
TEMPLATES    = ROOT_DIR / "templates/assets"
TEXTURE_PATH = ROOT_DIR / "src/main/resources/assets/omnitech/textures/block"
CONFIG_FILE  = SCRIPT_DIR / "minerals.yml"

LAYER_COUNT = 6
# visuals.inclusions[].shape → layer mask
SHAPE_LAYERS = {
    "cubic": 3, "botryoidal": 3,
    "needles": 5, "banded": 5,
    "prismatic": 6,
    "cross": 4,
}
FREE_LAYERS = [1, 2, 4, 6, 3, 5]
GRADIENTS = ["luma", "mixed", "diagonal", "vertical", "horizontal"]
MIN_LAYER_CONTRAST = 55  # min luma difference between an inclusion and the base


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb):
    return "#%02X%02X%02X" % tuple(max(0, min(255, int(v))) for v in rgb)


def luma(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def saturation(rgb):
    return (max(rgb) - min(rgb)) / 255


def shade(rgb, k):
    """k < 0 darkens toward black, k > 0 lightens toward white."""
    if k < 0:
        return tuple(int(v * (1 + k)) for v in rgb)
    return tuple(int(v + (255 - v) * k) for v in rgb)


def seed_of(*parts) -> int:
    return int(hashlib.md5("/".join(map(str, parts)).encode()).hexdigest()[:8], 16)


# ── Recipe ────────────────────────────────────────────────────────────────────

def base_recipe(mineral: dict) -> dict:
    """Version 0: `texture:` if present, else derived from `visuals`."""
    name = mineral["name"]
    explicit = mineral.get("texture")
    if explicit:
        return {"base": dict(explicit.get("base", {"mode": "gray"})),
                "layers": [dict(l) for l in explicit.get("layers", [])]}

    visuals = mineral.get("visuals", {})
    base_rgb = hex_to_rgb(visuals.get("base_layer", {}).get("color", "#7F7F7F"))
    # Near-neutral rocks keep the grey matrix, only shifting its brightness.
    if saturation(base_rgb) < 0.12:
        brightness = max(0.6, min(1.35, luma(base_rgb) / 120))
        base = {"mode": "gray", "brightness": brightness}
        base_l = 128 * brightness
    else:
        base = {"mode": "tint", "colors": [rgb_to_hex(base_rgb)], "brightness": 1.0}
        base_l = luma(base_rgb)

    layers, used = [], set()
    for i, inc in enumerate(visuals.get("inclusions", [])):
        rgb = hex_to_rgb(inc["color"])
        # keep specks readable: push them away from the matrix brightness
        if abs(luma(rgb) - base_l) < MIN_LAYER_CONTRAST:
            rgb = shade(rgb, 0.5) if base_l < 128 else shade(rgb, -0.5)
        layer = SHAPE_LAYERS.get(inc.get("shape"))
        if layer is None or layer in used:
            start = seed_of(name, i) % LAYER_COUNT
            layer = next(FREE_LAYERS[(start + j) % LAYER_COUNT] for j in range(LAYER_COUNT)
                         if FREE_LAYERS[(start + j) % LAYER_COUNT] not in used)
        used.add(layer)
        shading = inc.get("shading", "")
        spread = {"metallic": 0.55, "bright": 0.45, "glossy": 0.45, "glowing": 0.5,
                  "translucent": 0.35}.get(shading, 0.3)
        layers.append({
            "layer": layer,
            "colors": [rgb_to_hex(shade(rgb, -spread)), rgb_to_hex(rgb), rgb_to_hex(shade(rgb, spread))],
            "brightness": 1.0,
            "contrast": 1.15 if shading in ("metallic", "bright") else 1.0,
            "gradient": "mixed",
            "transform": seed_of(name, i) % 8,
            "_core": rgb,
        })
    return {"base": base, "layers": layers}


def vary(recipe: dict, name: str, version: int) -> dict:
    """Deterministic variation #version of a recipe (version 0 = unchanged)."""
    if version == 0:
        return recipe
    rng = random.Random(seed_of(name, "v", version))
    base = dict(recipe["base"])
    colors = base.get("colors") or []
    core = hex_to_rgb(colors[0]) if colors else None

    # base: flip between grey / tint / two-tone gradient
    mode = rng.choice(["gray", "tint", "gradient"] if core else ["gray", "gray", "tint"])
    if mode == "tint" and not core:
        core = (rng.randint(60, 140),) * 3
        core = tuple(max(0, min(255, c + rng.randint(-25, 25))) for c in core)
    if mode == "gray":
        base = {"mode": "gray", "brightness": base.get("brightness", 1.0) * rng.uniform(0.8, 1.2)}
    elif mode == "tint":
        base = {"mode": "tint", "colors": [rgb_to_hex(core)], "brightness": rng.uniform(0.85, 1.15)}
    else:
        hue_shift = [rng.randint(-30, 30) for _ in range(3)]
        second = tuple(c + d for c, d in zip(core, hue_shift))
        base = {"mode": "gradient", "gradient": rng.choice(GRADIENTS),
                "colors": [rgb_to_hex(shade(core, -0.5)), rgb_to_hex(core), rgb_to_hex(shade(second, 0.25))],
                "brightness": rng.uniform(0.85, 1.1)}
    base["contrast"] = rng.uniform(0.85, 1.3)

    masks = list(range(1, LAYER_COUNT + 1))
    rng.shuffle(masks)
    layers = []
    for i, layer in enumerate(recipe["layers"]):
        layer = dict(layer)
        core_rgb = layer.get("_core") or hex_to_rgb(layer["colors"][len(layer["colors"]) // 2])
        if rng.random() < 0.6:
            layer["layer"] = masks[i % LAYER_COUNT]
        layer["transform"] = rng.randrange(8)
        layer["gradient"] = rng.choice(GRADIENTS)
        spread = rng.uniform(0.2, 0.65)
        ramp = [shade(core_rgb, -spread), core_rgb, shade(core_rgb, spread)]
        if rng.random() < 0.35:  # multicolour: nudge the highlight toward another hue
            accent = tuple(max(0, min(255, c + rng.randint(-50, 50))) for c in core_rgb)
            ramp.append(shade(accent, spread * 0.8))
        layer["colors"] = [rgb_to_hex(c) for c in ramp]
        layer["brightness"] = rng.uniform(0.85, 1.2)
        layer["contrast"] = rng.uniform(0.9, 1.35)
        layers.append(layer)
    # sometimes sprinkle an extra fine layer in a variant of the first inclusion's colour
    if layers and rng.random() < 0.3:
        extra = dict(layers[0])
        extra["layer"] = rng.choice([1, 2])
        extra["transform"] = rng.randrange(8)
        extra["colors"] = [rgb_to_hex(shade(hex_to_rgb(extra["colors"][-1]), 0.2))] * 2
        layers.append(extra)
    return {"base": base, "layers": layers}


# ── Rendering ─────────────────────────────────────────────────────────────────

def transform(img: Image.Image, t: int) -> Image.Image:
    for _ in range(t % 4):
        img = img.transpose(Image.Transpose.ROTATE_90)
    if t >= 4:
        img = img.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return img


def gradient_map(t: float, colors):
    if len(colors) == 1:
        return colors[0]
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    k = t - i
    a, b = colors[i], colors[i + 1]
    return tuple(int(a[c] + (b[c] - a[c]) * k) for c in range(3))


def tint(img: Image.Image, colors, gradient="luma") -> Image.Image:
    """Map each pixel onto the colour ramp by template luminance and/or position."""
    rgbs = [hex_to_rgb(c) for c in colors]
    w, h = img.size
    px = img.load()
    lums = [luma(px[x, y]) for y in range(h) for x in range(w) if px[x, y][3]]
    lo, hi = (min(lums), max(lums)) if lums else (0, 1)
    out = Image.new("RGBA", img.size)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if not a:
                continue
            tl = (luma((r, g, b)) - lo) / (hi - lo) if hi > lo else 0.5
            if gradient == "luma":
                t = tl
            elif gradient == "mixed":
                t = 0.6 * tl + 0.4 * (1 - (x + y) / (w + h - 2))
            else:
                tp = {"diagonal": (x + y) / (w + h - 2), "vertical": y / (h - 1),
                      "horizontal": x / (w - 1)}[gradient]
                t = 0.5 * tl + 0.5 * tp  # keep some of the template's own shading
            out.putpixel((x, y), gradient_map(t, rgbs) + (a,))
    return out


def adjust(img: Image.Image, brightness=1.0, contrast=1.0) -> Image.Image:
    alpha = img.getchannel("A")
    if brightness != 1.0:
        img = ImageEnhance.Brightness(img).enhance(brightness)
    if contrast != 1.0:
        img = ImageEnhance.Contrast(img).enhance(contrast)
    img.putalpha(alpha)
    return img


def render(recipe: dict) -> Image.Image:
    base_cfg = recipe["base"]
    base = Image.open(TEMPLATES / "mineral_base.png").convert("RGBA")
    mode = base_cfg.get("mode", "gray")
    if mode == "tint":
        # single colour: ramp from a dark shade through the colour to a light shade
        c = hex_to_rgb(base_cfg["colors"][0])
        base = tint(base, [rgb_to_hex(shade(c, -0.45)), base_cfg["colors"][0], rgb_to_hex(shade(c, 0.3))], "luma")
    elif mode == "gradient":
        base = tint(base, base_cfg["colors"], base_cfg.get("gradient", "mixed"))
    base = adjust(base, base_cfg.get("brightness", 1.0), base_cfg.get("contrast", 1.0))

    for layer in recipe["layers"]:
        mask = Image.open(TEMPLATES / f"mineral_layer{layer['layer']}.png").convert("RGBA")
        mask = transform(mask, layer.get("transform", 0))
        top = tint(mask, layer["colors"], layer.get("gradient", "luma"))
        top = adjust(top, layer.get("brightness", 1.0), layer.get("contrast", 1.0))
        base.alpha_composite(top)
    return base


def render_version(mineral: dict, version: int) -> Image.Image:
    return render(vary(base_recipe(mineral), mineral["name"], version))


# ── Hand-made detection ───────────────────────────────────────────────────────

def looks_generated(img: Image.Image, mineral: dict) -> bool:
    """mineral_assets_creator.py painted colour + one shared noise value per pixel
    (±15 on the base, ±20 on inclusions). Hand-drawn textures don't fit that."""
    visuals = mineral.get("visuals", {})
    palette = [hex_to_rgb(visuals.get("base_layer", {}).get("color", "#000000"))]
    palette += [hex_to_rgb(i["color"]) for i in visuals.get("inclusions", [])]
    img = img.convert("RGBA")
    if img.size != (16, 16):
        return False
    hits = 0
    for r, g, b, a in img.get_flattened_data():
        for c in palette:
            d = [v - cv for v, cv in zip((r, g, b), c)]
            # clamping at 0/255 breaks the shared-noise equality, so ignore clamped channels
            free = [dv for dv, v in zip(d, (r, g, b)) if 0 < v < 255]
            if a == 255 and all(abs(dv) <= 20 for dv in d) and (not free or max(free) - min(free) <= 1):
                hits += 1
                break
    return hits >= 250


# ── Terminal preview ──────────────────────────────────────────────────────────

def ansi_rows(img: Image.Image, scale: int):
    img = img.convert("RGBA").resize((img.width * scale, img.height * scale), Image.Resampling.NEAREST)
    px = img.load()

    def col(x, y):
        r, g, b, a = px[x, y]
        if a < 128:  # checkerboard for transparency
            v = 60 if ((x // scale + y // scale) % 2) else 90
            return v, v, v
        return r, g, b

    rows = []
    for y in range(0, img.height, 2):
        line = ""
        for x in range(img.width):
            t, b = col(x, y), col(x, y + 1)
            line += f"\x1b[38;2;{t[0]};{t[1]};{t[2]}m\x1b[48;2;{b[0]};{b[1]};{b[2]}m▀"
        rows.append(line + "\x1b[0m")
    return rows


def show(name, old, new, generated, version, seen, scale=2):
    width = 16 * scale
    tag = "generated" if generated else "\x1b[33mHAND-MADE?\x1b[0m"
    if old is None:
        tag = "\x1b[32mnew\x1b[0m"
    print(f"\n\x1b[1m{name}\x1b[0m  ({tag})")
    print("OLD".ljust(width) + f"   NEW  v{version}  (seen: {', '.join(map(str, seen))})")
    left = ansi_rows(old, scale) if old else [" " * width] * (8 * scale)
    for l, r in zip(left, ansi_rows(new, scale)):
        print(l + "   " + r)


# ── Main ──────────────────────────────────────────────────────────────────────

def load_minerals():
    with open(CONFIG_FILE, encoding="utf-8") as f:
        return yaml.safe_load(f)["minerals"]


def write_sheet(path, rows):
    """rows: (old, [versions…]); OLD first, then each version."""
    s, gap = 64, 8
    cols = 1 + max(len(v) for _, v in rows)
    sheet = Image.new("RGBA", (cols * (s + gap), (s + 4) * len(rows)), (32, 32, 32, 255))
    for i, (old, versions) in enumerate(rows):
        y = i * (s + 4)
        if old:
            sheet.paste(old.convert("RGBA").resize((s, s), Image.Resampling.NEAREST), (0, y))
        for j, img in enumerate(versions, start=1):
            sheet.paste(img.resize((s, s), Image.Resampling.NEAREST), (j * (s + gap), y))
    sheet.save(path)


def review(mineral, old, generated):
    """Interactive loop; returns (answer, image)."""
    name = mineral["name"]
    cache = {0: render_version(mineral, 0)}
    history, version, newest = [0], 0, 0
    while True:
        show(name, old, cache[version], generated, version, sorted(cache))
        ans = input("replace? [y/n  r=regenerate b=back <num>=version  a/q] ").strip().lower()
        if ans == "r":
            newest += 1
            version = newest
        elif ans == "b":
            if len(history) > 1:
                history.pop()
            version = history[-1]
            continue
        elif ans.isdigit():
            version = int(ans)
            newest = max(newest, version)
        else:
            return ans, cache[version]
        if version not in cache:
            cache[version] = render_version(mineral, version)
        history.append(version)


def main(argv):
    auto = "--auto" in argv
    sheet = argv[argv.index("--sheet") + 1] if "--sheet" in argv else None
    variants = int(argv[argv.index("--variants") + 1]) if "--variants" in argv else 1
    skip = {sheet, str(variants)} if "--variants" in argv else {sheet}
    only = {a for a in argv if not a.startswith("--") and a not in skip}

    minerals = [m for m in load_minerals() if not only or m["name"] in only]
    accept_all, sheet_rows = False, []
    replaced = kept = 0
    for mineral in minerals:
        name = mineral["name"]
        path = TEXTURE_PATH / f"{name}.png"
        old = Image.open(path).convert("RGBA") if path.exists() else None
        generated = old is None or looks_generated(old, mineral)

        if sheet:
            sheet_rows.append((old, [render_version(mineral, v) for v in range(variants)]))
            print(f"{name:16} {'generated' if generated else 'hand-made?'}")
            continue
        if auto or (accept_all and generated):
            if generated:
                render_version(mineral, 0).save(path)
                replaced += 1
            else:
                kept += 1
            continue

        ans, new = review(mineral, old, generated)
        if ans == "q":
            break
        if ans == "a":
            accept_all = True
            ans = "y" if generated else "n"
        if ans == "y":
            new.save(path)
            replaced += 1
        else:
            kept += 1

    if sheet:
        write_sheet(sheet, sheet_rows)
        print(f"sheet → {sheet}")
    else:
        print(f"\n{replaced} replaced, {kept} kept")


if __name__ == "__main__":
    main(sys.argv[1:])
