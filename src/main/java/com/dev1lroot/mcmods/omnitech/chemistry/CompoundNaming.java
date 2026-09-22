/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

/**
 * Pure (common-side, no client dependency) SMILES → {@link Molecule} / IUPAC-name helpers,
 * shared by everything that needs to turn a stored SMILES string into a name without dragging in
 * client-only rendering code — notably {@link com.dev1lroot.mcmods.omnitech.ChemicalCompoundFluidType},
 * whose {@code getDescription} can run on the logical server. Client-side tooltip rendering
 * (structure diagrams, "hold shift" hints) lives separately in
 * {@link com.dev1lroot.mcmods.omnitech.util.ChemistryTooltipUtil}, which delegates here.
 */
public final class CompoundNaming {

    private CompoundNaming() {}

    /** Parses {@code smiles} into a {@link Molecule}, or {@link Molecule#EMPTY} if blank/unparseable. */
    public static Molecule parse(String smiles) {
        if (smiles == null || smiles.isBlank()) return Molecule.EMPTY;
        try {
            return SmilesParser.parse(smiles);
        } catch (SmilesParser.ParseException e) {
            return Molecule.EMPTY;
        }
    }

    /** Live IUPAC-style name derived from {@code smiles} ("Unknown Compound" if blank/unparseable). */
    public static String nameOf(String smiles) {
        Molecule m = parse(smiles);
        return m.isEmpty() ? "Unknown Compound" : IupacNamer.name(m);
    }
}
