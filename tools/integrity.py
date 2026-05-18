#!/usr/bin/env python3
"""
OmniTech resource integrity checker.

Scans fluids, items, tools, armors and base materials, then reports every
missing JSON definition, texture, model, recipe or lang entry.

Usage:
  python3 integrity.py                         # check everything (auto-detects root)
  python3 integrity.py --root /path/to/mod     # explicit root
  python3 integrity.py --check fluids,tools    # selected sections only
  python3 integrity.py --verbose               # also show passing items
  python3 integrity.py --no-color              # disable ANSI output

Sections:
  fluids     data/omnitech/fluid/*.json  →  still/flow textures + mcmeta + lang
  items      assets/omnitech/items/*.json  →  model + textures + lang
  tools      per-material (5 slots)  →  item_def + model + texture + recipe + lang
  armors     per-material (4 pieces)  →  item_def + model + texture + recipe +
                                          equipment JSON + entity textures + lang
  materials  per-material base recipes  →  ingot↔block, dust↔mote
  all        all of the above (default)

Exit code 0 = clean, 1 = missing resources found.
"""

import sys
import json
import argparse
from pathlib import Path
from collections import defaultdict

# ── Terminal colors ────────────────────────────────────────────────────────────

_COLOR = True  # disabled by --no-color or non-tty


def _c(code: str, text: str) -> str:
    return f"\033[{code}m{text}\033[0m" if _COLOR else text


def green(t):  return _c("32", t)
def red(t):    return _c("31", t)
def yellow(t): return _c("33", t)
def bold(t):   return _c("1",  t)
def dim(t):    return _c("2",  t)


def tick():  return green("✓")
def cross(): return red("✗")
def warn():  return yellow("!")


# ── Root auto-detection ────────────────────────────────────────────────────────

def find_root(start: Path) -> Path:
    for candidate in [start, *start.parents]:
        if (candidate / "src/main/resources/data/omnitech/fluid").exists():
            return candidate
    raise SystemExit(
        cross() + "  Could not locate mod root.\n"
        "         Run from within the project tree or pass --root <path>."
    )


# ── Resource path layout ───────────────────────────────────────────────────────

class Paths:
    """All resource directories derived from mod root."""

    def __init__(self, root: Path):
        res = root / "src/main/resources"
        a   = res / "assets/omnitech"
        d   = res / "data/omnitech"

        self.items_dir    = a / "items"
        self.models_item  = a / "models/item"
        self.models_block = a / "models/block"
        self.blockstates  = a / "blockstates"
        self.tex_item     = a / "textures/item"
        self.tex_block    = a / "textures/block"
        self.tex_fluid    = a / "textures/block/fluid"
        self.tex_humanoid = a / "textures/entity/equipment/humanoid"
        self.tex_leggings = a / "textures/entity/equipment/humanoid_leggings"
        self.equipment    = a / "equipment"
        self.lang_path    = a / "lang/en_us.json"

        self.fluid_data   = d / "fluid"
        self.recipe_root  = d / "recipe"
        self.loot_blocks  = d / "loot_table/blocks"


# ── Issue & report ─────────────────────────────────────────────────────────────

class Issue:
    __slots__ = ("section", "entity", "missing")

    def __init__(self, section: str, entity: str, missing: list[str]):
        self.section = section
        self.entity  = entity
        self.missing = missing


