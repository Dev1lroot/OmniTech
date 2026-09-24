/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Everyday names for well-known mixtures, instead of the generic "Solution". Safe on the logical
 * server (no client classes).
 *
 * <p>Water + alcohol is named by strength: {@code "40% Alcohol"}, counting every alcohol by
 * volume. Methanol counts too — it smells and looks just like ethanol, so the name alone won't
 * warn you (the composition tooltip will).
 *
 * <p>Water + sugar + something sour (lemon juice or citric acid) is {@code "Lemonade"}.
 */
public final class MixtureNaming {

    private static final Set<String> WATERS = Set.of("minecraft:water", "omnitech:distilled_water");
    private static final Set<String> ALCOHOLS = Set.of("omnitech:ethanol", "omnitech:methanol");
    private static final Set<String> SOURS = Set.of("omnitech:lemon_juice", "omnitech:citric_acid");
    private static final String SUGAR = "omnitech:sugar";

    private MixtureNaming() {}

    /** A specific name for {@code mixture}, or {@code null} if it's nothing recognisable. */
    public static @Nullable Component nameOf(Solution mixture) {
        if (mixture.totalAmount() <= 0) return null;
        Component alcohol = alcoholName(mixture);
        return alcohol != null ? alcohol : lemonadeName(mixture);
    }

    private static @Nullable Component alcoholName(Solution mixture) {
        int total = mixture.totalAmount();

        int water = 0, alcohol = 0;
        for (Solution.Part part : mixture.components()) {
            String id = BuiltInRegistries.FLUID.getKey(part.fluid()).toString();
            if (!part.dissolved()) return null;
            if (WATERS.contains(id)) water += part.amount();
            else if (ALCOHOLS.contains(id)) alcohol += part.amount();
            else return null;
        }
        if (water == 0 || alcohol == 0) return null;

        int percent = Math.max(1, Math.min(99, Math.round(100f * alcohol / total)));
        return Component.translatable("fluid.omnitech.solution.alcohol", percent);
    }

    private static @Nullable Component lemonadeName(Solution mixture) {
        boolean water = false, sour = false, sugar = false;
        for (Solution.Part part : mixture.components()) {
            String id = BuiltInRegistries.FLUID.getKey(part.fluid()).toString();
            if (!part.dissolved()) return null;
            if (WATERS.contains(id)) water = true;
            else if (SOURS.contains(id)) sour = true;
            else if (SUGAR.equals(id)) sugar = true;
            else return null;
        }
        return water && sour && sugar ? Component.translatable("fluid.omnitech.solution.lemonade") : null;
    }
}
