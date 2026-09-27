/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One Press recipe: a single item squeezed into a fluid (its juice) plus a leftover item (the
 * pressed pulp), once enough kinetic force has been put in.
 *
 * <p>JSON layout (in {@code data/omnitech/machine_recipe/press/}); either output may be omitted:
 * <pre>{@code
 * {
 *   "requiredKineticForce": 20,
 *   "input":       "minecraft:apple",
 *   "outputFluid": { "fluid": "omnitech:apple_juice", "amount": 150 },
 *   "outputItem":  { "item": "omnitech:apple_puree", "count": 1, "chance": 1.0 }
 * }
 * }</pre>
 *
 * <p>Instead of {@code outputFluid}, a recipe may squeeze out a <em>mixture</em>: each component
 * with its amount and whether it is dissolved (undissolved parts are strained out later by the
 * Filter Press):
 * <pre>{@code
 *   "outputMixture": [
 *     { "fluid": "omnitech:honey",   "amount": 175 },
 *     { "fluid": "omnitech:beeswax", "amount": 75, "dissolved": false }
 *   ]
 * }</pre>
 */
public class PressRecipe {

    /** One component of a mixture output. */
    public record MixturePart(Identifier fluidId, int amount, boolean dissolved) {
        public @Nullable Fluid fluid() { return BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null); }
    }

    private final String id;
    private final int requiredKineticForce;
    private final Identifier inputId;
    @Nullable private final Identifier outputFluidId;
    private final int outputFluidAmount;
    @Nullable private final Identifier outputItemId;
    private final int outputItemCount;
    private final float outputItemChance;
    private final List<MixturePart> outputMixture;

    public PressRecipe(String id, int requiredKineticForce, Identifier inputId,
            @Nullable Identifier outputFluidId, int outputFluidAmount,
            @Nullable Identifier outputItemId, int outputItemCount, float outputItemChance,
            List<MixturePart> outputMixture) {
        this.id = id;
        this.requiredKineticForce = requiredKineticForce;
        this.inputId = inputId;
        this.outputFluidId = outputFluidId;
        this.outputFluidAmount = outputFluidAmount;
        this.outputItemId = outputItemId;
        this.outputItemCount = outputItemCount;
        this.outputItemChance = outputItemChance;
        this.outputMixture = List.copyOf(outputMixture);
    }

    public String getId()                 { return id; }
    public int getRequiredKineticForce()  { return requiredKineticForce; }
    public float getOutputItemChance()    { return outputItemChance; }

    public @Nullable Item getInput() {
        return BuiltInRegistries.ITEM.getOptional(inputId).orElse(null);
    }

    public boolean matches(ItemStack stack) {
        Item input = getInput();
        return input != null && !stack.isEmpty() && stack.is(input);
    }

    /**
     * The fluid output as the components it is made of: the mixture's parts, or the single
     * dissolved juice. Components whose fluid is missing are left out.
     */
    public List<Solution.Part> getOutputComponents() {
        List<Solution.Part> parts = new ArrayList<>();
        if (!outputMixture.isEmpty()) {
            for (MixturePart c : outputMixture) {
                Fluid f = c.fluid();
                if (f != null && c.amount() > 0) parts.add(Solution.Part.of(f, c.amount(), c.dissolved()));
            }
        } else {
            FluidStack juice = plainOutput();
            if (!juice.isEmpty()) parts.add(Solution.Part.of(juice.getFluid(), juice.getAmount(), true));
        }
        return parts;
    }

    /** The juice (or mixture) squeezed out, or {@link FluidStack#EMPTY}. */
    public FluidStack getOutputFluid() {
        if (outputMixture.isEmpty()) return plainOutput();
        Solution mix = Solution.EMPTY;
        for (Solution.Part p : getOutputComponents()) mix = mix.plus(p.fluid(), p.amount(), p.dissolved());
        return SolutionFluids.toStack(mix);
    }

    private FluidStack plainOutput() {
        if (outputFluidId == null || outputFluidAmount <= 0) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(outputFluidId).orElse(null);
        return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, outputFluidAmount);
    }

    /** The leftover pulp at full count (ignoring chance), or {@link ItemStack#EMPTY}. */
    public ItemStack getOutputItem() {
        if (outputItemId == null || outputItemCount <= 0) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(outputItemId).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, outputItemCount);
    }

    /** The leftover pulp for one pressing, after its chance roll. */
    public ItemStack rollOutputItem(RandomSource random) {
        return random.nextFloat() < outputItemChance ? getOutputItem() : ItemStack.EMPTY;
    }
}
