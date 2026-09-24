/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds a neutral compound's formula from a cation and an anion by cross-multiplying charges. */
public final class IonicFormula {

    private IonicFormula() {}

    public static Formula combine(String cationElement, int cationCharge, Formula anionAtoms, int anionCharge) {
        return combine(Formula.of(Map.of(cationElement, 1)), cationCharge, anionAtoms, anionCharge);
    }

    public static Formula combine(Formula cationAtoms, int cationCharge, Formula anionAtoms, int anionCharge) {
        int cAbs = Math.abs(cationCharge);
        int aAbs = Math.abs(anionCharge);
        if (cAbs == 0 || aAbs == 0) throw new IllegalArgumentException("Charge cannot be zero");
        int g = gcd(cAbs, aAbs);
        int cationCount = aAbs / g;
        int anionCount = cAbs / g;
        Map<String, Integer> result = new LinkedHashMap<>();
        cationAtoms.scaledBy(cationCount).counts().forEach((k, v) -> result.merge(k, v, Integer::sum));
        anionAtoms.scaledBy(anionCount).counts().forEach((k, v) -> result.merge(k, v, Integer::sum));
        return Formula.of(result);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
