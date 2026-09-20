/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Filter Press recipe — the inverse of a {@link SolvationRecipe}: instead of dissolving an
 * item into a fluid, it strains a fluid that still carries undissolved solids back apart into
 * the clean fluid and the solid residue.
 *
 * <p>JSON layout (in {@code data/omnitech/machine_recipe/filter_press/}):
 * <pre>{@code
 * {
 *   "requiredKineticForce": 3.0,
 *   "inputFluid":  { "fluid": "minecraft:water", "amount": 1000 },
 *   "outputFluid": { "fluid": "minecraft:water", "amount": 1000 },
 *   "outputItem":  { "item":  "minecraft:sand",  "amount": 1 }
 * }
 * }</pre>
 */
public class FilterPressRecipe {

    private final String id;
    private final float  requiredKineticForce;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    private final Identifier outputItemId;
    private final int        outputItemAmount;

    private Item       cachedOutputItem   = null;
    private boolean    outputItemResolved = false;
    private FluidStack cachedInputFluid   = null;
    private FluidStack cachedOutputFluid  = null;

    public FilterPressRecipe(String id, float requiredKineticForce,
            String inputFluidId, int inputFluidAmount,
            String outputFluidId, int outputFluidAmount,
            String outputItemId, int outputItemAmount) {
        this.id                   = id;
        this.requiredKineticForce = requiredKineticForce;
        this.inputFluidId         = parseId(inputFluidId,  "minecraft");
        this.inputFluidAmount     = inputFluidAmount;
        this.outputFluidId        = parseId(outputFluidId, "minecraft");
        this.outputFluidAmount    = outputFluidAmount;
        this.outputItemId         = parseId(outputItemId,  "minecraft");
        this.outputItemAmount     = outputItemAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public float  getRequiredKineticForce()  { return requiredKineticForce; }
    public int    getInputFluidAmount()      { return inputFluidAmount; }
    public int    getOutputItemAmount()      { return outputItemAmount; }

    /** The item that comes out as filtered-out residue (resolved lazily). */
    public Item getOutputItem() {
        if (!outputItemResolved) {
            outputItemResolved = true;
            cachedOutputItem = BuiltInRegistries.ITEM.getOptional(outputItemId).orElse(null);
        }
        return cachedOutputItem;
    }

    public ItemStack getOutputItemStack() {
        Item item = getOutputItem();
        return item == null ? ItemStack.EMPTY : new ItemStack(item, outputItemAmount);
    }

    /** A copy of the required input FluidStack — the solution as fed in, undissolved parts and all. */
    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = (f != null) ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    /** A copy of the output FluidStack — the same solution with the residue strained out. */
    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = (f != null) ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    /** Returns {@code true} when the input tank holds enough of this recipe's fluid. */
    public boolean matches(FluidStack inputFluid) {
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(getInputFluid().getFluid())) return false;
        return inputFluid.getAmount() >= inputFluidAmount;
    }
}
