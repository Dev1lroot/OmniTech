/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional SMILES structural code for a pre-registered, statically-named fluid (ethanol,
 * acetone, …), populated by {@link FluidLoader} from an optional {@code "smiles"} field in its
 * {@code data/omnitech/fluid/<name>.json}. Purely additive: a fluid without one is simply absent
 * here, and its tooltip/name are unaffected.
 *
 * <p>This is separate from the generic {@code omnitech:chemical_compound} fluid, whose SMILES is
 * carried per-stack via {@link OmniTechDataComponents#SMILES} instead — see
 * {@link com.dev1lroot.mcmods.omnitech.util.ChemistryTooltipUtil}, which both paths share.
 */
public final class FluidChemistryRegistry {

    private FluidChemistryRegistry() {}

    // Keyed by full "namespace:path", same convention as FluidPhysicsRegistry.
    private static final Map<String, String> SMILES_BY_ID = new HashMap<>();

    /** Called by {@link FluidLoader} for OmniTech fluids (registers source + flowing). */
    static void register(String name, String smiles) {
        SMILES_BY_ID.put(OmniTech.MODID + ":" + name,         smiles);
        SMILES_BY_ID.put(OmniTech.MODID + ":flowing_" + name, smiles);
    }

    /** The SMILES registered for {@code fluid}, or {@code null} if it has none. */
    @Nullable
    public static String get(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        return key == null ? null : SMILES_BY_ID.get(key.toString());
    }
}
