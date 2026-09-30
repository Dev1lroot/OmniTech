#!/usr/bin/env python3
"""
bore_factory.py
===============
Everything the modular Bore needs, generated from materials.yml plus the tables below:

  • Bore heads — one per metal/alloy that has an ingot (+ vanilla iron/copper/gold/netherite):
      data/omnitech/bore_head/<mat>.json     stats read by BoreLoader (tier, speed, durability…)
      item definition showing the 3D head model (below), lang, crafting recipe, repair tag
  • Batteries — one per chemistry in BATTERIES:
      data/omnitech/battery/<id>.json        capacity, cell voltage, electrode formulas
      textures/item/<id>_battery.png         battery_base + tinted battery_anode/battery_cathode
      models + item definitions, lang, crafting recipe
  • The bore itself — a 3D model in every view (GUI included), item model type omnitech:bore:
      models/item/bore.json                  static corpus: motor housing + chuck (bore.png)
      models/item/bore_head_3d.json          head: front + rear cutting disks ("#bit")
      models/item/bore_head_3d_middle.json   head: middle disk (counter-rotates)
      models/item/bore_head_3d/<mat>[_middle].json  per head, texture textures/item/bore_head_3d/<mat>.png
    Outer disks spin one way and the middle disk the other while the holder mines
    (client/BoreItemModel).
    Geometry runs along -Z (tip north) around x = y = 8; BORE_DISPLAY turns it forward per view.
  • Tags: omnitech:bore_heads, omnitech:batteries, minecraft:enchantable/durability
    (Unbreaking + Mending apply to heads only).

Head stats come from real Vickers hardness (MPa):
    tier        <150 wood · <550 stone · <1500 iron · <5000 diamond · netherite
    speed       2 + 2.2·log2(HV/100)              (iron ≈ 7.7, tungsten ≈ 13, WC ≈ 19)
    durability  240·(HV/100)^0.7 blocks            (iron ≈ 850)
    energy      6 − 0.6·log2(HV/100) kJ per block  (soft heads waste energy)

Rules: PNG files are never overwritten (delete one to regenerate it); JSON is always rewritten.

Usage:
    python3 bore_factory.py
"""

import glob
import json
import math
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageEnhance
import yaml

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
ASSETS     = RES / "assets/omnitech"
TEMPLATES  = ROOT_DIR / "templates/assets"
TEX        = ASSETS / "textures/item"

# ── Vickers hardness, MPa ────────────────────────────────────────────────────
# Measured values where they exist; synthetic/superheavy elements are estimated
# from their lighter group homologues.
HARDNESS = {
    "lithium": 5, "sodium": 1, "potassium": 1, "rubidium": 1, "cesium": 1, "francium": 1,
    "beryllium": 1670, "magnesium": 45, "calcium": 170, "strontium": 100, "barium": 100, "radium": 100,
    "scandium": 750, "yttrium": 589, "titanium": 970, "zirconium": 903, "hafnium": 1760,
    "vanadium": 628, "niobium": 1320, "tantalum": 873, "chromium": 1060, "molybdenum": 1530,
    "tungsten": 3430, "manganese": 1000, "technetium": 1500, "rhenium": 2450,
    "iron": 608, "ruthenium": 2298, "osmium": 3920, "cobalt": 1043, "rhodium": 1246,
    "iridium": 1760, "nickel": 638, "palladium": 461, "platinum": 549,
    "copper": 369, "silver": 251, "gold": 216, "zinc": 330, "cadmium": 203,
    "aluminium": 167, "gallium": 56, "indium": 9, "thallium": 26, "tin": 50, "lead": 38,
    "bismuth": 94, "polonium": 100,
    "lanthanum": 491, "cerium": 270, "praseodymium": 400, "neodymium": 343, "promethium": 400,
    "samarium": 412, "europium": 167, "gadolinium": 570, "terbium": 863, "dysprosium": 540,
    "holmium": 481, "erbium": 589, "thulium": 520, "ytterbium": 206, "lutetium": 1160,
    "actinium": 400, "thorium": 350, "protactinium": 600, "uranium": 1960, "neptunium": 1000,
    "plutonium": 1500, "americium": 400, "curium": 400, "berkelium": 400, "californium": 400,
    "einsteinium": 300, "fermium": 300, "mendelevium": 300, "nobelium": 300, "lawrencium": 300,
    "rutherfordium": 1500, "dubnium": 1000, "seaborgium": 3000, "bohrium": 2500,
    "hassium": 3500, "meitnerium": 2000, "darmstadtium": 1000, "roentgenium": 300,
    "nihonium": 50, "moscovium": 100, "livermorium": 100,
    # alloys & ceramics
    "steel": 1400, "brass": 1000, "netherite": 6000,
    "tungsten_carbide": 22000, "hafnium_carbide": 20000, "hafnium_tantalum_carbide": 25000,
}