class Report:
    """Accumulates issues and output lines; prints everything at the end."""

    def __init__(self, verbose: bool):
        self.verbose = verbose
        self.issues:  list[Issue] = []
        self._lines:  list[str]   = []

    # ── Output helpers

    def section(self, title: str):
        bar = "─" * max(0, 62 - len(title) - 4)
        self._lines.append("")
        self._lines.append(bold(f"── {title} {bar}"))

    def raw(self, line: str):
        """Append a pre-formatted line (used by grouped sections)."""
        self._lines.append(line)

    def _fmt_missing(self, missing: list[str]) -> str:
        return "  ".join(red(m) for m in missing)

    # ── Entity-level helpers

    def entity(self, section: str, name: str, missing: list[str], indent: int = 2):
        """Record one entity check and append its output line."""
        pad = " " * indent
        if missing:
            self.issues.append(Issue(section, name, missing))
            self._lines.append(
                f"{pad}{cross()}  {name:<38}  {self._fmt_missing(missing)}"
            )
        elif self.verbose:
            self._lines.append(f"{pad}{tick()}  {name}")

    # ── Grouped helpers used by tools / armors

    def group_ok(self, label: str, slots: list[str]):
        if self.verbose:
            self._lines.append(
                f"  {tick()}  {label:<20}  " + "  ".join(dim(s) for s in slots)
            )

    def group_bad(self, mat: str, slot_issues: dict[str, list[str]], section: str):
        self._lines.append(f"  {cross()}  {bold(mat)}")
        for name, missing in slot_issues.items():
            self.issues.append(Issue(section, name, missing))
            self._lines.append(
                f"       {cross()}  {name:<38}  {self._fmt_missing(missing)}"
            )

    # ── Summary

    def summary(self) -> int:
        total    = sum(len(i.missing) for i in self.issues)
        affected = len(self.issues)

        by_section: dict[str, int] = defaultdict(int)
        for iss in self.issues:
            by_section[iss.section] += len(iss.missing)

        self._lines.append("")
        self._lines.append(bold("── Summary " + "─" * 51))

        if not self.issues:
            self._lines.append(f"  {green('All clean!')}  No missing resources.")
        else:
            self._lines.append(
                f"  {red(str(total))} missing resource(s) "
                f"in {red(str(affected))} entit(y/ies):"
            )
            for sec, count in sorted(by_section.items()):
                self._lines.append(f"    {dim(sec):<18}  {red(str(count))}")

        return 1 if self.issues else 0

    def print_all(self):
        for line in self._lines:
            print(line)


# ── Section constants ──────────────────────────────────────────────────────────

TOOL_SLOTS  = ["pickaxe", "axe", "shovel", "hoe", "sword"]
ARMOR_SLOTS = ["helmet", "chestplate", "leggings", "boots"]


def _detect_by_suffix(items_dir: Path, suffix: str) -> list[str]:
    return sorted({f.stem.removesuffix(suffix) for f in items_dir.glob(f"*{suffix}.json")})


# ── Section: fluids ────────────────────────────────────────────────────────────

def check_fluids(r: Report, p: Paths, lang: dict):
    if not p.fluid_data.exists():
        r.section("Fluids")
        r.raw(f"  {cross()}  {red('data/omnitech/fluid/ not found')}")
        return

    fluids = sorted(f.stem for f in p.fluid_data.glob("*.json"))
    r.section(f"Fluids ({len(fluids)} registered)")

    for name in fluids:
        missing = []
        if not (p.tex_fluid / f"{name}_still.png").exists():      missing.append("still_texture")
        if not (p.tex_fluid / f"{name}_still.png.mcmeta").exists(): missing.append("still_mcmeta")
        if not (p.tex_fluid / f"{name}_flow.png").exists():       missing.append("flow_texture")
        if not (p.tex_fluid / f"{name}_flow.png.mcmeta").exists():  missing.append("flow_mcmeta")
        if f"fluid.omnitech.{name}" not in lang:                   missing.append("lang_key")
        r.entity("fluids", name, missing)


# ── Section: tools ─────────────────────────────────────────────────────────────

def check_tools(r: Report, p: Paths, lang: dict):
    mats = _detect_by_suffix(p.items_dir, "_pickaxe")
    r.section(f"Tools ({len(mats)} materials × {len(TOOL_SLOTS)} slots)")

    for mat in mats:
        slot_issues: dict[str, list[str]] = {}

        for slot in TOOL_SLOTS:
            name    = f"{mat}_{slot}"
            missing = []
            if not (p.items_dir   / f"{name}.json").exists():  missing.append("item_def")
            if not (p.models_item / f"{name}.json").exists():  missing.append("model")
            if not (p.tex_item    / f"{name}.png").exists():   missing.append("texture")
            if not (p.recipe_root / f"{name}.json").exists():  missing.append("recipe")
            if f"item.omnitech.{name}" not in lang:             missing.append("lang_key")
            if missing:
                slot_issues[name] = missing

        if slot_issues:
            r.group_bad(mat, slot_issues, "tools")
        else:
            r.group_ok(mat, TOOL_SLOTS)


