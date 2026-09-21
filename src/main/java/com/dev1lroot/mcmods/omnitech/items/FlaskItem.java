/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.FluidHazardUtil;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FermenterBlockEntity;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.function.Consumer;

/**
 * A small glass lab flask that carries up to {@value #CAPACITY} mB, split across one or more
 * fluids at once — a {@link Solution}. Filling is one-way through the Fluid Filler's "Inject"
 * action (see {@code FluidFillerBlockEntity#tryInjectFlask}) or a filled {@link PipetteItem}
 * combined with the flask on a crafting table; there's currently no way to drain a flask
 * directly back into a tank.
 *
 * <p>Unlike {@link BucketItem}, a flask is meant for chemistry — acids and collected
 * gas samples are exactly what lab glassware is for, so {@link #canHold} only rejects
 * what glass genuinely can't survive: anything only liquid at temperatures far above
 * ambient (molten metals) or below it (a fluid that's frozen solid at 20 °C).
 */
public class FlaskItem extends Item {

    public static final int CAPACITY = 500;

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

    // ── Solution access helpers ──────────────────────────────────────────────

    public static Solution getSolution(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.SOLUTION.get(), Solution.EMPTY);
    }

    public static void setSolution(ItemStack stack, Solution solution) {
        if (solution == null || solution.isEmpty()) {
            stack.remove(OmniTechDataComponents.SOLUTION.get());
        } else {
            stack.set(OmniTechDataComponents.SOLUTION.get(), solution);
        }
    }

    public static int getTotalAmount(ItemStack stack) { return getSolution(stack).totalAmount(); }
    public static boolean isEmpty(ItemStack stack)     { return getSolution(stack).isEmpty(); }
    public static int getRemainingCapacity(ItemStack stack) {
        return Math.max(0, CAPACITY - getTotalAmount(stack));
    }

    /**
     * Adds up to {@code amount} mB of {@code fluid} to the solution, clamped to whatever
     * space remains and rejected outright if {@link #canHold} disallows the fluid.
     * Returns the amount actually added (0 if nothing could be added).
     */
    public static int addFluid(ItemStack stack, Fluid fluid, int amount) {
        if (fluid == null || fluid.isSame(Fluids.EMPTY) || amount <= 0 || !canHold(fluid)) return 0;
        int space = getRemainingCapacity(stack);
        int added = Math.min(amount, space);
        if (added <= 0) return 0;
        setSolution(stack, getSolution(stack).plus(fluid, added));
        return added;
    }

    /**
     * Fills an empty flask with a proportional sample of the fermenter's mixture — as much as the
     * flask holds ({@value #CAPACITY} mB), or all of it if the vessel has less. Returns true if
     * anything was transferred.
     */
    public static boolean tryFillFromFermenter(ItemStack stack, FermenterBlockEntity fermenter) {
        if (!isEmpty(stack)) return false;
        Solution sample = fermenter.drawSample(CAPACITY);
        if (sample.isEmpty()) return false;
        setSolution(stack, sample);
        return true;
    }

    // ── Fill-level bar ─────────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) { return getTotalAmount(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getTotalAmount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x2266FF; }

    // ── Display name ─────────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        Solution solution = getSolution(stack);
        if (solution.isEmpty()) return Component.literal("Flask");
        if (solution.components().size() == 1) {
            ResourceStack<FluidResource> only = solution.components().get(0);
            return only.resource().toStack(only.amount()).getHoverName().copy()
                    .append(Component.literal(" Flask"));
        }
        ResourceStack<FluidResource> dom = solution.dominant();
        return dom.resource().toStack(dom.amount()).getHoverName().copy()
                .append(Component.literal(" Solution Flask"));
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Solution solution = getSolution(stack);
        if (solution.isEmpty()) {
            tooltip.accept(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int total = solution.totalAmount();
        tooltip.accept(Component.literal("Solution: " + total + " / " + CAPACITY + " mB")
                .withStyle(ChatFormatting.GRAY));

        for (ResourceStack<FluidResource> c : solution.components()) {
            FluidResource res = c.resource();
            int amount = c.amount();
            int pct = total > 0 ? Math.round(100f * amount / total) : 0;
            var fluidStack = res.toStack(amount);
            tooltip.accept(fluidStack.getHoverName().copy()
                    .append(Component.literal(": " + amount + " mB (" + pct + "%)"))
                    .withStyle(ChatFormatting.WHITE));
            FluidHazardUtil.appendHazardTooltip(fluidStack, tooltip);
        }
    }
}