ENCHANTABILITY = {
    "gold": 22, "silver": 20, "platinum": 18, "palladium": 16, "rhodium": 16, "netherite": 15,
    "iron": 14, "iridium": 14, "copper": 13, "brass": 18, "steel": 14,
    "tungsten_carbide": 5, "hafnium_carbide": 5, "hafnium_tantalum_carbide": 5,
}

# Materials without a materials.yml entry
EXTRA_HEADS = {
    "netherite": {"formula": "", "ingot": "minecraft:netherite_ingot", "burns": False,
                  "colors": {"dark": "#2B2427", "light": "#5C5256", "lum": 1.0, "sat": 1.1}},
}

SKIP_HEADS = {"enriched_uranium"}   # reactor fuel, not a structural metal

# ── Batteries ────────────────────────────────────────────────────────────────
# id: (display name, anode formula, cathode formula, cell V, specific energy Wh/kg,
#      anode colour, cathode colour, recipe anode, recipe cathode, recipe electrolyte)
# Pack capacity (kJ) = specific energy × 100.
BATTERIES = {
    "lead_acid":              ("Lead-Acid",            "Pb",        "PbO₂",        2.0, 35,  "#5B6070", "#5A3A28", "omnitech:lead_plate",      "omnitech:lead_dust",       "omnitech:sulfur_dust"),
    "nickel_iron":            ("Nickel-Iron",          "Fe",        "NiOOH",       1.2, 30,  "#8A8A8A", "#4E6A42", "omnitech:iron_plate",      "omnitech:nickel_dust",     "omnitech:potassium_dust"),
    "nickel_cadmium":         ("Nickel-Cadmium",       "Cd",        "NiOOH",       1.2, 50,  "#B8B8CC", "#4E6A42", "omnitech:cadmium_plate",   "omnitech:nickel_dust",     "omnitech:potassium_dust"),
    "nickel_zinc":            ("Nickel-Zinc",          "Zn",        "NiOOH",       1.65, 70, "#A8B8C8", "#4E6A42", "omnitech:zinc_plate",      "omnitech:nickel_dust",     "omnitech:potassium_dust"),
    "nickel_metal_hydride":   ("Nickel-Metal Hydride", "LaNi₅H₆",   "NiOOH",       1.2, 80,  "#9CA88C", "#4E6A42", "omnitech:lanthanum_plate", "omnitech:nickel_dust",     "omnitech:potassium_dust"),
    "alkaline":               ("Rechargeable Alkaline", "Zn",       "MnO₂",        1.5, 60,  "#A8B8C8", "#2E2A2A", "omnitech:zinc_dust",       "omnitech:pyrolusite_dust", "omnitech:potassium_dust"),
    "silver_zinc":            ("Silver-Zinc",          "Zn",        "Ag₂O",        1.55, 130, "#A8B8C8", "#3A3434", "omnitech:zinc_plate",      "omnitech:silver_dust",     "omnitech:potassium_dust"),
    "sodium_sulfur":          ("Sodium-Sulfur",        "Na",        "S",           2.0, 150, "#D8D8C8", "#E8D040", "omnitech:halite_dust",     "omnitech:sulfur_dust",     "omnitech:aluminium_dust"),
    "sodium_ion":             ("Sodium-Ion",           "C",         "NaMnO₂",      3.1, 140, "#2A2A2A", "#7A5A7A", "omnitech:coal_dust",       "omnitech:manganese_dust",  "omnitech:halite_dust"),
    "lithium_titanate":       ("Lithium Titanate",     "Li₄Ti₅O₁₂", "LiNiMnCoO₂",  2.4, 80,  "#E0E0E8", "#3A4A3A", "omnitech:titanium_dust",   "omnitech:nickel_dust",     "omnitech:lithium_dust"),
    "lithium_iron_phosphate": ("Lithium Iron Phosphate", "C",       "LiFePO₄",     3.2, 160, "#2A2A2A", "#5A6A7A", "omnitech:graphite_dust",   "omnitech:iron_dust",       "omnitech:lithium_dust"),
    "lithium_cobalt_oxide":   ("Lithium Cobalt Oxide", "C",         "LiCoO₂",      3.7, 200, "#2A2A2A", "#2A3A7A", "omnitech:graphite_dust",   "omnitech:cobalt_dust",     "omnitech:lithium_dust"),
    "lithium_nmc":            ("Lithium NMC",          "C",         "LiNiMnCoO₂",  3.6, 250, "#2A2A2A", "#3A4A3A", "omnitech:graphite_dust",   "omnitech:nickel_dust",     "omnitech:lithium_dust"),
    "lithium_sulfur":         ("Lithium-Sulfur",       "Li",        "S",           2.1, 400, "#D0D0D0", "#E8D040", "omnitech:lithium_plate",   "omnitech:sulfur_dust",     "omnitech:lithium_dust"),
    "solid_state":            ("Solid-State Lithium",  "Li",        "LiNiMnCoO₂",  3.8, 500, "#D0D0D0", "#3A4A3A", "omnitech:lithium_plate",   "omnitech:nickel_dust",     "omnitech:zirconium_dust"),
}