# ── Section: armors ────────────────────────────────────────────────────────────

def check_armors(r: Report, p: Paths, lang: dict):
    mats = _detect_by_suffix(p.items_dir, "_helmet")
    r.section(f"Armors ({len(mats)} materials × {len(ARMOR_SLOTS)} pieces + equipment)")

    for mat in mats:
        slot_issues: dict[str, list[str]] = {}

        for piece in ARMOR_SLOTS:
            name    = f"{mat}_{piece}"
            missing = []
            if not (p.items_dir   / f"{name}.json").exists():  missing.append("item_def")
            if not (p.models_item / f"{name}.json").exists():  missing.append("model")
            if not (p.tex_item    / f"{name}.png").exists():   missing.append("texture")
            if not (p.recipe_root / f"{name}.json").exists():  missing.append("recipe")
            if f"item.omnitech.{name}" not in lang:             missing.append("lang_key")
            if missing:
                slot_issues[name] = missing

        # Set-level: equipment JSON + body textures
        set_missing = []
        if not (p.equipment    / f"{mat}_armor.json").exists():         set_missing.append("equipment_json")
        if not (p.tex_humanoid / f"{mat}_armor.png").exists():          set_missing.append("humanoid_texture")
        if not (p.tex_leggings / f"{mat}_armor_leggings.png").exists(): set_missing.append("leggings_texture")
        if set_missing:
            slot_issues[f"{mat} [set]"] = set_missing

        if slot_issues:
            r.group_bad(mat, slot_issues, "armors")
        else:
            r.group_ok(mat, ARMOR_SLOTS + ["equip"])


# ── Section: items (general) ───────────────────────────────────────────────────

def check_items(r: Report, p: Paths, lang: dict):
    tool_mats  = set(_detect_by_suffix(p.items_dir, "_pickaxe"))
    armor_mats = set(_detect_by_suffix(p.items_dir, "_helmet"))
    skip = (
        {f"{m}_{s}" for m in tool_mats  for s in TOOL_SLOTS} |
        {f"{m}_{s}" for m in armor_mats for s in ARMOR_SLOTS}
    )

    all_items = sorted(f.stem for f in p.items_dir.glob("*.json"))
    items     = [n for n in all_items if n not in skip]

    excluded = len(all_items) - len(items)
    r.section(f"Items ({len(items)} general, {excluded} delegated to tool/armor sections)")

    for name in items:
        missing = []

        # Parse the items/<name>.json to find what model it delegates to
        item_def_path = p.items_dir / f"{name}.json"
        model_ref = ""
        try:
            with open(item_def_path) as f:
                model_ref = json.load(f).get("model", {}).get("model", "")
        except Exception:
            missing.append("item_def_invalid")

        if model_ref.startswith("omnitech:item/"):
            # Pure item model
            model_name = model_ref.removeprefix("omnitech:item/")
            model_path = p.models_item / f"{model_name}.json"
            if not model_path.exists():
                missing.append("model")
            else:
                # Check every texture layer referenced in the model
                try:
                    with open(model_path) as f:
                        textures = json.load(f).get("textures", {})
                    for tex_val in textures.values():
                        if tex_val.startswith("omnitech:item/"):
                            tex_file = tex_val.removeprefix("omnitech:item/") + ".png"
                            if not (p.tex_item / tex_file).exists():
                                missing.append(f"texture:{tex_file}")
                except Exception:
                    missing.append("model_invalid")

        elif model_ref.startswith("omnitech:block/"):
            # Block item — check block model + blockstate + block texture
            block_name = model_ref.removeprefix("omnitech:block/")
            if not (p.models_block / f"{block_name}.json").exists():
                missing.append("block_model")
            if not (p.blockstates  / f"{name}.json").exists():
                missing.append("blockstate")
            block_model_path = p.models_block / f"{block_name}.json"
            if block_model_path.exists():
                try:
                    with open(block_model_path) as f:
                        textures = json.load(f).get("textures", {})
                    for tex_val in textures.values():
                        if tex_val.startswith("omnitech:block/"):
                            tex_file = tex_val.removeprefix("omnitech:block/") + ".png"
                            if not (p.tex_block / tex_file).exists():
                                missing.append(f"block_texture:{tex_file}")
                except Exception:
                    pass  # non-critical — model might use inheritance

        # Lang key — items can appear as either item.* or block.*
        if (f"item.omnitech.{name}" not in lang
                and f"block.omnitech.{name}" not in lang):
            missing.append("lang_key")

        r.entity("items", name, missing)


