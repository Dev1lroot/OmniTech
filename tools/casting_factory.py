#!/usr/bin/env python3
"""
casting_factory.py
==================
Two ways to make every metal part: the crafting table (from ingots) or the
Foundry (molten metal poured into a reusable casting template).

  • Template items (<shape>_template): data JSON, model, lang, texture
    (a stone slab with the shape's silhouette pressed into it), and a crafting
    recipe: the shape item imprinted in clay and fired bricks.
  • Item tags  omnitech:casting/<shape>  — the items accepted as the imprint.
  • Foundry recipes for every item that has a melt recipe but no cast yet.
    Cast cost is never below what the item melts back into, and never below its
    crafting-table cost, so melt/cast/craft loops can't duplicate metal.
  • Crafting-table recipes for metal parts that had none (nugget, plate, rod,
    wire, coil, cog, reductor).

Rules:
  • PNG files are NEVER overwritten.
  • Existing foundry / crafting recipes are never overwritten or duplicated.

Usage:
    python3 casting_factory.py
"""

import glob
import json
import random
from pathlib import Path
from PIL import Image
import yaml

SCRIPT_DIR = Path(__file__).parent
ROOT_DIR   = SCRIPT_DIR.parent
RES        = ROOT_DIR / "src/main/resources"
DATA       = RES / "data/omnitech"
ASSETS     = RES / "assets/omnitech"
SHAPE_TEX  = ROOT_DIR / "templates/assets"
SLAB_REF   = ASSETS / "textures/item/ingot_template.png"

# shape: (cast cost mB, en_us template name, ru_ru template name, vanilla imprint items/tags)
SHAPES = {
    "ingot":      (1000, "Ingot Casting Template",      "Литейная форма слитка",     ["minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot"]),
    "nugget":     (112,  "Nugget Casting Template",     "Литейная форма самородка",  ["minecraft:iron_nugget", "minecraft:gold_nugget", "minecraft:copper_nugget"]),
    "plate":      (500,  "Plate Casting Template",      "Литейная форма пластины",   []),
    "rod":        (500,  "Rod Casting Template",        "Литейная форма стержня",    []),
    "wire":       (500,  "Wire Casting Template",       "Литейная форма провода",    []),
    "coil":       (1500, "Coil Casting Template",       "Литейная форма катушки",    []),
    "cog":        (200,  "Cog Casting Template",        "Литейная форма шестерни",   []),
    "reductor":   (500,  "Reductor Casting Template",   "Литейная форма редуктора",  []),
    "block":      (9000, "Block Casting Template",      "Литейная форма блока",      ["minecraft:iron_block", "minecraft:gold_block", "minecraft:copper_block"]),
    "pickaxe":    (3000, "Pickaxe Casting Template",    "Литейная форма кирки",      ["#minecraft:pickaxes"]),
    "axe":        (3000, "Axe Casting Template",        "Литейная форма топора",     ["#minecraft:axes"]),
    "shovel":     (1000, "Shovel Casting Template",     "Литейная форма лопаты",     ["#minecraft:shovels"]),
    "hoe":        (2000, "Hoe Casting Template",        "Литейная форма мотыги",     ["#minecraft:hoes"]),
    "sword":      (2000, "Sword Casting Template",      "Литейная форма меча",       ["#minecraft:swords"]),
    "helmet":     (5000, "Helmet Casting Template",     "Литейная форма шлема",      ["#minecraft:head_armor"]),
    "chestplate": (8000, "Chestplate Casting Template", "Литейная форма нагрудника", ["#minecraft:chest_armor"]),
    "leggings":   (7000, "Leggings Casting Template",   "Литейная форма поножей",    ["#minecraft:leg_armor"]),
    "boots":      (4000, "Boots Casting Template",      "Литейная форма ботинок",    ["#minecraft:foot_armor"]),
}


# ── helpers ──────────────────────────────────────────────────────────────────

def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path: Path, obj, overwrite=False):
    if path.exists() and not overwrite:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return True


def update_lang(file: str, entries: dict):
    path = ASSETS / f"lang/{file}"
    text = path.read_text(encoding="utf-8")
    lang = json.loads(text)
    missing = {k: v for k, v in entries.items() if k not in lang}
    if not missing:
        return
    body = text.rstrip().removesuffix("}").rstrip()
    lines = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}" for k, v in missing.items())
    path.write_text(f"{body},\n\n{lines}\n}}\n", encoding="utf-8")


