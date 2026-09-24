/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.FluidFormulaRegistry;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.Formula;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionEngine;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@link FlaskItem} that actually runs its contents through {@link ReactionEngine} instead of
 * just carrying them inertly — the first piece of game plumbing for the generic reaction
 * simulation (see the {@code reaction} package). Everything about holding/filling/pouring is
 * inherited unchanged; the only addition is {@link #inventoryTick}, which — once per second,
 * while the flask is actually carried in a player's inventory (vanilla only ticks items there,
 * not ones sitting in a chest or on the ground) — looks at the first two components of its
 * {@link Solution}, asks {@code ReactionEngine} what happens between them, and if there's a real
 * reaction, converts a rate-limited slice of the reactants into whatever products this mod
 * already has a fluid for.
 *
 * <p>Known simplification: mB is treated as a mole-equivalent unit for stoichiometry (the same
 * loose convention the Chemical Mixer already uses) — this isn't real molarity, just enough to
 * make the ratios in the balanced equation mean something. A product only appears in the flask if
 * some {@code data/omnitech/fluid/*.json} was given a matching {@code "formula"} (see
 * {@link FluidFormulaRegistry}); anything else the reaction produces (an untracked salt, mostly)
 * is chemically consumed but not represented in the flask — a content gap to close by adding more
 * fluids/formulas over time, not a bug in the engine itself. Gas-state products always vent away
 * rather than accumulating.
 */
public class ReactionFlaskItem extends FlaskItem {

    private static final int TICK_INTERVAL = 20;
    private static final int RATE_UNITS_PER_TICK = 10;

    public ReactionFlaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        if (level.getGameTime() % TICK_INTERVAL == 0) simulate(stack);
    }

    private static void simulate(ItemStack stack) {
        Solution solution = getSolution(stack);
        List<Solution.Part> parts = solution.components();
        if (parts.size() < 2) return;

        Solution.Part partA = parts.get(0);
        Solution.Part partB = parts.get(1);
        String formulaTextA = FluidFormulaRegistry.get(partA.fluid());
        String formulaTextB = FluidFormulaRegistry.get(partB.fluid());
        if (formulaTextA == null || formulaTextB == null) return;

        ReactionResult result = ReactionEngine.react(formulaTextA, formulaTextB);
        if (!(result instanceof ReactionResult.Reaction reaction)) return;

        Formula formulaA = Formula.of(formulaTextA);
        Formula formulaB = Formula.of(formulaTextB);
        int coeffA = coefficientOf(reaction.reactants(), formulaA);
        int coeffB = coefficientOf(reaction.reactants(), formulaB);
        if (coeffA <= 0 || coeffB <= 0) return;

        int units = Math.min(Math.min(partA.amount() / coeffA, partB.amount() / coeffB), RATE_UNITS_PER_TICK);
        if (units <= 0) return;

        Solution next = solution.minus(partA.fluid(), units * coeffA).minus(partB.fluid(), units * coeffB);
        for (ReactionResult.Species product : reaction.products()) {
            if (product.state() == ReactionResult.PhysicalState.GAS) continue; // vents away, untracked
            Fluid fluid = FluidFormulaRegistry.getFluidByFormula(product.formula().canonical());
            if (fluid == null) continue; // no in-game fluid represents this product yet
            boolean dissolved = product.state() != ReactionResult.PhysicalState.PRECIPITATE;
            next = next.plus(fluid, units * product.coefficient(), dissolved);
        }
        setSolution(stack, next);
    }

    private static int coefficientOf(List<ReactionResult.Species> species, Formula formula) {
        for (ReactionResult.Species s : species) if (s.formula().equals(formula)) return s.coefficient();
        return 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        Solution solution = getSolution(stack);
        if (solution.isEmpty()) return Component.literal("Reaction Flask");
        if (solution.components().size() == 1) {
            Solution.Part only = solution.components().get(0);
            return only.resource().toStack(only.amount()).getHoverName().copy()
                    .append(Component.literal(only.dissolved() ? " Reaction Flask" : " Slurry Reaction Flask"));
        }
        Solution.Part dom = solution.dominant();
        return dom.resource().toStack(dom.amount()).getHoverName().copy()
                .append(Component.literal(" Reaction Flask"));
    }
}