# ── Section: materials (base recipes) ─────────────────────────────────────────

def check_materials(r: Report, p: Paths, lang: dict):
    mats = _detect_by_suffix(p.items_dir, "_ingot")
    r.section(f"Material recipes ({len(mats)} materials)")

    # Each material should have these four vanilla-recipe files at recipe root
    expected = [
        "{mat}_ingot_to_block",
        "{mat}_block_to_ingot",
        "{mat}_dust_to_mote",
        "{mat}_mote_to_dust",
    ]

    for mat in mats:
        missing = [
            pattern.replace("{mat}", mat).replace(f"{mat}_", "", 1)   # short label
            for pattern in expected
            if not (p.recipe_root / pattern.replace("{mat}", mat)).with_suffix(".json").exists()
        ]
        r.entity("materials", mat, missing)


# ── Dispatch ───────────────────────────────────────────────────────────────────

SECTIONS: dict[str, callable] = {
    "fluids":    check_fluids,
    "items":     check_items,
    "tools":     check_tools,
    "armors":    check_armors,
    "materials": check_materials,
}


# ── CLI ────────────────────────────────────────────────────────────────────────

def main() -> None:
    global _COLOR

    ap = argparse.ArgumentParser(
        prog="integrity.py",
        description="OmniTech resource integrity checker",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="\n".join([
            "sections:",
            "  fluids     fluid JSONs → still/flow textures + mcmeta + lang",
            "  items      item defs → models + textures + lang",
            "  tools      tool sets → item_def + model + texture + recipe + lang",
            "  armors     armor sets → item_def + model + texture + recipe + equipment + lang",
            "  materials  base material recipes (ingot↔block, dust↔mote)",
            "  all        everything (default)",
        ]),
    )
    ap.add_argument("--root",     metavar="PATH",
                    help="Mod root directory (auto-detected from cwd if omitted)")
    ap.add_argument("--check",    metavar="SECTIONS", default="all",
                    help="Comma-separated sections to check (default: all)")
    ap.add_argument("--verbose",  action="store_true",
                    help="Show passing items in addition to failures")
    ap.add_argument("--no-color", action="store_true",
                    help="Disable ANSI color output")
    args = ap.parse_args()

    if args.no_color or not sys.stdout.isatty():
        _COLOR = False

    root = Path(args.root).resolve() if args.root else find_root(Path.cwd())
    p    = Paths(root)

    # Load language file
    lang: dict = {}
    if p.lang_path.exists():
        try:
            with open(p.lang_path, encoding="utf-8") as f:
                lang = json.load(f)
        except json.JSONDecodeError as e:
            print(warn() + f"  lang file parse error: {e}")
    else:
        print(warn() + f"  lang file not found: {p.lang_path}")

    # Resolve section list
    if args.check.lower() == "all":
        selected = list(SECTIONS.keys())
    else:
        selected = [s.strip().lower() for s in args.check.split(",")]
        unknown  = [s for s in selected if s not in SECTIONS]
        if unknown:
            ap.error(
                f"Unknown section(s): {', '.join(unknown)}. "
                f"Valid: {', '.join(SECTIONS)}, all"
            )

    r = Report(args.verbose)

    print(bold("═" * 62))
    print(bold("  OmniTech Resource Integrity Checker"))
    print(f"  {dim('root:')} {dim(str(root))}")
    print(f"  {dim('checking:')} {dim(', '.join(selected))}")
    print(bold("═" * 62))

    for section_name in selected:
        SECTIONS[section_name](r, p, lang)

    exit_code = r.summary()
    r.print_all()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
