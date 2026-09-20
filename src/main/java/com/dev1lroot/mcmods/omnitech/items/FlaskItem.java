/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.FluidHazardUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.function.Consumer;

/**
 * A small glass lab flask that carries 0–{@value #CAPACITY} mB of a single fluid.
 *
 * <p>Unlike {@link BucketItem}, a flask is meant for chemistry — acids and collected
 * gas samples are exactly what lab glassware is for, so {@link #canHold} only rejects
 * what glass genuinely can't survive: anything only liquid at temperatures far above
 * ambient (molten metals) or below it (a fluid that's frozen solid at 20 °C).
 *
 * <p>Mirrors {@link FluidCanisterItem}'s data-component storage; see that class for the
 * storage/model/tooltip rationale.
 */
public class FlaskItem extends Item {

    public static final int CAPACITY = 250;

    public FlaskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Returns true unless this fluid is solid (or plasma) at 20 °C / 101 kPa. */
    public static boolean canHold(Fluid fluid) {
        if (fluid == null || fluid.isSame(Fluids.EMPTY)) return true;

        var physics = FluidPhysicsRegistry.get(fluid);
        var diagram = physics.phaseDiagram();
        if (diagram == null) return true; // no data — assume it's fine

        FluidPhase phase = FluidPhaseUtil.getPhase(20, 101, diagram);
        return phase == FluidPhase.LIQUID || phase == FluidPhase.GAS || phase == FluidPhase.VAPOUR;
    }

    // ── Fluid access helpers ──────────────────────────────────────────────────

    public static FluidStack getFluid(ItemStack stack) {
        ResourceStack<FluidResource> stored = stack.get(OmniTechDataComponents.FLUID_CONTENTS.get());
        if (stored == null || stored.isEmpty()) return FluidStack.EMPTY;
        return stored.resource().toStack(stored.amount());
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            stack.remove(OmniTechDataComponents.FLUID_CONTENTS.get());
        } else {
            stack.set(OmniTechDataComponents.FLUID_CONTENTS.get(),
                    new ResourceStack<>(FluidResource.of(fluid), fluid.getAmount()));
        }
    }

    public static int getAmount(ItemStack stack)  { return getFluid(stack).getAmount(); }
    public static boolean isEmpty(ItemStack stack) { return getFluid(stack).isEmpty(); }

    // ── Fill-level bar ─────────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) { return getAmount(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getAmount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x2266FF; }

    // ── Display name ─────────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) return Component.literal("Flask");
        return fluid.getHoverName().copy().append(Component.literal(" Flask"));
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            tooltip.accept(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.accept(Component.literal(fluid.getAmount() + " / " + CAPACITY + " mB")
                    .withStyle(ChatFormatting.GRAY));
            FluidHazardUtil.appendHazardTooltip(fluid, tooltip);
        }
    }
}
