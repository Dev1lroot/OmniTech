/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.FluidHazardUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FermenterBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidTankBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small precision dispenser: draws a player-configured amount (1–{@value #MAX_AMOUNT} mB) of
 * a single fluid from a Fluid Tank, then hands it off in exact doses.
 *
 * <ul>
 *   <li>Shift + right-click (in air) opens a screen to set the draw amount.</li>
 *   <li>Right-click an empty pipette on a Fluid Tank to draw that amount from it.</li>
 *   <li>Empty pipette + a flask holding a {@link Solution} on a crafting table extracts a
 *       proportional sample (same fluid ratios, scaled down) into the pipette, and leaves the
 *       flask behind with the rest.</li>
 *   <li>Filled pipette + a flask on a crafting table pours the pipette's solution into the
 *       flask (clamped to whatever room it has), leaving an emptied pipette behind.</li>
 * </ul>
 *
 * <p>Holds the exact same kind of contents as a {@link FlaskItem} — a {@link Solution} — just
 * at dropper scale, so a single component (from the tank) or several (carried over from a
 * flask sample) both work the same way.
 */
public class PipetteItem extends Item {

    /** Total pipette capacity, matching the maximum settable draw amount. */
    public static final int MAX_AMOUNT = 20;
    public static final int DEFAULT_AMOUNT = 10;

    public PipetteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── Solution access helpers ──────────────────────────────────────────────

    public static Solution getSolution(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.SOLUTION.get(), Solution.EMPTY);
    }

    public static void setSolution(ItemStack stack, Solution solution) {
        if (solution == null || solution.isEmpty()) {
            stack.remove(OmniTechDataComponents.SOLUTION.get());
            SolutionFluids.setConditions(stack, SolutionFluids.AMBIENT_TEMP, SolutionFluids.AMBIENT_PRESSURE);
        } else {
            stack.set(OmniTechDataComponents.SOLUTION.get(), solution);
        }
    }

    public static int getTotalAmount(ItemStack stack) { return getSolution(stack).totalAmount(); }
    public static boolean isEmpty(ItemStack stack)     { return getSolution(stack).isEmpty(); }

    public static int getTargetAmount(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.PIPETTE_AMOUNT.get(), DEFAULT_AMOUNT);
    }

    public static void setTargetAmount(ItemStack stack, int amount) {
        stack.set(OmniTechDataComponents.PIPETTE_AMOUNT.get(), Math.clamp(amount, 1, MAX_AMOUNT));
    }

    /**
     * Adds up to {@code amount} mB of {@code fluid} to the pipette, clamped to whatever room
     * is left under {@value #MAX_AMOUNT} and rejected if {@link FlaskItem#canHold} disallows it
     * (a pipette is glass too). Returns the amount actually added.
     */
    public static int addFluid(ItemStack stack, Fluid fluid, int amount) {
        if (fluid == null || amount <= 0 || !FlaskItem.canHold(fluid)) return 0;
        int space = Math.max(0, MAX_AMOUNT - getTotalAmount(stack));
        int added = Math.min(amount, space);
        if (added <= 0) return 0;
        setSolution(stack, getSolution(stack).plus(fluid, added));
        return added;
    }

    /**
     * Draws up to this pipette's configured target amount from {@code tank} directly into it
     * (right-click on a Fluid Tank). Returns true if anything was transferred.
     */
    public static boolean tryFillFromTank(ItemStack stack, FluidTankBlockEntity tank) {
        FluidStack tankFluid = tank.getFluid();
        if (tankFluid.isEmpty() || !isEmpty(stack)) return false;

        for (Solution.Part part : SolutionFluids.toSolution(tankFluid).components()) {
            if (!FlaskItem.canHold(part.fluid())) return false;
        }

        int want = Math.min(getTargetAmount(stack), MAX_AMOUNT);
        int extracted;
        try (var tx = Transaction.openRoot()) {
            extracted = tank.fluidHandler.extract(FluidResource.of(tankFluid), want, tx);
            if (extracted > 0) tx.commit();
        }
        if (extracted <= 0) return false;

        // What actually left the tank: same composition and conditions, `extracted` mB of it.
        FluidStack drawn = tankFluid.copyWithAmount(extracted);
        setSolution(stack, SolutionFluids.toSolution(drawn));
        SolutionFluids.setConditions(stack, SolutionFluids.temperatureOf(drawn), SolutionFluids.pressureOf(drawn));
        return true;
    }

    /**
     * Draws a sample of up to this pipette's target amount from the fermenter's mixture, every
     * component in proportion to its share. Returns true if anything was transferred.
     */
    public static boolean tryFillFromFermenter(ItemStack stack, FermenterBlockEntity fermenter) {
        if (!isEmpty(stack)) return false;
        Solution sample = fermenter.drawSample(Math.min(getTargetAmount(stack), MAX_AMOUNT));
        if (sample.isEmpty()) return false;
        setSolution(stack, sample);
        SolutionFluids.setConditions(stack, fermenter.getTemperature(), fermenter.getPressure());
        return true;
    }

    /** The whole contents as one fluid stack (plain fluid, or a {@code solution} mixture) at the pipette's conditions. */
    public static FluidStack toFluidStack(ItemStack stack) {
        return SolutionFluids.toStack(getSolution(stack),
                SolutionFluids.temperatureOf(stack), SolutionFluids.pressureOf(stack));
    }

    // ── Fill-level bar ─────────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) { return getTotalAmount(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getTotalAmount(stack) / MAX_AMOUNT);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x2266FF; }

    // ── Display name ─────────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        Solution solution = getSolution(stack);
        if (solution.isEmpty()) return Component.literal("Pipette Dispenser");
        Solution.Part dom = solution.dominant();
        return dom.resource().toStack(dom.amount()).getHoverName().copy()
                .append(Component.literal(" Pipette"));
    }

    // ── Shift + right-click in air: open the amount-setting screen ─────────────

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) {
            openAmountScreen(player.getItemInHand(hand), hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static void openAmountScreen(ItemStack stack, InteractionHand hand) {
        int current = getTargetAmount(stack);
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                new com.dev1lroot.mcmods.omnitech.gui.PipetteAmountScreen(current, hand));
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Draw amount: " + getTargetAmount(stack) + " mB")
                .withStyle(ChatFormatting.DARK_AQUA));

        Solution solution = getSolution(stack);
        if (solution.isEmpty()) {
            tooltip.accept(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        int total = solution.totalAmount();
        tooltip.accept(Component.literal("Solution: " + total + " / " + MAX_AMOUNT + " mB")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(SolutionFluids.temperatureOf(stack) + " °C, "
                + SolutionFluids.pressureOf(stack) + " kPa").withStyle(ChatFormatting.GRAY));

        List<Component> lines = new ArrayList<>();
        GuiUtil.appendMixtureLines(lines, toFluidStack(stack));
        lines.forEach(tooltip);

        for (Solution.Part c : solution.components()) {
            FluidHazardUtil.appendHazardTooltip(c.resource().toStack(c.amount()), tooltip);
        }
    }
}
