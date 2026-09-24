/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.dev1lroot.mcmods.omnitech.chemistry.reaction.CompoundClass.Type.*;

/**
 * Turns a bare {@link Formula} into a {@link CompoundClass} — acid, base, salt, oxide, or element —
 * using only the formula's own atom counts plus the universal, formula-independent facts in
 * {@link Element} and {@link PolyatomicIons}. This is the piece that makes reactions "generic": no
 * per-compound authoring is consulted here, only real inorganic-chemistry classification rules.
 *
 * <p>Known limitation: organic acids/bases (a -COOH or -NH2 group) can't be told apart from a
 * bare molecular formula alone — e.g. acetic acid (C2H4O2) is indistinguishable from a random
 * C2H4O2 ester by formula. Anything containing carbon with no metal present and no recognized
 * mineral-acid pattern is classified {@link CompoundClass.Type#ORGANIC} and only participates in
 * combustion, never acid/base chemistry, until a structural (SMILES) classifier is added.
 */
public final class CompoundClassifier {

    private CompoundClassifier() {}

    private static final Formula WATER_FORMULA = Formula.of("H2O");
    private static final Formula AMMONIA = Formula.of("NH3");
    private static final Set<String> STRONG_ACIDS = Set.of("HCl", "HBr", "HI", "HNO3", "H2SO4", "HClO4", "HClO3");
    /** Elements that actually form a real binary (hydro-) acid with hydrogen — not C, N, or P. */
    private static final Set<String> BINARY_ACID_ELEMENTS = Set.of("F", "Cl", "Br", "I", "S", "Se", "Te");

    public static CompoundClass classify(Formula f) {
        if (f.equals(WATER_FORMULA)) return simple(f, WATER);
        if (f.isSingleElement()) {
            String sym = f.soleElement();
            return Element.isMetal(sym)
                    ? new CompoundClass(f, ELEMENT_METAL, sym, 0, null, null, 0, false)
                    : simple(f, ELEMENT_NONMETAL);
        }

        String metal = soleMetal(f);
        if (metal != null) return classifyMetalCompound(f, metal);

        return classifyNoMetalCompound(f);
    }

    private static CompoundClass simple(Formula f, CompoundClass.Type type) {
        return new CompoundClass(f, type, null, 0, null, null, 0, false);
    }

    /** Returns the formula's single metal element, or null if it has none or more than one. */
    private static String soleMetal(Formula f) {
        String found = null;
        for (String e : f.elements()) {
            if (Element.isMetal(e)) {
                if (found != null) return null; // double-metal compound: out of scope
                found = e;
            }
        }
        return found;
    }

    private static CompoundClass classifyMetalCompound(Formula f, String metal) {
        int metalCount = f.count(metal);
        Map<String, Integer> remainingMap = new LinkedHashMap<>(f.counts());
        remainingMap.remove(metal);
        Formula remaining = Formula.of(remainingMap);

        if (remaining.elements().equals(Set.of("O"))) {
            int oCount = remaining.count("O");
            Integer charge = divideEvenly(2 * oCount, metalCount);
            if (charge == null) return simple(f, UNKNOWN);
            CompoundClass.Type oxideType = Element.AMPHOTERIC.contains(metal) ? OXIDE_AMPHOTERIC : OXIDE_BASIC;
            return new CompoundClass(f, oxideType, metal, charge, null, "O", -2, false);
        }

        Optional<PolyatomicIons.Match> hydroxide = PolyatomicIons.match(remaining,
                java.util.List.of(PolyatomicIons.ANIONS.stream()
                        .filter(i -> i.name().equals("hydroxide")).findFirst().orElseThrow()));
        if (hydroxide.isPresent()) {
            int n = hydroxide.get().multiplicity();
            Integer charge = divideEvenly(n, metalCount);
            if (charge == null) return simple(f, UNKNOWN);
            return new CompoundClass(f, HYDROXIDE_BASE, metal, charge, hydroxide.get().ion(), null, -1, false);
        }

        Optional<PolyatomicIons.Match> anionMatch = PolyatomicIons.match(remaining, PolyatomicIons.ANIONS);
        if (anionMatch.isPresent()) {
            PolyatomicIon ion = anionMatch.get().ion();
            int k = anionMatch.get().multiplicity();
            Integer charge = divideEvenly(k * Math.abs(ion.charge()), metalCount);
            if (charge == null) return simple(f, UNKNOWN);
            return new CompoundClass(f, SALT, metal, charge, ion, null, ion.charge(), false);
        }

        if (remaining.isSingleElement()) {
            String anionElement = remaining.soleElement();
            int n = remaining.count(anionElement);
            int anionCharge = Element.defaultAnionCharge(anionElement);
            if (anionCharge == 0) return simple(f, UNKNOWN);
            Integer charge = divideEvenly(n * Math.abs(anionCharge), metalCount);
            if (charge == null) return simple(f, UNKNOWN);
            return new CompoundClass(f, SALT, metal, charge, null, anionElement, anionCharge, false);
        }

        return simple(f, UNKNOWN);
    }

    private static CompoundClass classifyNoMetalCompound(Formula f) {
        if (f.equals(AMMONIA)) return simple(f, WEAK_BASE_MOLECULAR);

        if (f.contains("H")) {
            Map<String, Integer> withoutH = new LinkedHashMap<>(f.counts());
            int hCount = withoutH.remove("H");
            Formula remainder = Formula.of(withoutH);

            if (remainder.isSingleElement() && BINARY_ACID_ELEMENTS.contains(remainder.soleElement())) {
                String elem = remainder.soleElement();
                int n = remainder.count(elem);
                int anionCharge = Element.defaultAnionCharge(elem);
                if (hCount == n * Math.abs(anionCharge)) {
                    return new CompoundClass(f, BINARY_ACID, null, 0, null, elem, anionCharge,
                            STRONG_ACIDS.contains(f.canonical()));
                }
            }

            Optional<PolyatomicIons.Match> anionMatch = PolyatomicIons.match(remainder, PolyatomicIons.ANIONS);
            if (anionMatch.isPresent()) {
                PolyatomicIon ion = anionMatch.get().ion();
                int k = anionMatch.get().multiplicity();
                if (hCount == k * Math.abs(ion.charge())) {
                    return new CompoundClass(f, OXOACID, null, 0, ion, null, ion.charge(),
                            STRONG_ACIDS.contains(f.canonical()));
                }
            }
        }

        if (f.contains("C") && f.contains("H")) return simple(f, ORGANIC);

        if (!f.contains("H") && f.contains("O")) {
            // Every remaining element besides O is a nonmetal here (no-metal branch) -> acidic oxide.
            return simple(f, OXIDE_ACIDIC);
        }

        return simple(f, UNKNOWN);
    }

    private static Integer divideEvenly(int numerator, int denominator) {
        if (denominator == 0 || numerator % denominator != 0) return null;
        return numerator / denominator;
    }
}
