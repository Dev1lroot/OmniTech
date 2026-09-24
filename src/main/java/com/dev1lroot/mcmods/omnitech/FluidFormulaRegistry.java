/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.chemistry.reaction.Formula;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Chemical-formula string for a pre-registered, statically-named fluid, populated by
 * {@link FluidLoader} from an optional {@code "formula"} field in its
 * {@code data/omnitech/fluid/<name>.json} — the bridge that lets {@link ReactionFlaskItem} feed a
 * flask's actual contents into {@code ReactionEngine}. Separate from
 * {@link FluidChemistryRegistry}'s SMILES (used for naming/tooltips of mostly-organic fluids):
 * this is plain formula notation ({@code "H2SO4"}, {@code "NaOH"}), the format
 * {@code chemistry.reaction.FormulaParser} understands, and it's populated for the inorganic
 * acids/bases/elements the SMILES engine can't represent anyway.
 *
 * <p>Only a starter set of fluids carry a formula so far (see the fluid JSON files) — this is
 * meant to grow the same incremental way {@code ItemChemistryRegistry}'s per-item data did.
 */
public final class FluidFormulaRegistry {

    private FluidFormulaRegistry() {}

    private static final Map<String, String> FORMULA_BY_ID = new HashMap<>();
    /** Reverse index for turning a computed reaction product back into a placeable fluid. First registration for a given canonical formula wins. */
    private static final Map<String, String> ID_BY_CANONICAL_FORMULA = new HashMap<>();

    static void register(String name, String formula) {
        String id = OmniTech.MODID + ":" + name;
        FORMULA_BY_ID.put(id, formula);
        FORMULA_BY_ID.put(OmniTech.MODID + ":flowing_" + name, formula);
        try {
            ID_BY_CANONICAL_FORMULA.putIfAbsent(Formula.of(formula).canonical(), id);
        } catch (RuntimeException ignored) {
            // Malformed "formula" field in the JSON — leave the reverse lookup unaffected.
        }
    }

    /** The formula registered for {@code fluid} ("H2SO4", "NaOH", ...), or {@code null} if it has none. */
    @Nullable
    public static String get(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        return key == null ? null : FORMULA_BY_ID.get(key.toString());
    }

    /** The fluid registered as {@code canonicalFormula} (e.g. {@code Formula.of("H2O").canonical()}), if any. */
    @Nullable
    public static Fluid getFluidByFormula(String canonicalFormula) {
        String id = ID_BY_CANONICAL_FORMULA.get(canonicalFormula);
        if (id == null) return null;
        var key = net.minecraft.resources.Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.FLUID.getOptional(key).orElse(null);
    }
}
