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
import net.neoforged.neoforge.fluids.FluidStack;

public class BoilerRecipe {

    private final String id;
    private final int requiredMinimalTemperature;
    private final int productionTime;
    private final int heatConsumptionPerTick;

    private final Identifier inputFluidId;
    private final int inputAmount;

    private final Identifier outputFluidId;
    private final int outputAmount;

    // Optional item result
    private final Identifier resultItemId;
    private final int resultCount;
    private final float resultChance;

    // Lazily resolved to avoid "Components not bound yet" crash on load
    private Fluid cachedInputFluid  = null;
    private Fluid cachedOutputFluid = null;
    private Item  cachedResultItem  = null;

    public BoilerRecipe(String id, int requiredMinimalTemperature, int productionTime,
                        int heatConsumptionPerTick,
                        String inputFluid, int inputAmount,
                        String outputFluid, int outputAmount,
                        String resultItem, int resultCount, float resultChance) {
        this.id = id;
        this.requiredMinimalTemperature = requiredMinimalTemperature;
        this.productionTime = productionTime;
        this.heatConsumptionPerTick = heatConsumptionPerTick;
        this.inputFluidId  = resolveId(inputFluid,  "minecraft");
        this.inputAmount   = inputAmount;
        this.outputFluidId = resolveId(outputFluid, "omnitech");
        this.outputAmount  = outputAmount;
        this.resultItemId  = resultItem.isEmpty() ? null : resolveId(resultItem, "omnitech");
        this.resultCount   = resultCount;
        this.resultChance  = resultChance;
    }

    private static Identifier resolveId(String id, String defaultNamespace) {
        return id.contains(":") ? Identifier.parse(id)
                                : Identifier.fromNamespaceAndPath(defaultNamespace, id);
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public Fluid getInputFluid() {
        if (cachedInputFluid == null)
            cachedInputFluid = BuiltInRegistries.FLUID.getValue(inputFluidId);
        return cachedInputFluid;
    }

    public FluidStack getInputFluidStack() {
        Fluid f = getInputFluid();
        return f == null ? FluidStack.EMPTY : new FluidStack(f, inputAmount);
    }

    public Fluid getOutputFluid() {
        if (cachedOutputFluid == null)
            cachedOutputFluid = BuiltInRegistries.FLUID.getValue(outputFluidId);
        return cachedOutputFluid;
    }

    public FluidStack getOutputFluidStack() {
        Fluid f = getOutputFluid();
        return f == null ? FluidStack.EMPTY : new FluidStack(f, outputAmount);
    }

    // ── Item result ───────────────────────────────────────────────────────────

    public boolean hasResult() { return resultItemId != null && resultChance > 0f; }

    /**
     * Rolls the item result using random chance.
     * Returns an empty stack if the roll fails or there is no result.
     */
    public ItemStack rollResult(RandomSource random) {
        if (!hasResult()) return ItemStack.EMPTY;
        if (random.nextFloat() >= resultChance) return ItemStack.EMPTY;
        if (cachedResultItem == null)
            cachedResultItem = BuiltInRegistries.ITEM.getValue(resultItemId);
        return cachedResultItem == null ? ItemStack.EMPTY : new ItemStack(cachedResultItem, resultCount);
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /** True when the fluid in the water tank matches this recipe's input fluid. */
    public boolean matchesInput(FluidStack tank) {
        if (tank.isEmpty()) return false;
        Fluid f = getInputFluid();
        return f != null && tank.is(f) && tank.getAmount() >= inputAmount;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()                       { return id; }
    public int getRequiredMinimalTemperature()  { return requiredMinimalTemperature; }
    public int getProductionTime()             { return productionTime; }
    /** Degrees Celsius deducted from the boiler's stored heat every processing tick. */
    public int getHeatConsumptionPerTick()     { return heatConsumptionPerTick; }
    public int getInputAmount()                { return inputAmount; }
    public int getOutputAmount()               { return outputAmount; }
    public float getResultChance()             { return resultChance; }
}
