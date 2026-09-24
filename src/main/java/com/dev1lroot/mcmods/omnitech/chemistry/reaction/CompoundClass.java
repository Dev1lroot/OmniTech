/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

/** The result of classifying a {@link Formula} into a real inorganic-chemistry role. */
public record CompoundClass(
        Formula formula,
        Type type,
        String metal,
        int cationCharge,
        PolyatomicIon anion,
        String simpleAnionElement,
        int anionCharge,
        boolean strongAcid
) {
    public enum Type {
        ELEMENT_METAL, ELEMENT_NONMETAL, WATER, BINARY_ACID, OXOACID, WEAK_BASE_MOLECULAR,
        HYDROXIDE_BASE, OXIDE_BASIC, OXIDE_ACIDIC, OXIDE_AMPHOTERIC, SALT, ORGANIC, UNKNOWN
    }

    public boolean isAcid() {
        return type == Type.BINARY_ACID || type == Type.OXOACID;
    }

    public boolean isBase() {
        return type == Type.HYDROXIDE_BASE || type == Type.OXIDE_BASIC || type == Type.WEAK_BASE_MOLECULAR;
    }

    /** True for a salt whose anion is the conjugate base of a weak/volatile acid (fizzes with acid). */
    public boolean releasesGasWithAcid() {
        return type == Type.SALT && anion != null && anion.gasOnProtonation() != PolyatomicIon.GasProduct.NONE;
    }

    /** Net anion charge contributed by this compound's anion group (0 if not applicable). */
    public int anionTotalCharge() {
        return anionCharge;
    }

    public String anionName() {
        if (anion != null) return anion.name();
        if (simpleAnionElement != null) return simpleAnionElement;
        return null;
    }

    public Formula anionAtoms() {
        if (anion != null) return anion.atoms();
        if (simpleAnionElement != null) return Formula.of(java.util.Map.of(simpleAnionElement, 1));
        return null;
    }
}
