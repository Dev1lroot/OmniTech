#!/usr/bin/env python3
"""
electronics_factory.py
======================
Electronics progression: silicon → doped wafers → discrete components →
printed circuit boards → machine recipes.

  • Silicon chain (alloy furnace): quartz + coke → metallurgical silicon →
    Czochralski boule → wire-sawn wafers → N / P / PN-junction wafers
    (phosphorus and boron motes as dopants).
  • Components (crafting): diodes, zener, capacitors, NPN / PNP transistors, and one
    resistor item: crafted blank, then colour-coded in the crafting grid with 3–6 band
    items (dyes; gold/silver nuggets, dust or motes) — the bands decide the value
    (omnitech:resistor_coding, pcb/ResistorCode.java). Model: tinted body + 6 band layers.
  • PCB pipeline blocks: PCB Workbench (draw → blueprint), PCB Fabricator
    (copper plates + photoresist → etched boards), Soldering Station (parts →
    tested circuit). Circuit test benches live in data/omnitech/circuit_test.
  • Ready-made blueprints (data/omnitech/pcb_blueprint) for every test bench, each
    with a reference part placement the Soldering Station pre-fills; crafted from
    paper + blue dye + the circuit's key part. Their routing is checked here, their
    electrical behaviour by tools/pcb_sim_check.
  • Photoresist fluid (resin dissolved in acetone, Solvation Machine).
  • Machine casings (basic iron / steel) and crafting recipes for every machine
    that had none, built from casings, coils and circuits; machines without a
    loot table get a self-drop one so a crafted machine survives being mined.

Rules:
  • Only the photoresist fluid textures are generated (tinted from acetone);
    every item reuses an existing texture.
  • Existing recipes are never overwritten; a machine that already has a
    recipe producing it is skipped.
  • Lang entries are appended (existing keys are left untouched).
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data", "omnitech")
ASSETS = os.path.join(ROOT, "assets", "omnitech")
MC_TAGS = os.path.join(ROOT, "data", "minecraft", "tags")


def write_json(path, obj, overwrite=True):
    if not overwrite and os.path.exists(path):
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return True


def texture_exists(tex):
    """tex like 'item/diode' or 'block/casing'."""
    return os.path.exists(os.path.join(ASSETS, "textures", tex + ".png"))


# ════════════════════════════════════════════════════════════════════════════
# Items and blocks
# ════════════════════════════════════════════════════════════════════════════

# ItemLoader items — id: (texture, en name, ru name, item json)
ITEMS = {
    "metallurgical_silicon": ("raw_silicon",            "Metallurgical Silicon",   "Металлургический кремний", {"formula": "Si"}),
    "silicon_boule":         ("silicon_monocrystall",   "Monocrystalline Silicon Boule", "Монокристалл кремния", {"formula": "Si"}),
    "silicon_wafer":         ("silicon_alloy",          "Silicon Wafer",           "Кремниевая пластина",      {"formula": "Si"}),
    "n_type_wafer":          ("alloy_n",                "N-Type Silicon Wafer",    "Пластина кремния n-типа",  {"formula": "Si:P"}),
    "p_type_wafer":          ("alloy_p",                "P-Type Silicon Wafer",    "Пластина кремния p-типа",  {"formula": "Si:B"}),
    "pn_junction_wafer":     ("alloy_pn",               "P-N Junction Wafer",      "Пластина с p-n переходом", {"formula": "Si:P/Si:B"}),
    "diode":                 ("diode",                  "Diode",                   "Диод",                     {}),
    "zener_diode":           ("zener_diode",            "Zener Diode (5.1 V)",     "Стабилитрон (5,1 В)",      {}),
    "capacitor":             ("capacitor",              "Electrolytic Capacitor (100 µF)",  "Электролитический конденсатор (100 мкФ)", {}),
    "capacitor_1000uf":      ("capacitor_mark_2",       "Electrolytic Capacitor (1000 µF)", "Электролитический конденсатор (1000 мкФ)", {}),
    "npn_transistor":        ("npn-transistor",         "NPN Transistor",          "NPN-транзистор",           {}),
    "pnp_transistor":        ("pnp-transistor",         "PNP Transistor",          "PNP-транзистор",           {}),
    "stabilizer_circuit":    ("circuit_stab_mark_1",    "Power Stabilizer Circuit", "Плата стабилизатора питания", {}),
    "power_circuit":         ("circuit_power_mark_1",   "Power Switch Circuit",    "Плата силового ключа",     {}),
    "control_circuit":       ("circuit_control_mark_1", "Control Circuit",         "Плата управления",         {}),
}

# Items registered in Java (they carry data components) — assets + lang only
JAVA_ITEMS = {
    "pcb_blueprint":           ("circuit_blueprint", "PCB Blueprint",           "Чертёж печатной платы"),
    "printed_circuit_board":   ("marked-circuit",    "Printed Circuit Board",   "Печатная плата"),
    "assembled_circuit_board": ("marked-circuit23",  "Assembled Circuit Board", "Собранная плата"),
}

# Simple cube blocks registered through BlockLoader (data/omnitech/block)
CASINGS = {
    "basic_machine_casing": ("Basic Machine Casing", "Базовый корпус машины",
                             {"side": "omnitech:block/unplated_basic_casing",
                              "top": "omnitech:block/unplated_basic_casing_top",
                              "bottom": "omnitech:block/unplated_basic_casing_top"}),
    "machine_casing":       ("Machine Casing", "Корпус машины",
                             {"all": "omnitech:block/casing"}),
}


def _faces(up, down, north, side):
    return {"up": up, "down": down, "north": north, "south": side, "east": side, "west": side, "particle": side}


# PCB pipeline blocks (registered in Java) — id: (en, ru, model textures, mineable tool)
STATIONS = {
    "pcb_workbench": ("PCB Workbench", "Верстак печатных плат",
                      _faces("omnitech:block/casing_base_scheme", "minecraft:block/spruce_planks",
                             "minecraft:block/spruce_planks", "minecraft:block/spruce_planks"), "axe"),
    "pcb_fabricator": ("PCB Fabricator", "Фабрикатор печатных плат",
                       _faces("omnitech:block/machine_top_inset", "omnitech:block/machine_slab",
                              "omnitech:block/advanced_machine_fluid_socket", "omnitech:block/electric_machine_side"),
                       "pickaxe"),
    "soldering_station": ("Soldering Station", "Паяльная станция",
                          _faces("omnitech:block/casing_base_scheme_inner", "omnitech:block/machine_slab",
                                 "omnitech:block/casing_supply", "omnitech:block/electric_machine_side"),
                          "pickaxe"),
}

# Photoresist: novolac-style resin dissolved in acetone; amber, solvent-like
PHOTORESIST = {
    "polarity": "both",
    "miscible": False,
    "density": 1050,
    "viscosity": 1500,
    "temperature": 293,
    "light_level": 0,
    "min_temp": -95,
    "max_temp": 400,
    "min_pressure": 0,
    "max_pressure": 200000,
    "toxic": True,
    "flammable": True,
    "phase_diagram": {
        "melting_point": -95, "boiling_point": 56, "boiling_slope": 35,
        "critical_temp": 235, "critical_pressure": 4700,
        "triple_point_temp": -95, "triple_point_pressure": 1, "has_solid": True,
    },
}
PHOTORESIST_COLOR = (186, 84, 30)


# ════════════════════════════════════════════════════════════════════════════
# Recipes
# ════════════════════════════════════════════════════════════════════════════

def shaped(result, pattern, key, count=1, category="misc"):
    return {"type": "minecraft:crafting_shaped", "category": category,
            "key": key, "pattern": pattern, "result": {"count": count, "id": result}}


def shapeless(result, ingredients, count=1, category="misc"):
    return {"type": "minecraft:crafting_shapeless", "category": category,
            "ingredients": ingredients, "result": {"count": count, "id": result}}


O = "omnitech:"
M = "minecraft:"

# Silicon chain — alloy furnace (id: (minTemperature, ingredients, outputs))
ALLOY = {
    # SiO2 + 2C → Si + 2CO, submerged-arc carbothermal reduction
    "metallurgical_silicon": (1900, [M + "quartz", O + "coke_coal"], [O + "metallurgical_silicon"]),
    # Czochralski pull from a fused-quartz crucible
    "silicon_boule":         (1450, [O + "metallurgical_silicon", M + "quartz"], [O + "silicon_boule"]),
    # Thermal diffusion doping
    "n_type_wafer":          (1000, [O + "silicon_wafer", O + "phosphorus_mote"], [O + "n_type_wafer"]),
    "p_type_wafer":          (1000, [O + "silicon_wafer", O + "boron_mote"], [O + "p_type_wafer"]),
    "pn_junction_wafer":     (1000, [O + "n_type_wafer", O + "boron_mote"], [O + "pn_junction_wafer"]),
}

# Solvation machine — resin dissolved in acetone
SOLVATION = {
    "photoresist": {"requiredKineticForce": 20,
                    "inputFluid": {"fluid": O + "acetone", "amount": 1000},
                    "inputItem": {"item": M + "resin_clump", "amount": 2},
                    "outputFluid": {"fluid": O + "photoresist", "amount": 1000}},
}

COMPONENT_RECIPES = {
    # Wire saw slices the boule into wafers
    "silicon_wafer": shapeless(O + "silicon_wafer", [O + "silicon_boule", O + "steel_wire"], count=8),
    # Glass-passivated axial diodes diced from one junction wafer
    "diode": shaped(O + "diode", [" W ", "GJG", " W "],
                    {"W": O + "copper_wire", "G": M + "glass_pane", "J": O + "pn_junction_wafer"}, count=8),
    # Heavily doped junction breaks down at a fixed voltage
    "zener_diode": shapeless(O + "zener_diode",
                             [O + "pn_junction_wafer", O + "phosphorus_mote", O + "copper_wire",
                              O + "copper_wire", M + "glass_pane"], count=4),
    # Rolled aluminium foil + paper separator in a can; the big one has more foil
    "capacitor": shaped(O + "capacitor", ["AWA", "PPP", "AWA"],
                        {"A": O + "aluminium_plate", "W": O + "copper_wire", "P": M + "paper"}, count=8),
    "capacitor_1000uf": shaped(O + "capacitor_1000uf", ["AAA", "PWP", "AAA"],
                               {"A": O + "aluminium_plate", "W": O + "copper_wire", "P": M + "paper"}, count=4),
    "npn_transistor": shaped(O + "npn_transistor", ["NPN", "WWW"],
                             {"N": O + "n_type_wafer", "P": O + "p_type_wafer", "W": O + "copper_wire"}, count=8),
    "pnp_transistor": shaped(O + "pnp_transistor", ["PNP", "WWW"],
                             {"N": O + "n_type_wafer", "P": O + "p_type_wafer", "W": O + "copper_wire"}, count=8),
    "basic_machine_casing": shaped(O + "basic_machine_casing", ["PRP", "R R", "PRP"],
                                   {"P": O + "iron_plate", "R": O + "iron_rod"}, count=2),
    "machine_casing": shaped(O + "machine_casing", ["PRP", "RBR", "PRP"],
                             {"P": O + "steel_plate", "R": O + "steel_rod", "B": O + "basic_machine_casing"}),
    "pcb_workbench": shaped(O + "pcb_workbench", ["GGG", "WTW", "PIP"],
                            {"G": M + "glass_pane", "W": O + "copper_wire", "T": M + "crafting_table",
                             "P": O + "iron_plate", "I": O + "iron_rod"}),
    # UV lamp over a resist bath and an etching tray — no circuits, it is what makes them
    "pcb_fabricator": shaped(O + "pcb_fabricator", ["ILI", "FBF", "IPI"],
                             {"I": O + "iron_plate", "L": M + "glowstone", "F": O + "fluid_pipe",
                              "B": O + "basic_machine_casing", "P": M + "piston"}),
    # Solder spool, iron and heating coil on a bench
    "soldering_station": shaped(O + "soldering_station", ["WRC", "PPP"],
                                {"W": O + "tin_wire", "R": O + "iron_rod", "C": O + "copper_coil",
                                 "P": O + "iron_plate"}),
}

# Resistor: carbon on a ceramic core, crafted blank; the value is painted on later as bands
COMPONENT_RECIPES["resistor"] = shapeless(O + "resistor", [O + "graphite_dust", M + "clay_ball",
                                                          O + "copper_wire", O + "copper_wire"], count=8)

# Colour-code band ordinals (pcb/ResistorCode.Band)
BAND = {n: i for i, n in enumerate(["black", "brown", "red", "orange", "yellow", "green", "blue",
                                     "violet", "grey", "white", "gold", "silver"])}
BAND_NAMES = {
    "black": ("Black", "Чёрный"), "brown": ("Brown", "Коричневый"), "red": ("Red", "Красный"),
    "orange": ("Orange", "Оранжевый"), "yellow": ("Yellow", "Жёлтый"), "green": ("Green", "Зелёный"),
    "blue": ("Blue", "Синий"), "violet": ("Violet", "Фиолетовый"), "grey": ("Grey", "Серый"),
    "white": ("White", "Белый"), "gold": ("Gold", "Золотой"), "silver": ("Silver", "Серебряный"),
}
# Metallic bands: anything in these tags
BAND_TAGS = {
    "gold": [M + "gold_nugget", O + "gold_dust", O + "gold_mote"],
    "silver": [O + "silver_nugget", O + "silver_dust", O + "silver_mote"],
}


def resistor(ohms, tolerance="gold"):
    """Band ordinals for a 4-band resistor, e.g. resistor(4700) → yellow violet red gold."""
    import math
    exp = math.floor(math.log10(ohms)) - 1
    two = round(ohms / 10 ** exp)
    if two >= 100:
        two //= 10
        exp += 1
    mult = {-1: "gold", -2: "silver"}.get(exp, None)
    return [two // 10, two % 10, BAND[mult] if mult else exp, BAND[tolerance]]


# Circuit shorthands used in machine recipes
STAB, PWR, CTL = O + "stabilizer_circuit", O + "power_circuit", O + "control_circuit"
BASIC, CASE = O + "basic_machine_casing", O + "machine_casing"

MACHINES = {
    # ── Primitive / kinetic / thermal — no electronics ────────────────────────
    "alloy_furnace": shaped(O + "alloy_furnace", ["BIB", "BFB", "BBB"],
                            {"B": M + "bricks", "I": O + "iron_plate", "F": M + "furnace"}),
    "smelter": shaped(O + "smelter", ["B B", "BCB", "BFB"],
                      {"B": M + "bricks", "C": M + "cauldron", "F": M + "furnace"}),
    "foundry": shaped(O + "foundry", ["I I", "ICI", "SSS"],
                      {"I": O + "iron_plate", "C": M + "cauldron", "S": M + "smooth_stone"}),
    "glass_blowing_station": shaped(O + "glass_blowing_station", ["RGR", "BFB", "BBB"],
                                    {"R": O + "iron_rod", "G": M + "glass", "B": M + "bricks", "F": M + "furnace"}),
    "manual_macerator": shaped(O + "manual_macerator", ["CGC", "S S", "SSS"],
                               {"C": O + "wooden_cog", "G": M + "grindstone", "S": M + "cobblestone"}),
    "manual_centrifuge": shaped(O + "manual_centrifuge", ["PCP", "RBR", "SSS"],
                                {"P": "#minecraft:planks", "C": O + "wooden_cog", "R": O + "wooden_reductor",
                                 "B": M + "bucket", "S": M + "cobblestone"}),
    "kf_generator": shaped(O + "kf_generator", ["SCS", "CFC", "SRS"],
                           {"S": M + "cobblestone", "C": O + "wooden_cog", "F": M + "furnace",
                            "R": O + "wooden_reductor"}),
    "conveyor_belt": shaped(O + "conveyor_belt", ["LLL", "CRC"],
                            {"L": M + "leather", "C": O + "wooden_cog", "R": O + "iron_rod"}, count=4),
    "sorter": shaped(O + "sorter", ["IHI", "ROR", "III"],
                     {"I": O + "iron_plate", "H": M + "hopper", "R": M + "redstone", "O": M + "observer"}),
    "fluid_pipe": shaped(O + "fluid_pipe", ["CCC", "   ", "CCC"], {"C": O + "copper_plate"}, count=8),
    "fluid_tank": shaped(O + "fluid_tank", ["IGI", "G G", "IGI"], {"I": O + "iron_plate", "G": M + "glass"}),
    "pump": shaped(O + "pump", ["IPI", "FCF", "IRI"],
                   {"I": O + "iron_plate", "P": M + "piston", "F": O + "fluid_pipe",
                    "C": O + "wooden_cog", "R": O + "wooden_reductor"}),
    "valve": shapeless(O + "valve", [O + "fluid_pipe", O + "iron_plate", O + "iron_rod"]),
    "boiler": shaped(O + "boiler", ["CGC", "C C", "CCC"], {"C": O + "copper_plate", "G": M + "glass"}),
    "fluid_collector": shaped(O + "fluid_collector", ["IHI", "IBI", "IFI"],
                              {"I": O + "iron_plate", "H": M + "hopper", "B": M + "bucket", "F": O + "fluid_pipe"}),
    "fluid_filler": shaped(O + "fluid_filler", ["IFI", "GBG", "III"],
                           {"I": O + "iron_plate", "F": O + "fluid_pipe", "G": M + "glass", "B": M + "bucket"}),
    "filter_press": shaped(O + "filter_press", ["IPI", "WCW", "IBI"],
                           {"I": O + "iron_plate", "P": M + "piston", "W": M + "white_wool",
                            "C": O + "wooden_cog", "B": M + "bucket"}),
    "fermenter": shaped(O + "fermenter", ["IGI", "IBI", "IFI"],
                        {"I": O + "iron_plate", "G": M + "glass", "B": M + "barrel", "F": O + "fluid_pipe"}),
    "structure_table": shaped(O + "structure_table", ["GBG", "PPP", "P P"],
                              {"G": M + "glass_bottle", "B": M + "book", "P": "#minecraft:planks"}),
    "research_table": shaped(O + "research_table", ["PBP", "WTW", "W W"],
                             {"P": M + "paper", "B": M + "book", "T": M + "crafting_table", "W": "#minecraft:planks"}),
    "heat_exchanger": shaped(O + "heat_exchanger", ["PFP", "FCF", "PFP"],
                             {"P": O + "iron_plate", "F": O + "fluid_pipe", "C": O + "copper_coil"}),
    "radiator": shaped(O + "radiator", ["CRC", "CRC", "CRC"], {"C": O + "copper_plate", "R": O + "iron_rod"}),
    "thermal_conductor": shaped(O + "thermal_conductor", ["RRR"], {"R": O + "copper_rod"}, count=4),
    "solvation_machine": shaped(O + "solvation_machine", ["IGI", "CBC", "IRI"],
                                {"I": O + "iron_plate", "G": M + "glass", "C": O + "wooden_cog",
                                 "B": M + "cauldron", "R": O + "wooden_reductor"}),
    "extractor": shaped(O + "extractor", ["IPI", "GBG", "ICI"],
                        {"I": O + "iron_plate", "P": M + "piston", "G": M + "glass",
                         "B": M + "bucket", "C": O + "wooden_cog"}),
    "chemical_infuser": shaped(O + "chemical_infuser", ["IGI", "FCF", "IRI"],
                               {"I": O + "iron_plate", "G": M + "glass", "F": O + "fluid_pipe",
                                "C": M + "cauldron", "R": O + "wooden_reductor"}),
    "chemical_mixer": shaped(O + "chemical_mixer", ["IFI", "GCG", "IFI"],
                             {"I": O + "iron_plate", "F": O + "fluid_pipe", "G": M + "glass", "C": M + "cauldron"}),
    "rotary_compressor": shaped(O + "rotary_compressor", ["SCS", "FRF", "SCS"],
                                {"S": O + "steel_plate", "C": O + "steel_cog", "F": O + "fluid_pipe",
                                 "R": O + "steel_reductor"}),
    "decompressor": shaped(O + "decompressor", ["SPS", "FTF", "SCS"],
                           {"S": O + "steel_plate", "P": M + "piston", "F": O + "fluid_pipe",
                            "T": O + "fluid_tank", "C": O + "steel_cog"}),
    "chemical_reactor": shaped(O + "chemical_reactor", ["SGS", "FMF", "SHS"],
                               {"S": O + "steel_plate", "G": M + "glass", "F": O + "fluid_pipe",
                                "M": CASE, "H": O + "heater"}),
    "fractional_distiller": shaped(O + "fractional_distiller", ["SGS", "SGS", "FBF"],
                                   {"S": O + "steel_plate", "G": M + "glass", "F": O + "fluid_pipe",
                                    "B": O + "boiler"}),
    "power_transformer": shaped(O + "power_transformer", ["PWP", "CIC", "PBP"],
                                {"P": O + "iron_plate", "W": O + "electric_wire", "C": O + "copper_coil",
                                 "I": M + "iron_ingot", "B": BASIC}),

    # ── Electric machines — basic casing + power electronics ─────────────────
    "electric_engine": shaped(O + "electric_engine", ["PKP", "CRC", "PBP"],
                              {"P": O + "steel_plate", "K": PWR, "C": O + "copper_coil",
                               "R": O + "steel_rod", "B": BASIC}),
    "electric_furnace": shaped(O + "electric_furnace", ["PKP", "CFC", "PBP"],
                               {"P": O + "iron_plate", "K": PWR, "C": O + "copper_coil",
                                "F": M + "furnace", "B": BASIC}),
    "electric_heater": shaped(O + "electric_heater", ["PCP", "CBC", "PKP"],
                              {"P": O + "iron_plate", "C": O + "copper_coil", "B": BASIC, "K": PWR}),
    "electric_charger": shaped(O + "electric_charger", ["PKP", "WBW", "PCP"],
                               {"P": O + "iron_plate", "K": STAB, "W": O + "electric_wire",
                                "B": BASIC, "C": O + "capacitor"}),
    "electric_capacitor": shaped(O + "electric_capacitor", ["PKP", "CBC", "PCP"],
                                 {"P": O + "iron_plate", "K": STAB, "C": O + "capacitor", "B": BASIC}),
    "power_relay": shaped(O + "power_relay", ["PWP", "CKC", "PBP"],
                          {"P": O + "iron_plate", "W": O + "electric_wire", "C": O + "copper_coil",
                           "K": PWR, "B": BASIC}),
    "solar_panel": shaped(O + "solar_panel", ["GGG", "JJJ", "PKP"],
                          {"G": M + "glass_pane", "J": O + "pn_junction_wafer", "P": O + "iron_plate", "K": STAB}),
    "electrolysis_machine": shaped(O + "electrolysis_machine", ["RKR", "GBG", "PFP"],
                                   {"R": O + "graphite_rod", "K": STAB, "G": M + "glass", "B": BASIC,
                                    "P": O + "iron_plate", "F": O + "fluid_pipe"}),
    "assembler": shaped(O + "assembler", ["SKS", "PMP", "SCS"],
                        {"S": O + "steel_plate", "K": CTL, "P": M + "piston", "M": CASE, "C": O + "steel_cog"}),

    # ── Logic, computing and radio — control circuits ─────────────────────────
    "logic_machine": shaped(O + "logic_machine", ["PKP", "SMS", "PWP"],
                            {"P": O + "steel_plate", "K": CTL, "S": STAB, "M": CASE, "W": O + "copper_wire"}),
    "display": shaped(O + "display", ["PGP", "LKL", "PSP"],
                      {"P": O + "iron_plate", "G": M + "glass_pane", "L": M + "glowstone_dust",
                       "K": CTL, "S": STAB}),
    "display_mk2": shaped(O + "display_mk2", ["GLG", "LDL", "GKG"],
                          {"G": O + "gold_wire", "L": M + "glowstone_dust", "D": O + "display", "K": CTL}),
    "display_mk3": shaped(O + "display_mk3", ["GQG", "QDQ", "GKG"],
                          {"G": O + "gold_wire", "Q": M + "quartz", "D": O + "display_mk2", "K": CTL}),
    "programming_station": shaped(O + "programming_station", ["PDP", "KBK", "PSP"],
                                  {"P": O + "iron_plate", "D": O + "display", "K": CTL, "B": BASIC, "S": STAB}),
    "keyboard": shaped(O + "keyboard", ["BBB", "PKP"],
                       {"B": M + "stone_button", "P": O + "iron_plate", "K": CTL}),
    "floppy_drive": shaped(O + "floppy_drive", ["PKP", "CRC", "PPP"],
                           {"P": O + "iron_plate", "K": CTL, "C": O + "copper_coil", "R": O + "iron_rod"}),
    "expansion_slot": shaped(O + "expansion_slot", ["GWG", "PKP"],
                             {"G": O + "gold_wire", "W": O + "copper_wire", "P": O + "iron_plate", "K": CTL}),
    "gpio_port": shaped(O + "gpio_port", ["RWR", "PKP"],
                        {"R": M + "redstone", "W": O + "copper_wire", "P": O + "iron_plate", "K": PWR}),
    "logic_cable": shaped(O + "logic_cable", ["WDW"], {"W": O + "copper_wire", "D": M + "green_dye"}, count=3),
    "logic_gate_block": shaped(O + "logic_gate_block", ["RQR", "PPP"],
                               {"R": M + "redstone", "Q": O + "npn_transistor", "P": O + "iron_plate"}),
    "analog_cable": shaped(O + "analog_cable", ["WDW"], {"W": O + "copper_wire", "D": M + "yellow_dye"}, count=3),
    "microphone": shaped(O + "microphone", ["PBP", "CKC", "PPP"],
                         {"P": O + "iron_plate", "B": M + "iron_bars", "C": O + "copper_coil", "K": PWR}),
    "speaker": shaped(O + "speaker", ["PAP", "CKC", "PPP"],
                      {"P": O + "iron_plate", "A": M + "paper", "C": O + "copper_coil", "K": PWR}),
    "radio_transmitter": shaped(O + "radio_transmitter", ["IRI", "CKC", "PSP"],
                                {"I": O + "iron_plate", "R": O + "iron_rod", "C": O + "copper_coil",
                                 "K": CTL, "P": O + "iron_plate", "S": STAB}),
    "radio_receiver": shaped(O + "radio_receiver", ["IRI", "CKC", "PQP"],
                             {"I": O + "iron_plate", "R": O + "iron_rod", "C": O + "copper_coil",
                              "K": CTL, "P": O + "iron_plate", "Q": PWR}),
    "radio_scanner": shaped(O + "radio_scanner", ["IRI", "KDK", "PSP"],
                            {"I": O + "iron_plate", "R": O + "iron_rod", "K": CTL, "D": O + "display",
                             "P": O + "iron_plate", "S": STAB}),

    # ── Nuclear, space ────────────────────────────────────────────────────────
    "reactor_block": shaped(O + "reactor_block", ["LSL", "S S", "LSL"],
                            {"L": O + "lead_plate", "S": O + "steel_plate"}, count=4),
    "reactor_port": shaped(O + "reactor_port", ["FKF", "PRP"],
                           {"F": O + "fluid_pipe", "K": CTL, "P": O + "lead_plate", "R": O + "reactor_block"}),
    "reactor_cell": shaped(O + "reactor_cell", ["LGL", "GRG", "LGL"],
                           {"L": O + "lead_plate", "G": M + "glass", "R": O + "reactor_block"}),
    "rocket_controller": shaped(O + "rocket_controller", ["TKT", "KMK", "TDT"],
                                {"T": O + "titanium_plate", "K": CTL, "M": CASE, "D": O + "display"}),
    "orrery": shaped(O + "orrery", ["GCG", "CKC", "BBB"],
                     {"G": M + "glass", "C": O + "brass_cog", "K": CTL, "B": O + "brass_plate"}),
    "gravitation_source": shaped(O + "gravitation_source", ["NKN", "KSK", "NMN"],
                                 {"N": O + "neodymium_plate", "K": CTL, "S": M + "nether_star", "M": CASE}),
}



# ════════════════════════════════════════════════════════════════════════════
# Ready-made blueprints
# ════════════════════════════════════════════════════════════════════════════
# Footprints mirror pcb/PartSpec.java: offsets from the part's centre at rotation 0;
# each rotation is a clockwise quarter turn (dx, dy) → (−dy, dx).
_TWO = {"pins": [(-1, 0), (1, 0)], "body": [(0, 0)]}
_SOT = {"pins": [(0, -1), (-1, 1), (1, 1)], "body": [(-1, 0), (0, 0), (1, 0)]}   # C, B, E
GRID_W = 24          # PcbDesign.MAX_W — cell index stride
GRID_CELLS = 24 * 16


def _footprint(item):
    return _SOT if item.endswith("_transistor") else _TWO


def _rot(dx, dy, r):
    for _ in range(r & 3):
        dx, dy = -dy, dx
    return dx, dy


def _cells(item, x, y, r, key):
    return [(x + a, y + b) for a, b in (_rot(dx, dy, r) for dx, dy in _footprint(item)[key])]


BLUEPRINTS = {
    # Full-wave bridge → 1000 µF reservoir → 100 Ω + 5.1 V zener shunt regulator
    "power_stabilizer": {
        "name": "Power Stabilizer", "bench": "1_power_stabilizer", "key": O + "zener_diode",
        "size": (16, 10),
        "parts": [("diode", 3, 2, 3, ["GND", "AC1"]), ("diode", 5, 1, 0, ["AC1", "VDC"]),
                  ("diode", 3, 7, 1, ["GND", "AC2"]), ("diode", 5, 8, 0, ["AC2", "VDC"]),
                  ("capacitor_1000uf", 5, 4, 2, ["VDC", "GND"]), ("resistor", 9, 3, 0, ["VDC", "OUT"], resistor(100)),
                  ("zener_diode", 5, 6, 0, ["GND", "OUT"])],
        "terminals": [(1, 1, "AC1"), (1, 8, "AC2"), (8, 5, "OUT"), (4, 5, "GND")],
        "copper": ["................",
                   "..#....######...",
                   "......#.#...#...",
                   "......#.....#...",
                   "...#......#.#...",
                   "...#......#.#...",
                   ".......####.#...",
                   "............#...",
                   "..#....######...",
                   "................"],
    },
    # NPN low-side switch: 1 kΩ base drive, 10 kΩ pull-down, flyback diode to V+
    "power_switch": {
        "name": "Power Switch", "bench": "2_power_switch", "key": O + "npn_transistor",
        "size": (16, 10),
        "parts": [("resistor", 3, 2, 0, ["IN", "BASE"], resistor(1000)), ("resistor", 5, 7, 1, ["BASE", "GND"], resistor(10000)),
                  ("npn_transistor", 8, 4, 0, ["OUT", "BASE", "GND"]), ("diode", 13, 3, 1, ["OUT", "V+"])],
        "terminals": [(0, 2, "IN"), (12, 2, "OUT"), (12, 8, "GND"), (13, 5, "V+")],
        "copper": ["................",
                   "................",
                   ".#...#..####....",
                   ".....#..........",
                   ".....#..........",
                   ".....##.........",
                   ".........#......",
                   ".........#......",
                   "......######....",
                   "................"],
    },
    # Astable multivibrator: cross-coupled NPNs, 1 kΩ collectors, 10 kΩ bases, 100 µF
    "clock_oscillator": {
        "name": "Clock Oscillator", "bench": "3_clock_oscillator", "key": O + "capacitor",
        "size": (16, 10),
        "parts": [("npn_transistor", 3, 4, 0, ["Q1C", "Q1B", "GND"]), ("npn_transistor", 12, 5, 2, ["OUT", "Q2B", "GND"]),
                  ("capacitor", 8, 2, 0, ["Q1C", "Q2B"]), ("capacitor", 8, 7, 2, ["OUT", "Q1B"]),
                  ("resistor", 4, 1, 1, ["V+", "Q1C"], resistor(1000)), ("resistor", 11, 1, 1, ["V+", "Q2B"], resistor(10000)),
                  ("resistor", 4, 8, 3, ["V+", "Q1B"], resistor(10000)), ("resistor", 11, 8, 3, ["V+", "OUT"], resistor(1000))],
        "terminals": [(0, 9, "V+"), (7, 4, "GND"), (13, 7, "OUT")],
        "copper": ["####.######.####",
                   "...............#",
                   "...#.##...#.##.#",
                   ".............#.#",
                   "..........#....#",
                   ".....######....#",
                   "..#............#",
                   "..##.##...#.#..#",
                   "...............#",
                   ".###.######.####"],
    },
}


def _check_routing(bid, bp, pads, copper):
    """Every pin/terminal of a named net on one copper island, distinct nets apart."""
    island, n = {}, 0
    for start in copper:
        if start in island:
            continue
        island[start], stack = n, [start]
        while stack:
            x, y = stack.pop()
            for d in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if d in copper and d not in island:
                    island[d] = n
                    stack.append(d)
        n += 1
    nets = {}
    for item, x, y, r, names, *_ in bp["parts"]:
        for c, net in zip(_cells(item, x, y, r, "pins"), names):
            nets.setdefault(net, set()).add(island[c])
    for x, y, label in bp["terminals"]:
        nets.setdefault(label, set()).add(island[(x, y)])
    owner = {}
    for net, isl in nets.items():
        if len(isl) != 1:
            sys.exit(f"blueprint {bid}: net {net} is not connected")
        i = next(iter(isl))
        if owner.setdefault(i, net) != net:
            sys.exit(f"blueprint {bid}: {owner[i]} shorted to {net}")


def build_blueprints():
    for bid, bp in BLUEPRINTS.items():
        w, h = bp["size"]
        occupied, pads = set(), set()
        for item, x, y, r, *_ in bp["parts"]:
            fp = _cells(item, x, y, r, "pins") + _cells(item, x, y, r, "body")
            if occupied & set(fp):
                sys.exit(f"blueprint {bid}: {item} overlaps another part")
            occupied |= set(fp)
            pads |= set(_cells(item, x, y, r, "pins"))
        pads |= {(x, y) for x, y, _ in bp["terminals"]}
        traces = {(x, y) for y, row in enumerate(bp["copper"]) for x, ch in enumerate(row) if ch == "#"}
        if traces & pads:
            sys.exit(f"blueprint {bid}: copper drawn over a pad")
        _check_routing(bid, bp, pads, traces | pads)

        cells = ["0"] * GRID_CELLS
        for y in range(h):
            for x in range(w):
                cells[y * GRID_W + x] = str(1 | (2 if (x, y) in traces else 0) | (4 if (x, y) in pads else 0))
        design = {"name": bp["name"], "width": w, "height": h, "cells": "".join(cells),
                  "labels": {str(y * GRID_W + x): label for x, y, label in bp["terminals"]}}
        parts = []
        for item, x, y, r, _nets, *bands in bp["parts"]:
            part = {"item": O + item, "x": x, "y": y, "rot": r}
            if bands:
                part["bands"] = bands[0]
            parts.append(part)

        write_json(os.path.join(DATA, "pcb_blueprint", bid + ".json"),
                   {"bench": bp["bench"], "design": design, "parts": parts})
        recipe = shapeless(O + "pcb_blueprint", [M + "paper", M + "blue_dye", bp["key"]])
        recipe["result"]["components"] = {"omnitech:pcb_design": design, "omnitech:pcb_parts": parts}
        write_json(os.path.join(DATA, "recipe", "pcb_blueprint_" + bid + ".json"), recipe)
    print(f"  ready blueprints: {len(BLUEPRINTS)}, routing OK")



def existing_results():
    out = {}
    folder = os.path.join(DATA, "recipe")
    for f in os.listdir(folder):
        if not f.endswith(".json"):
            continue
        try:
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                r = json.load(fh).get("result", {})
        except (json.JSONDecodeError, AttributeError):
            continue
        rid = r.get("id") if isinstance(r, dict) else None
        if rid:
            out.setdefault(rid, []).append(f)
    return out


def build_recipes():
    have = existing_results()
    written = skipped = 0
    for name, recipe in {**COMPONENT_RECIPES, **MACHINES}.items():
        rid = recipe["result"]["id"]
        path = os.path.join(DATA, "recipe", name + ".json")
        if rid in have and os.path.basename(path) not in have[rid]:
            print(f"  skip {name}: already made by {have[rid]}")
            skipped += 1
            continue
        write_json(path, recipe)
        written += 1
    for name, (temp, ins, outs) in ALLOY.items():
        write_json(os.path.join(DATA, "machine_recipe", "alloy_furnace", name + ".json"),
                   {"minTemperature": temp, "ingredients": ins, "output": outs})
        written += 1
    for name, recipe in SOLVATION.items():
        write_json(os.path.join(DATA, "machine_recipe", "solvation", name + ".json"), recipe)
        written += 1
    print(f"  recipes: {written} written, {skipped} skipped")


# ════════════════════════════════════════════════════════════════════════════
# Assets, loot, tags, tabs, lang
# ════════════════════════════════════════════════════════════════════════════

def item_assets(iid, tex):
    if not texture_exists("item/" + tex):
        sys.exit(f"missing texture item/{tex}.png for {iid}")
    write_json(os.path.join(ASSETS, "items", iid + ".json"),
               {"model": {"type": "minecraft:model", "model": "omnitech:item/" + iid}})
    write_json(os.path.join(ASSETS, "models", "item", iid + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "omnitech:item/" + tex}})


def build_items():
    for iid, (tex, _en, _ru, js) in ITEMS.items():
        write_json(os.path.join(DATA, "item", iid + ".json"), js)
        item_assets(iid, tex)
    for iid, (tex, _en, _ru) in JAVA_ITEMS.items():
        item_assets(iid, tex)


def block_assets(bid, model, rotatable):
    write_json(os.path.join(ASSETS, "models", "block", bid + ".json"), model)
    if rotatable:
        variants = {f"facing={f}": ({"model": "omnitech:block/" + bid, "y": y} if y else
                                    {"model": "omnitech:block/" + bid})
                    for f, y in (("north", 0), ("south", 180), ("west", 270), ("east", 90))}
    else:
        variants = {"": {"model": "omnitech:block/" + bid}}
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": variants})
    write_json(os.path.join(ASSETS, "items", bid + ".json"),
               {"model": {"type": "minecraft:model", "model": "omnitech:block/" + bid}})
    self_drop(bid)


def self_drop(bid, overwrite=True):
    return write_json(os.path.join(DATA, "loot_table", "blocks", bid + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "omnitech:" + bid}],
                   "condition": {"type": "minecraft:survives_explosion"}}],
        "random_sequence": "omnitech:blocks/" + bid,
    }, overwrite=overwrite)


def build_machine_loot():
    """Machines that are now craftable must drop themselves when mined."""
    added = [m for m in MACHINES if self_drop(m, overwrite=False)]
    print(f"  loot tables: +{len(added)}")


def check_texture(ref):
    ns, path = ref.split(":", 1)
    if ns == "omnitech" and not texture_exists(path):
        sys.exit(f"missing texture {ref}")


def build_blocks():
    for bid, (_en, _ru, tex) in CASINGS.items():
        for t in tex.values():
            check_texture(t)
        write_json(os.path.join(DATA, "block", bid + ".json"),
                   {"map_color": "metal", "strength": [5.0, 6.0], "sound": "metal"})
        parent = "minecraft:block/cube_all" if "all" in tex else "minecraft:block/cube_bottom_top"
        block_assets(bid, {"parent": parent, "textures": tex}, rotatable=False)
    for bid, (_en, _ru, tex, _tool) in STATIONS.items():
        for t in tex.values():
            check_texture(t)
        block_assets(bid, {"parent": "minecraft:block/cube", "textures": tex}, rotatable=True)


def tint(src, dst, color, only_changed_vs=None):
    """Recolours a texture by luminance. With {@code only_changed_vs}, only pixels that
    differ from that reference image (the fluid in a bucket) are recoloured."""
    img = Image.open(src).convert("RGBA")
    ref = Image.open(only_changed_vs).convert("RGBA") if only_changed_vs else None
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0 or (ref is not None and ref.getpixel((x, y)) == (r, g, b, a)):
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            k = 0.35 + 0.9 * lum
            px[x, y] = tuple(min(255, int(c * k)) for c in color) + (a,)
    img.save(dst)


def build_photoresist():
    name = "photoresist"
    write_json(os.path.join(DATA, "fluid", name + ".json"), PHOTORESIST)
    fluid_tex = os.path.join(ASSETS, "textures", "block", "fluid")
    for kind in ("still", "flow"):
        dst = os.path.join(fluid_tex, f"{name}_{kind}.png")
        if not os.path.exists(dst):
            tint(os.path.join(fluid_tex, f"acetone_{kind}.png"), dst, PHOTORESIST_COLOR)
        meta = os.path.join(fluid_tex, f"acetone_{kind}.png.mcmeta")
        with open(meta, encoding="utf-8") as f:
            mcmeta = f.read()
        with open(dst + ".mcmeta", "w", encoding="utf-8") as f:
            f.write(mcmeta)
    item_tex = os.path.join(ASSETS, "textures", "item")
    bucket = os.path.join(item_tex, name + "_bucket.png")
    if not os.path.exists(bucket):
        # pixels where two existing fluid buckets differ are the liquid, the rest is the pail
        tint(os.path.join(item_tex, "acetone_bucket.png"), bucket, PHOTORESIST_COLOR,
             only_changed_vs=os.path.join(item_tex, "brine_bucket.png"))
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
               {"variants": {"": {"model": "omnitech:block/fluid/" + name}}})
    write_json(os.path.join(ASSETS, "models", "block", "fluid", name + ".json"),
               {"textures": {"particle": "omnitech:block/fluid/" + name + "_still"}})
    item_assets(name + "_bucket", name + "_bucket")


def build_resistor():
    """Colour-coded resistor: composite model (item/generated stops at 5 layers), tags, recipes."""
    tex = "omnitech:item/resistor/"
    for t in ["resistor_pins", "resistor_base_tint"] + [f"resistor_band_{i}of6" for i in range(1, 7)]:
        if not texture_exists("item/resistor/" + t):
            sys.exit(f"missing texture item/resistor/{t}.png")
    models = os.path.join(ASSETS, "models", "item")
    # layer 0 pins (untinted), 1 body, 2-4 bands 1-3 …
    write_json(os.path.join(models, "resistor_body.json"), {"parent": "minecraft:item/generated", "textures": {
        "layer0": tex + "resistor_pins", "layer1": tex + "resistor_base_tint",
        "layer2": tex + "resistor_band_1of6", "layer3": tex + "resistor_band_2of6", "layer4": tex + "resistor_band_3of6"}})
    # … and bands 4-6 drawn over it
    write_json(os.path.join(models, "resistor_bands.json"), {"parent": "minecraft:item/generated", "textures": {
        "layer0": tex + "resistor_band_4of6", "layer1": tex + "resistor_band_5of6", "layer2": tex + "resistor_band_6of6"}})

    def tint(layer):
        return {"type": "omnitech:resistor_tint", "layer": layer}
    write_json(os.path.join(ASSETS, "items", "resistor.json"), {"model": {
        "type": "minecraft:composite",
        "models": [
            {"type": "minecraft:model", "model": "omnitech:item/resistor_body",
             "tints": [{"type": "minecraft:constant", "value": 16777215}, tint(0), tint(1), tint(2), tint(3)]},
            {"type": "minecraft:model", "model": "omnitech:item/resistor_bands",
             "tints": [tint(4), tint(5), tint(6)]},
        ]}})
    # the old single-texture model is gone
    old = os.path.join(models, "resistor.json")
    if os.path.exists(old):
        os.remove(old)

    for metal, items in BAND_TAGS.items():
        write_json(os.path.join(DATA, "tags", "item", "resistor_band", metal + ".json"), {"values": items})
    write_json(os.path.join(DATA, "recipe", "resistor_coding.json"), {"type": "omnitech:resistor_coding"})

    # Fixed-value resistors from the previous design
    for v in ("100r", "470r", "1k", "4k7", "10k", "47k"):
        rid = "resistor_" + v
        for path in (os.path.join(DATA, "item", rid + ".json"), os.path.join(ASSETS, "items", rid + ".json"),
                     os.path.join(models, rid + ".json"), os.path.join(DATA, "recipe", rid + ".json")):
            if os.path.exists(path):
                os.remove(path)


def add_to_tag(path, ids):
    with open(path, encoding="utf-8") as f:
        tag = json.load(f)
    for i in ids:
        if i not in tag["values"]:
            tag["values"].append(i)
    write_json(path, tag)


def build_tags_and_tabs():
    add_to_tag(os.path.join(MC_TAGS, "block", "mineable", "pickaxe.json"),
               ["omnitech:" + b for b in CASINGS]
               + ["omnitech:" + b for b, v in STATIONS.items() if v[3] == "pickaxe"])
    add_to_tag(os.path.join(MC_TAGS, "block", "mineable", "axe.json"),
               ["omnitech:" + b for b, v in STATIONS.items() if v[3] == "axe"])

    def tab_add(tab, ids):
        path = os.path.join(DATA, "creative_tab", tab + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for i in ids:
            if i not in data["data"]:
                data["data"].append(i)
        write_json(path, data)

    tab_add("omnitech.machines", ["omnitech:" + b for b in STATIONS] + ["omnitech:" + b for b in CASINGS]
            + ["omnitech:photoresist_bucket"])
    tab_add("omnitech.materials", ["omnitech:" + i for i in ITEMS] + ["omnitech:resistor"])
    tab_add("omnitech.utility", ["omnitech:pcb_blueprint", "omnitech:printed_circuit_board",
                                 "omnitech:assembled_circuit_board"])


LANG = {
    "container.omnitech.pcb_workbench": ("PCB Workbench", "Верстак печатных плат"),
    "container.omnitech.pcb_fabricator": ("PCB Fabricator", "Фабрикатор печатных плат"),
    "container.omnitech.soldering_station": ("Soldering Station", "Паяльная станция"),
    "fluid.omnitech.photoresist": ("Photoresist", "Фоторезист"),
    "block.omnitech.photoresist": ("Photoresist", "Фоторезист"),
    "item.omnitech.photoresist_bucket": ("Photoresist Bucket", "Ведро фоторезиста"),

    "gui.omnitech.pcb.tool.trace": ("Trace", "Дорожка"),
    "gui.omnitech.pcb.tool.trace.tooltip": ("Draw copper traces (T). Right mouse erases copper.",
                                            "Рисовать медные дорожки (T). Правая кнопка стирает медь."),
    "gui.omnitech.pcb.tool.pad": ("Pad", "Площадка"),
    "gui.omnitech.pcb.tool.pad.tooltip": ("Drill pin holes for component legs (P).",
                                          "Сверлить отверстия под выводы деталей (P)."),
    "gui.omnitech.pcb.tool.label": ("Terminal", "Вывод"),
    "gui.omnitech.pcb.tool.label.tooltip": ("Click a pad to cycle its terminal name: V+, GND, IN, OUT, AC1, AC2 (L). Right click clears.",
                                            "Клик по площадке перебирает имя вывода: V+, GND, IN, OUT, AC1, AC2 (L). Правый клик — убрать."),
    "gui.omnitech.pcb.tool.board": ("Shape", "Форма"),
    "gui.omnitech.pcb.tool.board.tooltip": ("Add board area; right mouse cuts it away (B).",
                                            "Добавить площадь платы; правая кнопка вырезает (B)."),
    "gui.omnitech.pcb.name": ("Board name", "Название платы"),
    "gui.omnitech.pcb.load": ("Load", "Загр."),
    "gui.omnitech.pcb.load.tooltip": ("Load the blueprint from the middle slot into the editor",
                                      "Загрузить чертёж из среднего слота в редактор"),
    "gui.omnitech.pcb.print": ("Print", "Печать"),
    "gui.omnitech.pcb.print.tooltip": ("Print the drawing on paper as a PCB blueprint",
                                       "Напечатать рисунок на бумаге как чертёж платы"),
    "gui.omnitech.pcb.clear": ("Clear", "Очистить"),
    "gui.omnitech.pcb.clear.tooltip": ("Erase all copper and pads", "Стереть всю медь и площадки"),
    "gui.omnitech.pcb.unplace.tooltip": ("Remove every placed part", "Убрать все установленные детали"),
    "gui.omnitech.pcb.info": ("%s pads · %s nets · %s terminals", "%s площ. · %s цепей · %s выв."),
    "gui.omnitech.pcb.terminal": ("Terminal %s", "Вывод %s"),
    "gui.omnitech.pcb.fab_cost": ("Per board: %s copper plate(s), %s mB photoresist",
                                  "На плату: медных пластин %s, фоторезиста %s mB"),
    "gui.omnitech.pcb.rotate": ("Rotate", "Поворот"),
    "gui.omnitech.pcb.rotate.tooltip": ("Rotate the part (R or mouse wheel)", "Повернуть деталь (R или колесо мыши)"),
    "gui.omnitech.pcb.test": ("Test", "Тест"),
    "gui.omnitech.pcb.test.tooltip": ("Simulate the board in every test bench", "Смоделировать плату на всех стендах"),
    "gui.omnitech.pcb.board": ("Board", "Плата"),
    "gui.omnitech.pcb.solder": ("Solder", "Паять"),
    "gui.omnitech.pcb.solder.tooltip": ("Solder the placed parts. Shift: a whole batch.",
                                        "Припаять детали. Shift — целую партию."),
    "gui.omnitech.pcb.simulating": ("Simulating…", "Моделирование…"),
    "gui.omnitech.pcb.no_signal": ("No signal", "Нет сигнала"),
    "gui.omnitech.pcb.insert_board": ("Insert a printed circuit board", "Вставьте печатную плату"),
    "gui.omnitech.pcb.parts_count": ("Parts: %s", "Деталей: %s"),
    "gui.omnitech.pcb.solder_count": ("Solder: %s", "Припой: %s"),
    "gui.omnitech.pcb.pins": ("Pins: %s", "Выводы: %s"),
    "gui.omnitech.pcb.bom": ("Bill of materials:", "Список материалов:"),
    "gui.omnitech.pcb.pin.A": ("Anode", "Анод"),
    "gui.omnitech.pcb.pin.K": ("Cathode (band)", "Катод (полоса)"),
    "gui.omnitech.pcb.pin.C": ("Collector", "Коллектор"),
    "gui.omnitech.pcb.pin.B": ("Base", "База"),
    "gui.omnitech.pcb.pin.E": ("Emitter", "Эмиттер"),
    "gui.omnitech.pcb.pin.+": ("Positive lead (+)", "Плюсовой вывод (+)"),
    "gui.omnitech.pcb.pin.-": ("Negative lead (stripe)", "Минусовой вывод (полоса)"),
    "gui.omnitech.pcb.pin.1": ("Lead", "Вывод"),
    "gui.omnitech.pcb.pin.2": ("Lead", "Вывод"),

    "pcb.omnitech.workbench.no_pads": ("The drawing has no pads to solder to", "На рисунке нет площадок для пайки"),
    "pcb.omnitech.workbench.no_paper": ("Put paper in the first slot", "Положите бумагу в первый слот"),
    "pcb.omnitech.output_full": ("Output slot is full", "Выходной слот заполнен"),
    "pcb.omnitech.solder.no_board": ("Insert a printed circuit board", "Вставьте печатную плату"),
    "pcb.omnitech.solder.no_parts": ("Place components on the board first", "Сначала установите детали на плату"),
    "pcb.omnitech.solder.missing": ("Missing components or solder", "Не хватает деталей или припоя"),
    "pcb.omnitech.solder.passed": ("Soldered %s × %s — test passed", "Спаяно %s × %s — тест пройден"),
    "pcb.omnitech.solder.untested": ("Soldered %s board(s) — no test bench passed",
                                     "Спаяно плат: %s — ни один стенд не пройден"),
    "pcb.omnitech.test.missing_terminal": ("Board has no %s terminal", "На плате нет вывода %s"),
    "pcb.omnitech.test.no_parts": ("No parts soldered", "Детали не установлены"),
    "pcb.omnitech.test.shorted": ("%s and %s are shorted", "%s и %s замкнуты"),
    "pcb.omnitech.test.diverged": ("Circuit did not settle (simulation diverged)",
                                   "Схема не установилась (моделирование расходится)"),
    "pcb.omnitech.check.mean": ("Average %2$s V over %1$s s (needs %3$s V)", "Среднее %2$s В за %1$s с (нужно %3$s В)"),
    "pcb.omnitech.check.ripple": ("Ripple %2$s V over %1$s s (max %3$s V)", "Пульсации %2$s В за %1$s с (макс. %3$s В)"),
    "pcb.omnitech.check.swing": ("Swing %2$s V over %1$s s (min %3$s V)", "Размах %2$s В за %1$s с (мин. %3$s В)"),
    "pcb.omnitech.check.transitions": ("%2$s edges over %1$s s (min %3$s)", "Фронтов: %2$s за %1$s с (мин. %3$s)"),
    "pcb.omnitech.check.balance": ("AC draw imbalance %2$s%% (max %3$s%%)", "Несимметрия тока сети %2$s%% (макс. %3$s%%)"),
    "pcb.omnitech.check.window": ("Measurement window is empty", "Окно измерения пусто"),
    "pcb.omnitech.check.unknown": ("Unknown check %s", "Неизвестная проверка %s"),

    "circuit_test.omnitech.1_power_stabilizer": ("Stabilizer", "Стабилизатор"),
    "circuit_test.omnitech.2_power_switch": ("Switch", "Ключ"),
    "circuit_test.omnitech.3_clock_oscillator": ("Clock", "Генератор"),

    "gui.omnitech.pcb.reference": ("★", "★"),
    "gui.omnitech.pcb.reference.tooltip": ("Place the parts where the ready-made blueprint says",
                                           "Расставить детали по готовому чертежу"),
    "tooltip.omnitech.pcb.reference": ("Ready-made: parts placement included (%s)",
                                       "Готовый чертёж: расстановка деталей включена (%s)"),

    "jei.omnitech.pcb_fabrication": ("PCB Fabrication", "Изготовление плат"),
    "jei.omnitech.pcb_fabrication.mask": ("Blueprint is the photomask, not used up",
                                          "Чертёж — фотошаблон, не расходуется"),
    "jei.omnitech.soldering": ("Soldering & Testing", "Пайка и испытание"),
    "jei.omnitech.soldering.test": ("Test: %s", "Стенд: %s"),
    "jei.omnitech.soldering.reference": ("Reference placement — the Soldering Station fills it in for you",
                                         "Эталонная расстановка — паяльная станция расставит детали сама"),
    "jei.omnitech.info.pcb_workbench": (
        "Draw a circuit board: Trace paints copper, Pad drills pin holes, Shape adds or cuts board area, "
        "Terminal names a pad (V+, GND, IN, OUT, AC1, AC2). Print it on paper to get a PCB Blueprint. "
        "Not sure where to start? Craft a ready-made blueprint (paper + blue dye + zener diode, NPN transistor or capacitor).",
        "Нарисуйте плату: Дорожка — медь, Площадка — отверстия под выводы, Форма — добавить или вырезать площадь, "
        "Вывод — имя площадки (V+, GND, IN, OUT, AC1, AC2). Напечатайте на бумаге — получится чертёж платы. "
        "Не знаете, с чего начать? Скрафтите готовый чертёж (бумага + синий краситель + стабилитрон, NPN-транзистор или конденсатор)."),
    "jei.omnitech.info.pcb_fabricator": (
        "Mass-produces etched boards from a PCB Blueprint (the photomask, never used up). Needs power, copper plates "
        "and photoresist — resin clumps dissolved in acetone in the Solvation Machine. Bigger boards cost more.",
        "Серийно изготавливает платы по чертежу (фотошаблон не расходуется). Нужны энергия, медные пластины "
        "и фоторезист — смола, растворённая в ацетоне в машине растворения. Большие платы дороже."),
    "jei.omnitech.info.soldering_station": (
        "Place parts on the pads of an etched board (R rotates), then press Test to simulate it on every test bench, "
        "and Solder to build it. Boards from ready-made blueprints come with their parts already placed — just "
        "load the parts and tin wire and press Solder.",
        "Расставьте детали на площадки платы (R — поворот), нажмите «Тест» для моделирования на всех стендах "
        "и «Паять» для сборки. Платы по готовым чертежам приходят с уже расставленными деталями — просто "
        "положите детали и оловянную проволоку и нажмите «Паять»."),
    "jei.omnitech.info.pcb_blueprint": (
        "A printed circuit drawing. Put it in the PCB Fabricator as a photomask, or in the PCB Workbench to edit or copy it.",
        "Чертёж печатной платы. Вставьте в фабрикатор как фотошаблон или в верстак, чтобы изменить или скопировать."),

    "item.omnitech.resistor": ("Resistor", "Резистор"),
    "item.omnitech.resistor.blank": ("Blank Resistor", "Резистор без маркировки"),
    "item.omnitech.resistor.value": ("Resistor %s", "Резистор %s"),
    "tooltip.omnitech.resistor.blank": ("Paint the colour bands in a crafting grid: the resistor plus 3–6 dyes (gold / silver nugget for metallic bands), read left to right",
                                        "Нанесите цветные полосы в сетке крафта: резистор и 3–6 красителей (золотой / серебряный самородок для металлических полос), слева направо"),
    "tooltip.omnitech.resistor.carbon_film": ("Carbon film", "Углеродная плёнка"),
    "tooltip.omnitech.resistor.metal_film": ("Metal film", "Металлоплёночный"),
    "tooltip.omnitech.resistor.tempco": ("%s ppm/K", "%s ppm/K"),

    "tooltip.omnitech.pcb.board": ("%s×%s board, %s pads", "Плата %s×%s, площадок: %s"),
    "tooltip.omnitech.pcb.parts": ("%s components soldered", "Припаяно деталей: %s"),
    "tooltip.omnitech.pcb.terminals": ("Terminals: %s", "Выводы: %s"),
}


def build_lang():
    entries = dict(LANG)
    for key, (en, ru) in BAND_NAMES.items():
        entries["color.omnitech.band." + key] = (en, ru)
    for iid, (_tex, en, ru, _js) in ITEMS.items():
        entries["item.omnitech." + iid] = (en, ru)
    for iid, (_tex, en, ru) in JAVA_ITEMS.items():
        entries["item.omnitech." + iid] = (en, ru)
    for bid, (en, ru, _tex) in CASINGS.items():
        entries["block.omnitech." + bid] = (en, ru)
    for bid, (en, ru, _tex, _tool) in STATIONS.items():
        entries["block.omnitech." + bid] = (en, ru)
    for idx, code in ((0, "en_us"), (1, "ru_ru")):
        path = os.path.join(ASSETS, "lang", code + ".json")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        lang = json.loads(text)
        new = [(k, v[idx]) for k, v in entries.items() if k not in lang]
        if new:
            # Append as a new block before the closing brace so hand-made grouping survives
            body = text.rstrip()
            assert body.endswith("}")
            body = body[:-1].rstrip()
            lines = ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}"
                               for k, v in new)
            text = body + ",\n\n" + lines + "\n}\n"
            json.loads(text)  # sanity
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
        print(f"  lang {code}: +{len(new)}")


if __name__ == "__main__":
    print("Items / blocks / fluid")
    build_items()
    build_blocks()
    build_photoresist()
    build_resistor()
    build_tags_and_tabs()
    print("Recipes")
    build_recipes()
    build_blueprints()
    build_machine_loot()
    print("Lang")
    build_lang()
