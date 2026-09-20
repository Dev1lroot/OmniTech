/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;

/**
 * Shared tooltip rendering for the three fluid hazard flags (see
 * {@link FluidPhysicsRegistry.FluidPhysics}), used by every item that can hold or
 * represent a fluid: {@link com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem},
 * {@link com.dev1lroot.mcmods.omnitech.items.FlaskItem}, and
 * {@link OmniTechBucketItem}.
 */
public final class FluidHazardUtil {

    private FluidHazardUtil() {}

    /** Appends one tooltip line per active hazard flag on {@code fluid}. No-op if empty. */
    public static void appendHazardTooltip(FluidStack fluid, Consumer<Component> tooltip) {
        if (fluid.isEmpty()) return;
        var physics = FluidPhysicsRegistry.get(fluid.getFluid());

        if (physics.toxic()) {
            tooltip.accept(Component.literal("Toxic").withStyle(ChatFormatting.DARK_GREEN));
        }
        if (physics.flammable()) {
            tooltip.accept(Component.literal("Flammable").withStyle(ChatFormatting.GOLD));
        }
        int rad = physics.radioactiveLevel();
        if (rad > 0) {
            String numeral = switch (Math.min(rad, 3)) {
                case 1 -> "I";
                case 2 -> "II";
                default -> "III";
            };
            tooltip.accept(Component.literal("Radioactive " + numeral).withStyle(ChatFormatting.GREEN));
        }
    }
}
