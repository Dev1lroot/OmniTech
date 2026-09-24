/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.Map;
import java.util.Set;

/**
 * Periodic-table-derived reactivity data. Nothing here is per-compound authoring — it is derived
 * purely from an element's position in the table (atomic number, group/block), the same way a
 * chemist predicts an unfamiliar element's behavior from where it sits on the table. A hand-picked
 * {@link #ACTIVITY_SERIES} covers the ~30 elements with well-known textbook reactivity (real
 * standard-electrode-potential order); every other element falls back to a category-based estimate
 * from {@link #reactivityScore}, since exact electrode potentials for e.g. transactinides don't
 * meaningfully exist. Precision intentionally degrades gracefully rather than requiring an entry
 * per element.
 */
public final class Element {

    private Element() {}

    public static final Map<String, Integer> ATOMIC_NUMBER = Map.ofEntries(
            Map.entry("H", 1), Map.entry("He", 2), Map.entry("Li", 3), Map.entry("Be", 4), Map.entry("B", 5),
            Map.entry("C", 6), Map.entry("N", 7), Map.entry("O", 8), Map.entry("F", 9), Map.entry("Ne", 10),
            Map.entry("Na", 11), Map.entry("Mg", 12), Map.entry("Al", 13), Map.entry("Si", 14), Map.entry("P", 15),
            Map.entry("S", 16), Map.entry("Cl", 17), Map.entry("Ar", 18), Map.entry("K", 19), Map.entry("Ca", 20),
            Map.entry("Sc", 21), Map.entry("Ti", 22), Map.entry("V", 23), Map.entry("Cr", 24), Map.entry("Mn", 25),
            Map.entry("Fe", 26), Map.entry("Co", 27), Map.entry("Ni", 28), Map.entry("Cu", 29), Map.entry("Zn", 30),
            Map.entry("Ga", 31), Map.entry("Ge", 32), Map.entry("As", 33), Map.entry("Se", 34), Map.entry("Br", 35),
            Map.entry("Kr", 36), Map.entry("Rb", 37), Map.entry("Sr", 38), Map.entry("Y", 39), Map.entry("Zr", 40),
            Map.entry("Nb", 41), Map.entry("Mo", 42), Map.entry("Tc", 43), Map.entry("Ru", 44), Map.entry("Rh", 45),
            Map.entry("Pd", 46), Map.entry("Ag", 47), Map.entry("Cd", 48), Map.entry("In", 49), Map.entry("Sn", 50),
            Map.entry("Sb", 51), Map.entry("Te", 52), Map.entry("I", 53), Map.entry("Xe", 54), Map.entry("Cs", 55),
            Map.entry("Ba", 56), Map.entry("La", 57), Map.entry("Ce", 58), Map.entry("Pr", 59), Map.entry("Nd", 60),
            Map.entry("Pm", 61), Map.entry("Sm", 62), Map.entry("Eu", 63), Map.entry("Gd", 64), Map.entry("Tb", 65),
            Map.entry("Dy", 66), Map.entry("Ho", 67), Map.entry("Er", 68), Map.entry("Tm", 69), Map.entry("Yb", 70),
            Map.entry("Lu", 71), Map.entry("Hf", 72), Map.entry("Ta", 73), Map.entry("W", 74), Map.entry("Re", 75),
            Map.entry("Os", 76), Map.entry("Ir", 77), Map.entry("Pt", 78), Map.entry("Au", 79), Map.entry("Hg", 80),
            Map.entry("Tl", 81), Map.entry("Pb", 82), Map.entry("Bi", 83), Map.entry("Po", 84), Map.entry("At", 85),
            Map.entry("Rn", 86), Map.entry("Fr", 87), Map.entry("Ra", 88), Map.entry("Ac", 89), Map.entry("Th", 90),
            Map.entry("Pa", 91), Map.entry("U", 92), Map.entry("Np", 93), Map.entry("Pu", 94), Map.entry("Am", 95),
            Map.entry("Cm", 96), Map.entry("Bk", 97), Map.entry("Cf", 98), Map.entry("Es", 99), Map.entry("Fm", 100),
            Map.entry("Md", 101), Map.entry("No", 102), Map.entry("Lr", 103), Map.entry("Rf", 104), Map.entry("Db", 105),
            Map.entry("Sg", 106), Map.entry("Bh", 107), Map.entry("Hs", 108), Map.entry("Mt", 109), Map.entry("Ds", 110),
            Map.entry("Rg", 111), Map.entry("Cn", 112), Map.entry("Nh", 113), Map.entry("Fl", 114), Map.entry("Mc", 115),
            Map.entry("Lv", 116), Map.entry("Ts", 117), Map.entry("Og", 118)
    );

