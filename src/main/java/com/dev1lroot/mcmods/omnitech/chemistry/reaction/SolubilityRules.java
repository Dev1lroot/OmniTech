/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.Set;

/**
 * The classic general-chemistry solubility-rules table: which ionic compounds dissolve in water
 * versus precipitate out. This is what decides whether a double-displacement reaction actually has
 * a driving force (real chemistry: no net reaction happens between two soluble salts unless one of
 * the new pairings is insoluble, gaseous, or molecular/weak like water).
 */
public final class SolubilityRules {

    private SolubilityRules() {}

    public enum Solubility { SOLUBLE, INSOLUBLE }

    private static final Set<String> ALKALI = Set.of("Li", "Na", "K", "Rb", "Cs", "Fr");
    private static final Set<String> ALWAYS_SOLUBLE_ANIONS =
            Set.of("nitrate", "nitrite", "acetate", "chlorate", "perchlorate", "chlorite",
                    "hypochlorite", "permanganate", "thiosulfate", "bicarbonate", "bisulfate", "bisulfite");
    private static final Set<String> USUALLY_INSOLUBLE_ANIONS =
            Set.of("carbonate", "phosphate", "sulfite", "chromate", "dichromate", "oxalate", "silicate");
    private static final Set<String> HALIDE_INSOLUBLE_CATIONS = Set.of("Ag", "Pb", "Hg");
    private static final Set<String> SULFATE_INSOLUBLE_CATIONS = Set.of("Ba", "Sr", "Pb", "Ca");
    private static final Set<String> ALKALINE_EARTH_HYDROXIDE_SOLUBLE = Set.of("Ba", "Sr", "Ca");
    private static final Set<String> FLUORIDE_INSOLUBLE_CATIONS = Set.of("Ca", "Mg", "Ba", "Sr", "Pb");

    public static Solubility of(CompoundClass cc) {
        String cation = cc.metal();
        if (cation == null) return Solubility.SOLUBLE; // not a salt/hydroxide we can judge — assume no barrier
        if (ALKALI.contains(cation)) return Solubility.SOLUBLE;

        if (cc.type() == CompoundClass.Type.HYDROXIDE_BASE) {
            return ALKALINE_EARTH_HYDROXIDE_SOLUBLE.contains(cation) ? Solubility.SOLUBLE : Solubility.INSOLUBLE;
        }

        PolyatomicIon anion = cc.anion();
        if (anion != null) {
            String name = anion.name();
            if (ALWAYS_SOLUBLE_ANIONS.contains(name)) return Solubility.SOLUBLE;
            if (name.equals("sulfate")) {
                return SULFATE_INSOLUBLE_CATIONS.contains(cation) ? Solubility.INSOLUBLE : Solubility.SOLUBLE;
            }
            if (USUALLY_INSOLUBLE_ANIONS.contains(name)) return Solubility.INSOLUBLE;
            return Solubility.SOLUBLE;
        }

        String simple = cc.simpleAnionElement();
        if (simple != null) {
            return switch (simple) {
                case "Cl", "Br", "I" -> HALIDE_INSOLUBLE_CATIONS.contains(cation) ? Solubility.INSOLUBLE : Solubility.SOLUBLE;
                case "F" -> FLUORIDE_INSOLUBLE_CATIONS.contains(cation) ? Solubility.INSOLUBLE : Solubility.SOLUBLE;
                case "S" -> Solubility.INSOLUBLE; // sulfides: insoluble except alkali/alkaline-earth (alkali handled above)
                default -> Solubility.SOLUBLE;
            };
        }
        return Solubility.SOLUBLE;
    }
}
