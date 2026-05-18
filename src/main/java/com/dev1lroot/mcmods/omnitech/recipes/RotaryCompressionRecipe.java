/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Rotary Compressor recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/compression_recipes/}):
 * <pre>{@code
 * {
 *   "requiredKineticForce": 5.0,
 *   "inputFluid":  { "fluid": "omnitech:air",                   "amount": 1000 },
 *   "outputFluid": { "fluid": "omnitech:compressed_heated_air",  "amount": 1000 }
 * }
 * }</pre>
 */
public class RotaryCompressionRecipe {

    private final String id;
    private final float  requiredKineticForce;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    // Lazy-resolved caches
    private FluidStack cachedInputFluid  = null;
    private FluidStack cachedOutputFluid = null;

    public RotaryCompressionRecipe(String id, float requiredKineticForce,
                                   String inputFluidId, int inputFluidAmount,
                                   String outputFluidId, int outputFluidAmount) {
        this.id                   = id;
        this.requiredKineticForce = requiredKineticForce;
        this.inputFluidId         = parseId(inputFluidId,  "omnitech");
        this.inputFluidAmount     = inputFluidAmount;
        this.outputFluidId        = parseId(outputFluidId, "omnitech");
        this.outputFluidAmount    = outputFluidAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                  { return id; }
    public float  getRequiredKineticForce(){ return requiredKineticForce; }
    public int    getInputFluidAmount()    { return inputFluidAmount; }

    /** A copy of the required input FluidStack (resolved lazily). */
    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = (f != null) ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    /** A copy of the output FluidStack (resolved lazily). */
    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = (f != null) ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    public boolean matches(FluidStack inputFluid) {
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(getInputFluid().getFluid())) return false;
        return inputFluid.getAmount() >= inputFluidAmount;
    }
}