    private static final Set<String> NOBLE_GASES = Set.of("He", "Ne", "Ar", "Kr", "Xe", "Rn", "Og");
    private static final Set<String> HALOGENS = Set.of("F", "Cl", "Br", "I", "At", "Ts");
    /** Chemically behave as nonmetals (no simple cation) despite metalloid/borderline position. */
    private static final Set<String> METALLOIDS = Set.of("B", "Si", "Ge", "As", "Te");
    private static final Set<String> ALKALI = Set.of("Li", "Na", "K", "Rb", "Cs", "Fr");
    private static final Set<String> ALKALINE_EARTH = Set.of("Be", "Mg", "Ca", "Sr", "Ba", "Ra");
    /** Amphoteric oxides/hydroxides — react with both acids and strong bases. */
    public static final Set<String> AMPHOTERIC = Set.of("Al", "Zn", "Sn", "Pb", "Cr", "Be");
    /** Sit below hydrogen in the activity series — do not react with non-oxidizing acids. */
    private static final Set<String> NOBLE_METALS = Set.of("Cu", "Hg", "Ag", "Pd", "Pt", "Au", "Rh", "Ru", "Os", "Ir");

    /** Hand-picked real activity-series order (higher = more reactive); index into a 1000-scale. */
    private static final String[] ACTIVITY_SERIES = {
            "Cs", "Rb", "K", "Na", "Li", "Ba", "Sr", "Ca", "Mg", "Al", "Mn", "Zn", "Cr", "Fe", "Cd", "Co", "Ni",
            "Sn", "Pb", "H", "Sb", "Bi", "Cu", "Hg", "Ag", "Pd", "Pt", "Au"
    };

    /** Default oxidation state a free metal adopts reacting with a non-oxidizing acid/nonmetal. */
    private static final Map<String, Integer> DEFAULT_CHARGE = Map.ofEntries(
            Map.entry("Fe", 2), Map.entry("Cu", 2), Map.entry("Sn", 2), Map.entry("Pb", 2), Map.entry("Cr", 3),
            Map.entry("Mn", 2), Map.entry("Co", 2), Map.entry("Ni", 2), Map.entry("Hg", 2), Map.entry("Au", 3),
            Map.entry("Sb", 3), Map.entry("Bi", 3), Map.entry("Pt", 2), Map.entry("Ti", 4), Map.entry("V", 3)
    );

    public static boolean isKnown(String symbol) {
        return ATOMIC_NUMBER.containsKey(symbol);
    }

    public static int atomicNumber(String symbol) {
        return ATOMIC_NUMBER.getOrDefault(symbol, -1);
    }

    public static boolean isNobleGas(String symbol) {
        return NOBLE_GASES.contains(symbol);
    }

    public static boolean isMetal(String symbol) {
        if (!isKnown(symbol)) return false;
        if (symbol.equals("H") || NOBLE_GASES.contains(symbol) || HALOGENS.contains(symbol)
                || METALLOIDS.contains(symbol)) {
            return false;
        }
        int z = atomicNumber(symbol);
        // Nonmetals/metalloids not already covered above: C, N, O, P, S, Se.
        return !Set.of("C", "N", "O", "P", "S", "Se").contains(symbol) && z > 0;
    }

    /** Higher = more reactive. Only meaningful for metals and "H" (the acid-hydrogen reference point). */
    public static int reactivityScore(String symbol) {
        for (int i = 0; i < ACTIVITY_SERIES.length; i++) {
            if (ACTIVITY_SERIES[i].equals(symbol)) return 1000 - i * 10;
        }
        if (!isMetal(symbol)) return Integer.MIN_VALUE;
        if (NOBLE_METALS.contains(symbol)) return 735; // alongside Pd/Pt/Au band
        if (ALKALI.contains(symbol)) return 1000 + (atomicNumber(symbol) - atomicNumber("Cs"));
        if (ALKALINE_EARTH.contains(symbol)) return symbol.equals("Be") ? 905 : 950 + (atomicNumber(symbol) - atomicNumber("Ba"));
        int z = atomicNumber(symbol);
        boolean lanthanideOrActinide = (z >= 57 && z <= 71) || (z >= 89 && z <= 103);
        if (lanthanideOrActinide) return 905; // rare earths: broadly as reactive as Al/Mn, tarnish readily
        boolean postTransition = Set.of("Ga", "In", "Tl", "Po").contains(symbol);
        if (postTransition) return 825; // near Sn/Pb
        // Generic d-block (Sc-Zn/Y-Cd/Hf-Hg/Rf-Cn rows) not explicitly listed: moderate reactivity.
        return 845;
    }

    public static boolean isMoreReactive(String a, String b) {
        return reactivityScore(a) > reactivityScore(b);
    }

    /** Typical oxidation state a free metal adopts forming a new ionic compound. */
    public static int defaultCationCharge(String symbol) {
        Integer explicit = DEFAULT_CHARGE.get(symbol);
        if (explicit != null) return explicit;
        if (ALKALI.contains(symbol)) return 1;
        if (ALKALINE_EARTH.contains(symbol)) return 2;
        if (symbol.equals("Al")) return 3;
        int z = atomicNumber(symbol);
        if ((z >= 57 && z <= 71) || (z >= 89 && z <= 103)) return 3; // lanthanides/actinides: commonly +3
        if (Set.of("Ga", "In", "Tl").contains(symbol)) return 3;
        return 2; // generic transition-metal default
    }

    /** Typical anion charge for a nonmetal forming a simple binary anion (Cl-, O2-, N3-, hydride H-...). */
    public static int defaultAnionCharge(String symbol) {
        return switch (symbol) {
            case "F", "Cl", "Br", "I", "At", "H" -> -1;
            case "O", "S", "Se" -> -2;
            case "N", "P" -> -3;
            case "C" -> -4;
            default -> 0;
        };
    }
}
