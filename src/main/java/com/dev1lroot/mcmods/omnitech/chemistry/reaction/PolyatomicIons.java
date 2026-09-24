/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dev1lroot.mcmods.omnitech.chemistry.reaction.PolyatomicIon.GasProduct.*;

/** The standard set of named polyatomic ions used to recognize salts/acids from a bare formula. */
public final class PolyatomicIons {

    private PolyatomicIons() {}

    public static final List<PolyatomicIon> ANIONS = List.of(
            PolyatomicIon.of("nitrate", "NO3", -1),
            PolyatomicIon.of("nitrite", "NO2", -1),
            PolyatomicIon.of("sulfate", "SO4", -2),
            PolyatomicIon.of("bisulfate", "HSO4", -1),
            PolyatomicIon.of("sulfite", "SO3", -2, SO2),
            PolyatomicIon.of("bisulfite", "HSO3", -1, SO2),
            PolyatomicIon.of("thiosulfate", "S2O3", -2),
            PolyatomicIon.of("carbonate", "CO3", -2, CO2),
            PolyatomicIon.of("bicarbonate", "HCO3", -1, CO2),
            PolyatomicIon.of("phosphate", "PO4", -3),
            PolyatomicIon.of("hydroxide", "OH", -1),
            PolyatomicIon.of("cyanide", "CN", -1, HCN),
            PolyatomicIon.of("acetate", "C2H3O2", -1),
            PolyatomicIon.of("oxalate", "C2O4", -2),
            PolyatomicIon.of("hypochlorite", "ClO", -1),
            PolyatomicIon.of("chlorite", "ClO2", -1),
            PolyatomicIon.of("chlorate", "ClO3", -1),
            PolyatomicIon.of("perchlorate", "ClO4", -1),
            PolyatomicIon.of("permanganate", "MnO4", -1),
            PolyatomicIon.of("chromate", "CrO4", -2),
            PolyatomicIon.of("dichromate", "Cr2O7", -2),
            PolyatomicIon.of("silicate", "SiO3", -2)
    );

    public static final List<PolyatomicIon> CATIONS = List.of(
            PolyatomicIon.of("ammonium", "NH4", 1, NH3),
            PolyatomicIon.of("hydronium", "H3O", 1)
    );

    /** Finds an ion whose atoms, scaled by some positive integer, exactly account for {@code remaining}. */
    public static Optional<Match> match(Formula remaining, List<PolyatomicIon> candidates) {
        if (remaining.totalAtoms() == 0) return Optional.empty();
        for (PolyatomicIon ion : candidates) {
            Integer k = ratio(remaining, ion.atoms());
            if (k != null && k > 0) return Optional.of(new Match(ion, k));
        }
        return Optional.empty();
    }

    /** If {@code whole} is exactly {@code unit} scaled by a positive integer, returns that integer. */
    private static Integer ratio(Formula whole, Formula unit) {
        if (!whole.elements().equals(unit.elements())) return null;
        Integer k = null;
        for (Map.Entry<String, Integer> e : unit.counts().entrySet()) {
            int wc = whole.count(e.getKey());
            int uc = e.getValue();
            if (uc == 0 || wc % uc != 0) return null;
            int candidate = wc / uc;
            if (k == null) k = candidate;
            else if (!k.equals(candidate)) return null;
        }
        return k;
    }

    public record Match(PolyatomicIon ion, int multiplicity) {}
}