BATTERY_CASING = "omnitech:aluminium_plate"
BATTERY_WIRE   = "omnitech:copper_wire"

BORE_RECIPE = {
    "pattern": [" PP", "WGP", "RW "],
    "key": {"P": "omnitech:steel_plate", "W": "omnitech:copper_wire",
            "G": "omnitech:steel_cog", "R": "omnitech:steel_rod"},
}


# ── helpers ──────────────────────────────────────────────────────────────────

def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_lang(entries: dict):
    path = ASSETS / "lang/en_us.json"
    text = path.read_text(encoding="utf-8")
    lang = json.loads(text)
    missing = {k: v for k, v in entries.items() if k not in lang}
    if not missing:
        return
    body = text.rstrip().removesuffix("}").rstrip()
    lines = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in missing.items())
    path.write_text(f"{body},\n\n{lines}\n}}\n", encoding="utf-8")


def known_items() -> set:
    items = {"omnitech:" + Path(f).stem for f in glob.glob(str(DATA / "item/*.json"))}
    return items


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def metal_gradient(size, dark, light):
    lo, hi = hex_rgb(dark), hex_rgb(light)
    img = Image.new("RGBA", size)
    draw = ImageDraw.Draw(img)
    n = size[0] + size[1]
    for i in range(n):
        r = i / n
        draw.line([(i, 0), (0, i)], fill=tuple(int(hi[j] * (1 - r) + lo[j] * r) for j in range(3)) + (255,))
    return img


def tint_metal(template: Image.Image, colors) -> Image.Image:
    tint = metal_gradient(template.size, colors["dark"], colors["light"])
    out = ImageChops.multiply(template, tint)
    if colors.get("lum", 1.0) != 1.0:
        out = ImageEnhance.Brightness(out).enhance(colors["lum"])
    if colors.get("sat", 1.0) != 1.0:
        out = ImageEnhance.Contrast(out).enhance(colors["sat"])
    out.putalpha(template.getchannel("A"))
    return out


def tint_flat(template: Image.Image, color) -> Image.Image:
    solid = Image.new("RGBA", template.size, hex_rgb(color) + (255,))
    out = ImageChops.multiply(template, solid)
    out.putalpha(template.getchannel("A"))
    return out


def title(name: str) -> str:
    return " ".join(w.capitalize() for w in name.split("_"))


def handheld(texture):
    return {"parent": "minecraft:item/handheld", "textures": {"layer0": texture}}


