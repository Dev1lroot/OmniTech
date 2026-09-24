/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * What a fluid leaves behind, or turns into, while the {@link
 * com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler.BoilerBlockEntity Boiler} boils it off.
 *
 * <p>Every {@code inputAmount} mB of {@code inputFluid} that boils away counts as one cycle. Per
 * cycle the boiler rolls {@code resultChance} for {@code resultCount} of {@code resultItem} (the
 * dissolved solids that stay behind, e.g. salt), and — if an output fluid is given — the vapour
 * leaving the boiler is {@code outputAmount} mB of {@code outputFluid} instead of the fluid itself.
 * <pre>
 * {
 *   "input":       { "fluid": "omnitech:hydrazine_hydrate", "amount": 1000 },
 *   "outputFluid": { "fluid": "omnitech:hydrazine",         "amount": 700  },   // optional
 *   "result":      { "item":  "omnitech:sodium_chlorine",   "amount": 1, "chance": 1.0 }   // optional
 * }
 * </pre>
 */
public class BoilingRecipe {

    private final String id;

    private final Identifier inputFluidId;
    private final int inputAmount;

    @Nullable private final Identifier outputFluidId;
    private final int outputAmount;

    @Nullable private final Identifier resultItemId;
    private final int resultCount;
    private final float resultChance;

    // Lazily resolved so loading never touches registries that are not bound yet
    private Fluid cachedInputFluid  = null;
    private Fluid cachedOutputFluid = null;
    private Item  cachedResultItem  = null;

    public BoilingRecipe(String id,
                         String inputFluid, int inputAmount,
                         @Nullable String outputFluid, int outputAmount,
                         @Nullable String resultItem, int resultCount, float resultChance) {
        this.id            = id;
        this.inputFluidId  = resolveId(inputFluid, "minecraft");
        this.inputAmount   = Math.max(1, inputAmount);
        this.outputFluidId = outputFluid == null || outputFluid.isEmpty() ? null : resolveId(outputFluid, "omnitech");
        this.outputAmount  = outputAmount;
        this.resultItemId  = resultItem == null || resultItem.isEmpty() ? null : resolveId(resultItem, "omnitech");
        this.resultCount   = resultCount;
        this.resultChance  = resultChance;
    }

    private static Identifier resolveId(String id, String defaultNamespace) {
        return id.contains(":") ? Identifier.parse(id)
                                : Identifier.fromNamespaceAndPath(defaultNamespace, id);
    }

    // ── Fluids ────────────────────────────────────────────────────────────────

    public Fluid getInputFluid() {
        if (cachedInputFluid == null) cachedInputFluid = BuiltInRegistries.FLUID.getValue(inputFluidId);
        return cachedInputFluid;
    }

    public boolean matches(Fluid fluid) { return fluid != null && fluid == getInputFluid(); }

    /** True if the vapour is a different fluid from the one that boils. */
    public boolean convertsFluid() { return outputFluidId != null && outputAmount > 0; }

    public Fluid getOutputFluid() {
        if (outputFluidId == null) return null;
        if (cachedOutputFluid == null) cachedOutputFluid = BuiltInRegistries.FLUID.getValue(outputFluidId);
        return cachedOutputFluid;
    }

    /** mB of output fluid produced when {@code boiled} mB of the input fluid boils away. */
    public int vapourFor(int boiled) {
        return (int) ((long) boiled * outputAmount / inputAmount);
    }

    // ── Item result ───────────────────────────────────────────────────────────

    public boolean hasResult() { return resultItemId != null && resultCount > 0 && resultChance > 0f; }

    /** The stack this recipe yields per cycle if the roll succeeds, empty otherwise. */
    public ItemStack rollResult(RandomSource random) {
        if (!hasResult() || random.nextFloat() >= resultChance) return ItemStack.EMPTY;
        return resultStack();
    }

    /** The stack a successful roll yields (used to check the output slot has room). */
    public ItemStack resultStack() {
        if (!hasResult()) return ItemStack.EMPTY;
        if (cachedResultItem == null) cachedResultItem = BuiltInRegistries.ITEM.getValue(resultItemId);
        return cachedResultItem == null ? ItemStack.EMPTY : new ItemStack(cachedResultItem, resultCount);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()       { return id; }
    public int getInputAmount() { return inputAmount; }
    public int getOutputAmount() { return outputAmount; }
    public float getResultChance() { return resultChance; }
}
