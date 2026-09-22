/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One reaction that can run inside a {@code FermenterBlockEntity}'s compound solution.
 *
 * <p>Unlike the single-fluid machine recipes, a fermentation recipe never looks at "the" input
 * fluid — it asks whether the <em>components</em> it needs are present anywhere in the mixture,
 * and if so, on every {@link #interval} ticks swaps a few mB of some components for others. Several
 * recipes can be active at once, each on its own clock, which is what makes a batch behave like a
 * living process (yeast multiplying while it is also being poisoned by its own ethanol).
 *
 * <p>JSON layout (in {@code data/omnitech/machine_recipe/fermentation/}):
 * <pre>{@code
 * {
 *   "interval": 100,                                    // ticks between reactions
 *   "inputs":    [ { "fluid": "minecraft:water", "amount": 1 } ],   // consumed each reaction
 *   "catalysts": [ { "fluid": "omnitech:yeast",  "amount": 1 } ],   // must be present, not consumed
 *   "outputs":   [ { "fluid": "omnitech:ethanol", "amount": 2 } ],  // produced each reaction
 *   "outputItem": { "item": "minecraft:bone_meal", "amount": 1 }    // optional, goes to the item slot
 * }
 * }</pre>
 * {@code catalysts}, {@code outputs} and {@code outputItem} are all optional.
 */
public final class FermentationRecipe {

    /**
     * A fluid + mB amount, with the {@link Fluid} resolved lazily from the registry, and whether it is
     * <em>dissolved</em> in the mixture (the default) or an undissolved solid suspended in it. The flag
     * matters where the amount is <em>added</em> (outputs, dissolved items, spawned microbes); when a
     * recipe merely looks for or consumes a fluid it matches the fluid whichever form it is in.
     */
    public static final class FluidAmount {
        private final Identifier id;
        private final int amount;
        private final boolean dissolved;
        private @Nullable Fluid fluid;
        private boolean resolved;

        public FluidAmount(Identifier id, int amount) { this(id, amount, true); }

        public FluidAmount(Identifier id, int amount, boolean dissolved) {
            this.id = id;
            this.amount = amount;
            this.dissolved = dissolved;
        }

        public Identifier id()      { return id; }
        public int amount()         { return amount; }
        public boolean dissolved()  { return dissolved; }

        /** The registered fluid, or {@code null} if this id names nothing (typo / missing mod). */
        public @Nullable Fluid fluid() {
            if (!resolved) {
                resolved = true;
                fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
            }
            return fluid;
        }
    }

    private final String id;
    private final int interval;
    private final List<FluidAmount> inputs;
    private final List<FluidAmount> catalysts;
    private final List<FluidAmount> outputs;
    private final @Nullable Identifier outputItemId;
    private final int outputItemAmount;
    private @Nullable Item outputItem;
    private boolean outputItemResolved;

    public FermentationRecipe(String id, int interval,
            List<FluidAmount> inputs, List<FluidAmount> catalysts, List<FluidAmount> outputs,
            @Nullable Identifier outputItemId, int outputItemAmount) {
        this.id = id;
        this.interval = Math.max(1, interval);
        this.inputs = inputs;
        this.catalysts = catalysts;
        this.outputs = outputs;
        this.outputItemId = outputItemId;
        this.outputItemAmount = outputItemAmount;
    }

    public String getId()                  { return id; }
    public int getInterval()               { return interval; }
    public List<FluidAmount> getInputs()   { return inputs; }
    public List<FluidAmount> getCatalysts(){ return catalysts; }
    public List<FluidAmount> getOutputs()  { return outputs; }

    /** Net change in total volume per reaction (outputs − inputs); may be negative. */
    public int netVolume() {
        int net = 0;
        for (FluidAmount o : outputs) net += o.amount();
        for (FluidAmount i : inputs)  net -= i.amount();
        return net;
    }

    public ItemStack getOutputItemStack() {
        if (outputItemId == null) return ItemStack.EMPTY;
        if (!outputItemResolved) {
            outputItemResolved = true;
            outputItem = BuiltInRegistries.ITEM.getOptional(outputItemId).orElse(null);
        }
        return outputItem == null ? ItemStack.EMPTY : new ItemStack(outputItem, outputItemAmount);
    }

    /**
     * True when every input is present in at least its consumed amount and every catalyst is
     * present in at least its required amount. A recipe referencing an unregistered fluid never
     * matches, rather than throwing.
     */
    public boolean matches(Solution solution) {
        for (FluidAmount in : inputs) {
            Fluid f = in.fluid();
            if (f == null || solution.amountOf(f) < in.amount()) return false;
        }
        for (FluidAmount cat : catalysts) {
            Fluid f = cat.fluid();
            if (f == null || solution.amountOf(f) < cat.amount()) return false;
        }
        return true;
    }

    /** Applies one reaction to {@code solution}: consumes the inputs, then adds the outputs. */
    public Solution apply(Solution solution) {
        Solution next = solution;
        for (FluidAmount in : inputs)  next = next.minus(in.fluid(), in.amount());
        for (FluidAmount out : outputs) {
            if (out.fluid() != null) next = next.plus(out.fluid(), out.amount(), out.dissolved());
        }
        return next;
    }
}