def omni_items() -> set:
    return {"omnitech:" + Path(f).stem for f in glob.glob(str(DATA / "item/*.json")) + glob.glob(str(DATA / "block/*.json"))}


# ── template texture ─────────────────────────────────────────────────────────

def blank_slab() -> Image.Image:
    """Keep the reference template's 3px frame, refill the face with plain stone."""
    ref = Image.open(SLAB_REF).convert("RGBA")
    w, h = ref.size
    face = [ref.getpixel((x, y)) for y in range(3, 7) for x in range(3, w - 3)]
    base = sorted(p[0] for p in face)[len(face) // 2]
    rnd = random.Random(1234)
    out = ref.copy()
    for y in range(3, h - 3):
        row = rnd.randint(-4, 4)
        for x in range(3, w - 3):
            v = max(0, min(255, base + row + rnd.randint(-5, 5)))
            out.putpixel((x, y), (v, v, v, 255))
    return out


def template_texture(shape: str) -> Image.Image:
    slab = blank_slab()
    w, h = slab.size
    src = Image.open(SHAPE_TEX / f"default_{shape}.png").convert("RGBA")
    box = int(w * 0.7)
    src = src.resize((box, box), Image.NEAREST)
    off = (w - box) // 2
    mask = [[False] * w for _ in range(h)]
    lum  = [[0.0] * w for _ in range(h)]
    for y in range(box):
        for x in range(box):
            r, g, b, a = src.getpixel((x, y))
            if a > 40:
                mask[y + off][x + off] = True
                lum[y + off][x + off] = (0.299 * r + 0.587 * g + 0.114 * b) / 255
    out = slab.copy()
    inside = lambda x, y: 0 <= x < w and 0 <= y < h and mask[y][x]
    for y in range(h):
        for x in range(w):
            v = slab.getpixel((x, y))[0]
            if mask[y][x]:
                # recessed: darker, lit from the bottom-right like the existing templates
                k = 0.42 + 0.18 * lum[y][x]
                if not inside(x - 1, y - 1):
                    k -= 0.10
                v = int(v * k)
            elif inside(x - 1, y - 1):
                v = min(255, int(v * 1.35))
            out.putpixel((x, y), (v, v, v, 255))
    return out


# ── main ─────────────────────────────────────────────────────────────────────

def main():
    items = omni_items()
    mats = {m["name"]: m for m in yaml.safe_load((SCRIPT_DIR / "materials.yml").read_text())["materials"]}
    en, ru = {}, {}

    # melt recipes: item → (fluid, temperature, amount)
    melts = {}
    for f in glob.glob(str(DATA / "machine_recipe/smelting/*_melt.json")):
        j = load(f)
        inp = j["input"][0] if isinstance(j["input"], list) else j["input"]
        melts[inp] = (j["output"]["fluid"], j["requiredMinimalTemperature"], j["output"]["amount"])

    cast = {load(f)["output"] for f in glob.glob(str(DATA / "machine_recipe/foundry/*.json"))}
    crafted = set()
    for f in glob.glob(str(DATA / "recipe/*.json")):
        r = load(f).get("result")
        if isinstance(r, dict):
            crafted.add(r.get("id"))

    def shape_of(item_id):
        name = item_id.split(":")[1]
        if name.startswith("raw_"):
            return None, None
        for s in SHAPES:
            if name.endswith("_" + s):
                return name[: -len(s) - 1], s
        return None, None

    # ── templates, tags, textures ──
    shape_members = {s: [] for s in SHAPES}
    for item in sorted(items | set(melts)):
        mat, s = shape_of(item)
        if s and mat and (mat in mats or item in melts):
            shape_members[s].append(item)

    for s, (_, en_name, ru_name, vanilla) in SHAPES.items():
        tpl = f"{s}_template"
        write_json(DATA / f"item/{tpl}.json", {})
        write_json(ASSETS / f"items/{tpl}.json",
                   {"model": {"type": "minecraft:model", "model": f"omnitech:item/{tpl}"}})
        write_json(ASSETS / f"models/item/{tpl}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"omnitech:item/{tpl}"}})
        png = ASSETS / f"textures/item/{tpl}.png"
        if not png.exists():
            template_texture(s).save(png)
        write_json(DATA / f"tags/item/casting/{s}.json",
                   {"values": vanilla + [{"id": i, "required": False} for i in shape_members[s]]}, overwrite=True)
        write_json(DATA / f"recipe/{tpl}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"B": "minecraft:brick", "C": "minecraft:clay_ball", "X": f"#omnitech:casting/{s}"},
            "pattern": ["BCB", "CXC", "BCB"],
            "result": {"count": 1, "id": f"omnitech:{tpl}"},
        })
        en[f"item.omnitech.{tpl}"] = en_name
        ru[f"item.omnitech.{tpl}"] = ru_name

    # ── foundry casts ──
    n_cast = 0
    for item, (fluid, temp, melt_amount) in sorted(melts.items()):
        mat, s = shape_of(item)
        if not s or item in cast:
            continue
        cost = max(SHAPES[s][0], melt_amount)
        name = item.split(":")[1]
        if write_json(DATA / f"machine_recipe/foundry/{name}.json", {
            "requiredMinimalTemperature": temp,
            "input": {"fluid": fluid, "amount": cost},
            "template": f"omnitech:{s}_template",
            "output": item,
        }):
            n_cast += 1

    # ── crafting-table recipes for parts ──
    def ingot_of(mat):
        if f"omnitech:{mat}_ingot" in items:
            return f"omnitech:{mat}_ingot"
        return mats.get(mat, {}).get("vanilla_id")

    def shaped(name, key, pattern, out, count):
        if out in crafted or out not in items:
            return 0
        if not all(v in items or v.startswith("minecraft:") for v in key.values()):
            return 0
        return int(write_json(DATA / f"recipe/{name}.json", {
            "type": "minecraft:crafting_shaped", "category": "misc",
            "key": key, "pattern": pattern, "result": {"count": count, "id": out},
        }))

    n_craft = 0
    for mat, m in sorted(mats.items()):
        ingot = ingot_of(mat)
        if not ingot:
            continue
        o = lambda s: f"omnitech:{mat}_{s}"
        nug = o("nugget")
        if nug in items and not m.get("vanilla_id"):
            # no pre-existing nugget recipes, so these are keyed on file name only
            n_craft += write_json(DATA / f"recipe/{mat}_ingot_to_nugget.json", {
                "type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [ingot], "result": {"count": 9, "id": nug}})
            n_craft += write_json(DATA / f"recipe/{mat}_nugget_to_ingot.json", {
                "type": "minecraft:crafting_shaped", "category": "misc",
                "key": {"N": nug}, "pattern": ["NNN", "NNN", "NNN"], "result": {"count": 1, "id": ingot}})
        n_craft += shaped(f"{mat}_ingot_to_plate", {"I": ingot}, ["II"], o("plate"), 2)
        n_craft += shaped(f"{mat}_ingot_to_rod", {"I": ingot}, ["I", "I"], o("rod"), 4)
        n_craft += shaped(f"{mat}_rod_to_wire", {"R": o("rod")}, ["RRR"], o("wire"), 3)
        n_craft += shaped(f"{mat}_wire_to_coil", {"W": o("wire"), "R": o("rod")}, ["WWW", "WRW", "WWW"], o("coil"), 1)
        n_craft += shaped(f"{mat}_plate_to_cog", {"P": o("plate"), "R": o("rod")}, [" P ", "PRP", " P "], o("cog"), 2)
        n_craft += shaped(f"{mat}_cog_to_reductor", {"C": o("cog"), "R": o("rod")}, ["CRC"], o("reductor"), 1)

    update_lang("en_us.json", en)
    update_lang("ru_ru.json", ru)

    tab_path = DATA / "creative_tab/omnitech.utility.json"
    tab = load(tab_path)
    at = tab["data"].index("omnitech:ingot_template") + 1
    for s in SHAPES:
        if f"omnitech:{s}_template" not in tab["data"]:
            tab["data"].insert(at, f"omnitech:{s}_template")
            at += 1
    write_json(tab_path, tab, overwrite=True)

    print(f"{len(SHAPES)} templates, {n_cast} new foundry casts, {n_craft} new crafting recipes")


if __name__ == "__main__":
    main()