def generated(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def item_def(model):
    return {"model": {"type": "minecraft:model", "model": model}}


# ── head stats ───────────────────────────────────────────────────────────────

def head_stats(name, hv):
    x = math.log2(hv / 100.0)
    if hv < 150:    tier = "wood"
    elif hv < 550:  tier = "stone"
    elif hv < 1500: tier = "iron"
    elif hv < 5000: tier = "diamond"
    else:           tier = "netherite"
    return {
        "tier": tier,
        "hardness": hv,
        "speed": round(max(1.5, min(20.0, 2 + 2.2 * x)), 1),
        "durability": max(16, int(round(240 * (hv / 100.0) ** 0.7 / 10.0)) * 10),
        "energy_per_block": max(1, min(8, int(round(6 - 0.6 * x)))),
        "enchantability": ENCHANTABILITY.get(name, 10),
    }


def collect_heads():
    mats = yaml.safe_load((SCRIPT_DIR / "materials.yml").read_text(encoding="utf-8"))["materials"]
    heads = {}
    for m in mats:
        name = m["name"]
        if name in SKIP_HEADS or not m.get("colors"):
            continue
        if "vanilla_id" in m:
            ingot = m["vanilla_id"]
        elif "%_ingot" in (m.get("items") or []):
            ingot = f"omnitech:{name}_ingot"
        else:
            continue
        heads[name] = {"formula": m.get("formula", ""), "ingot": ingot,
                       "burns": m.get("burns", True), "colors": m["colors"]}
    heads.update(EXTRA_HEADS)
    return heads


# ── 3D bore models ───────────────────────────────────────────────────────────
# The drill axis is the model's -Z (north): in first person that is straight ahead, and
# the display transforms below turn it forward for the other views.
# Along the axis (px, tip → rear): front disk (6 px) -3.5..-1, middle disk (8 px) -0.5..2,
# rear disk (10 px) 2.5..5, chuck 5..8, housing 8..19; the pistol grip hangs under the housing.
# Side textures are drawn upright (texture top = tip end); per-face rotations lay them along Z.

def element(frm, to, side_uv, end_uv, texture, side_uv_y=None):
    """Box along Z. side_uv maps the up/down faces (as wide as the box's X size), side_uv_y
    the east/west faces (as wide as its Y size; defaults to side_uv)."""
    side = lambda uv, rot: {"uv": uv, "texture": texture, "rotation": rot}
    uv_y = side_uv_y or side_uv
    return {"from": frm, "to": to, "faces": {
        "up":    side(side_uv, 0),
        "down":  side(side_uv, 180),
        "east":  side(uv_y, 90),
        "west":  side(uv_y, 270),
        "north": {"uv": end_uv, "texture": texture},      # tip-facing end
        "south": {"uv": end_uv, "texture": texture},      # rear end
    }}


def disk(size, z0, z1, face_uv, rim_uv, texture):
    """A rounded-square disk `size` px across: a size×(size-2) and a (size-2)×size cuboid
    crossed on the axis — reads as a disk once spinning. face_uv is an 8×8 region and rim_uv
    an 8-wide one; both are stretched to the disk's size."""
    lo, hi = 8 - size / 2, 8 + size / 2
    u0, v0, u1, v1 = face_uv
    r0, rv0, r1, rv1 = rim_uv
    inset = 8 / size          # one model pixel, in texture pixels of the 8-px regions
    face_wide = [u0, v0 + inset, u1, v1 - inset]
    face_tall = [u0 + inset, v0, u1 - inset, v1]
    rim_short = [r0 + inset, rv0, r1 - inset, rv1]
    return [
        element([lo, lo + 1, z0], [hi, hi - 1, z1], rim_uv, face_wide, texture, side_uv_y=rim_short),
        element([lo + 1, lo, z0], [hi - 1, hi, z1], rim_short, face_tall, texture, side_uv_y=rim_uv),
    ]


# Disks taper toward the tip: (size px, z from, z to)
DISK_REAR, DISK_MIDDLE, DISK_FRONT = (10, 2.5, 5), (8, -0.5, 2), (6, -3.5, -1)
HEAD_CENTER_Z = (DISK_FRONT[1] + DISK_REAR[2]) / 2   # px along the axis

HEAD_ITEM_DISPLAY = {
    "gui":    {"rotation": [30, 225, 0], "scale": [0.9, 0.9, 0.9]},
    "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed":  {"scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand":  {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand":  {"rotation": [0, 45, 0], "scale": [0.6, 0.6, 0.6]},
}


def head_item_def(name):
    """A loose bore head shows its 3D model (all three disks), centred in the item space."""
    shift = (8 - HEAD_CENTER_Z) / 16.0
    return {"model": {
        "type": "minecraft:composite",
        "transformation": {"translation": [0, 0, shift], "left_rotation": [0, 0, 0, 1],
                           "scale": [1, 1, 1], "right_rotation": [0, 0, 0, 1]},
        "models": [
            {"type": "minecraft:model", "model": f"omnitech:item/bore_head_3d/{name}"},
            {"type": "minecraft:model", "model": f"omnitech:item/bore_head_3d/{name}_middle"},
        ],
    }}


# Same values for both hands — ItemTransform mirrors the left hand itself.
_FIRST = {"rotation": [3, 6, 0], "translation": [0, 0, -3], "scale": [0.8, 0.8, 0.8]}
_THIRD = {"rotation": [90, 0, 0], "translation": [0, 4, 1], "scale": [0.9, 0.9, 0.9]}
BORE_DISPLAY = {
    "firstperson_righthand": _FIRST, "firstperson_lefthand": _FIRST,
    "thirdperson_righthand": _THIRD, "thirdperson_lefthand": _THIRD,
    "gui":    {"rotation": [60, -40, 0], "scale": [0.7, 0.7, 0.7]},     # tip up-right, tilted away
    "fixed":  {"rotation": [90, -45, 0], "scale": [0.7, 0.7, 0.7]},     # tip up-right, in the frame
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
}


def corpus_model():
    t = "#corpus"
    grip = {f: {"uv": [10, 6, 13, 12], "texture": t} for f in ("north", "south", "east", "west")}
    grip["up"] = grip["down"] = {"uv": [13, 6, 16, 9], "texture": t}
    return {
        "textures": {"corpus": "omnitech:item/bore", "particle": "omnitech:item/bore"},
        "elements": [
            element([5, 5, 8], [11, 11, 19], [0, 0, 6, 11], [6, 0, 12, 6], t),     # motor housing
            element([6, 6, 5], [10, 10, 8], [6, 6, 10, 9], [12, 0, 16, 4], t),     # chuck
            {"from": [6.5, -1, 13], "to": [9.5, 5, 16], "faces": grip},            # pistol grip
        ],
        "display": BORE_DISPLAY,
    }


def head_3d_outer():
    """Front + rear disks — spin with the drive."""
    return {
        "textures": {"particle": "#bit"},
        "elements": disk(*DISK_FRONT, [0, 0, 8, 8], [8, 0, 16, 2.5], "#bit")
                    + disk(*DISK_REAR, [0, 0, 8, 8], [8, 0, 16, 2.5], "#bit"),
        # used when the head is a loose item; on the bore the corpus transforms apply
        "display": HEAD_ITEM_DISPLAY,
    }


def head_3d_middle():
    """Middle disk — counter-rotates."""
    return {
        "textures": {"particle": "#bit"},
        "elements": disk(*DISK_MIDDLE, [0, 8, 8, 16], [8, 4, 16, 6.5], "#bit"),
        "display": HEAD_ITEM_DISPLAY,
    }


def corpus_texture() -> Image.Image:
    """16×16: housing side 6×11 at (0,0), housing ends 6×6 at (6,0), chuck side 4×3 at (6,6),
    chuck ends 4×4 at (12,0), grip side 3×6 at (10,6), grip ends 3×3 at (13,6).
    Texture top = the end facing the head (housing/chuck) or the housing (grip)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    outline = (40, 34, 26, 255)
    dark, mid, light = (158, 112, 18, 255), (218, 166, 34, 255), (248, 208, 96, 255)
    grip, grip_l = (44, 44, 50, 255), (74, 74, 82, 255)
    grey_d, grey, grey_l = (70, 70, 78, 255), (112, 112, 120, 255), (164, 164, 174, 255)

    for y in range(11):                                   # housing side
        for x in range(6):
            if x in (0, 5):
                c = outline
            elif y >= 8:
                c = grip_l if (x + y) % 2 else grip        # cooling vents at the rear
            elif y == 0 or y == 7:
                c = dark
            else:
                c = light if x == 1 else (mid if x in (2, 3) else dark)
            img.putpixel((x, y), c)
    for y in range(6):                                    # housing ends
        for x in range(6):
            edge = x in (0, 5) or y in (0, 5)
            img.putpixel((6 + x, y), outline if edge else (grey_l if (x, y) in ((2, 2), (3, 3)) else grey_d))
    for y in range(3):                                    # chuck side (knurled)
        for x in range(4):
            img.putpixel((6 + x, 6 + y), grey_l if (x + y) % 2 == 0 else grey)
    for y in range(4):                                    # chuck ends
        for x in range(4):
            img.putpixel((12 + x, y), grey if x in (1, 2) and y in (1, 2) else grey_d)
    for y in range(6):                                    # grip side: rubber with finger ridges
        for x in range(3):
            c = dark if y == 0 else (grip_l if y % 2 == 1 and x == 1 else grip)
            img.putpixel((10 + x, 6 + y), c)
    for y in range(3):                                    # grip ends
        for x in range(3):
            img.putpixel((13 + x, 6 + y), grip)
    return img


def head_3d_template() -> Image.Image:
    """Grayscale 16×16 head skin, tinted per metal. Outer disks: face 8×8 at (0,0), rim 8×3 at
    (8,0). Middle disk (darker, so the counter-rotation reads): face at (0,8), rim at (8,4)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(x, y, v):
        img.putpixel((x, y), (v, v, v, 255))

    for oy, shade in ((0, 0), (8, -45)):                  # disk faces: carbide teeth on the rim
        for y in range(8):
            for x in range(8):
                r = max(abs(x - 3.5), abs(y - 3.5))
                if r > 3:
                    v = 245 if (x + y) % 2 == 0 else 175   # teeth
                elif r < 1:
                    v = 120                                # arbor
                else:
                    v = 205 - int(r * 10)
                put(x, oy + y, max(0, v + shade))
    for oy, shade in ((0, 0), (4, -45)):                  # rims: serrated cutting edge
        for y in range(3):
            for x in range(8):
                v = (240 if x % 2 == 0 else 150) if y == 1 else 190
                put(8 + x, oy + y, max(0, v + shade))
    return img


# ── main ─────────────────────────────────────────────────────────────────────

def main():
    items = known_items()
    lang = {}
    missing_refs = set()

    def need(item_id):
        if item_id.startswith("omnitech:") and item_id not in items:
            missing_refs.add(item_id)
        return item_id

    heads = collect_heads()
    head_ids = []
    stats_by_name = {}

    for name, m in heads.items():
        hv = HARDNESS.get(name)
        if hv is None:
            print(f"[!] no hardness for {name}, skipped")
            continue
        item = f"{name}_bore_head"
        head_ids.append(item)
        st = head_stats(name, hv)
        stats_by_name[name] = st

        write_json(DATA / f"bore_head/{name}.json", {
            **st,
            "formula": m["formula"],
            "repair_ingredient": m["ingot"],
            "fire_resistant": not m["burns"],
        })
        write_json(DATA / f"tags/item/repairs/{item}.json", {"values": [m["ingot"]]})
        write_json(DATA / f"recipe/{item}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "key": {"X": need(m["ingot"])},
            "pattern": ["XX ", "XXX", " XX"],
            "result": {"count": 1, "id": f"omnitech:{item}"},
        })
        write_json(ASSETS / f"items/{item}.json", head_item_def(name))
        lang[f"item.omnitech.{item}"] = f"{title(name)} Bore Head"

    # ── batteries ──
    base = Image.open(TEMPLATES / "battery_base.png").convert("RGBA")
    anode_tpl = Image.open(TEMPLATES / "battery_anode.png").convert("RGBA")
    cathode_tpl = Image.open(TEMPLATES / "battery_cathode.png").convert("RGBA")
    battery_ids = []

    for bid, (disp, anode, cathode, volts, whkg, c_an, c_cat, r_an, r_cat, r_el) in BATTERIES.items():
        item = f"{bid}_battery"
        battery_ids.append(item)
        write_json(DATA / f"battery/{bid}.json", {
            "name": disp,
            "anode": anode,
            "cathode": cathode,
            "cell_voltage": volts,
            "capacity_kj": whkg * 100,
        })
        write_json(DATA / f"recipe/{item}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"P": need(BATTERY_CASING), "W": need(BATTERY_WIRE), "S": "minecraft:paper",
                    "A": need(r_an), "C": need(r_cat), "E": need(r_el)},
            "pattern": ["PWP", "ASC", "PEP"],
            "result": {"count": 1, "id": f"omnitech:{item}"},
        })
        write_json(ASSETS / f"models/item/{item}.json", generated(f"omnitech:item/{item}"))
        write_json(ASSETS / f"items/{item}.json", item_def(f"omnitech:item/{item}"))

        png = TEX / f"{item}.png"
        if not png.exists():
            img = base.copy()
            img.alpha_composite(tint_flat(anode_tpl, c_an))
            img.alpha_composite(tint_flat(cathode_tpl, c_cat))
            img.save(png)
            print(f"[+] {png.name}")
        lang[f"item.omnitech.{item}"] = f"{disp} Battery"

    # ── bore ──
    png = TEX / "bore.png"
    if not png.exists():
        corpus_texture().save(png)
        print(f"[+] {png.name}")
    write_json(ASSETS / "models/item/bore.json", corpus_model())
    write_json(ASSETS / "models/item/bore_head_3d.json", head_3d_outer())
    write_json(ASSETS / "models/item/bore_head_3d_middle.json", head_3d_middle())
    head_tpl_3d = head_3d_template()
    for name in heads:
        if f"{name}_bore_head" not in head_ids:
            continue
        png = TEX / f"bore_head_3d/{name}.png"
        if not png.exists():
            png.parent.mkdir(parents=True, exist_ok=True)
            tint_metal(head_tpl_3d, heads[name]["colors"]).save(png)
            print(f"[+] bore_head_3d/{png.name}")
        for part, parent in (("", "bore_head_3d"), ("_middle", "bore_head_3d_middle")):
            write_json(ASSETS / f"models/item/bore_head_3d/{name}{part}.json", {
                "parent": f"omnitech:item/{parent}",
                "textures": {"bit": f"omnitech:item/bore_head_3d/{name}"},
            })
    # Heads sorted soft → hard so the creative tab reads as a progression
    order = sorted(head_ids, key=lambda i: stats_by_name[i.removesuffix("_bore_head")]["hardness"])
    write_json(ASSETS / "items/bore.json", {"model": {
        "type": "omnitech:bore",
        "corpus": "omnitech:item/bore",
        "heads": {f"omnitech:{i}": {
            "outer": f"omnitech:item/bore_head_3d/{i.removesuffix('_bore_head')}",
            "middle": f"omnitech:item/bore_head_3d/{i.removesuffix('_bore_head')}_middle",
        } for i in order},
    }})
    write_json(DATA / "recipe/bore.json", {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {k: need(v) for k, v in BORE_RECIPE["key"].items()},
        "pattern": BORE_RECIPE["pattern"],
        "result": {"count": 1, "id": "omnitech:bore"},
    })
    lang["item.omnitech.bore"] = "Bore"
    lang["container.omnitech.bore"] = "Bore"

    # ── tags ──
    write_json(DATA / "tags/item/bore_heads.json", {"values": [f"omnitech:{i}" for i in order]})
    write_json(DATA / "tags/item/batteries.json", {"values": [f"omnitech:{i}" for i in battery_ids]})
    write_json(RES / "data/minecraft/tags/item/enchantable/durability.json",
               {"replace": False, "values": ["#omnitech:bore_heads"]})

    update_lang(lang)

    print(f"\n{len(head_ids)} bore heads, {len(battery_ids)} batteries")
    for ref in sorted(missing_refs):
        print(f"[!] recipe references unknown item {ref}")


if __name__ == "__main__":
    main()
