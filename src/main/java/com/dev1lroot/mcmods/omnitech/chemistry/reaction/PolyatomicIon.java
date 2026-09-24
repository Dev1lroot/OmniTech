/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

/**
 * A named polyatomic ion: its atoms (as they appear in a neutral compound's formula), charge, and
 * whether attacking it with an acid liberates a gas (carbonate → CO2, sulfite → SO2, ...) — the
 * mechanism behind "acid fizzes on limestone."
 */
public record PolyatomicIon(String name, Formula atoms, int charge, GasProduct gasOnProtonation) {

    public enum GasProduct { NONE, CO2, SO2, H2S, HCN, NH3 }

    public static PolyatomicIon of(String name, String formula, int charge) {
        return new PolyatomicIon(name, Formula.of(formula), charge, GasProduct.NONE);
    }

    public static PolyatomicIon of(String name, String formula, int charge, GasProduct gas) {
        return new PolyatomicIon(name, Formula.of(formula), charge, gas);
    }
}
