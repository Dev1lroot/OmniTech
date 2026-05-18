/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Fluid Collector recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/fluid_collector_recipes/}):
 * <pre>{@code
 * {
 *   "inputBlock":  "minecraft:stone",
 *   "outputFluid": { "fluid": "omnitech:some_fluid", "amount": 100 }
 * }
 * }</pre>
 *
 * <p>{@code amount} is the millibuckets generated per tick while the back face
 * of the collector touches {@code inputBlock}.
 */
public class FluidCollectorRecipe {

    private final String id;
    private final Identifier inputBlockId;
    private final Identifier outputFluidId;
    private final int       outputFluidAmount;

    // Lazy-resolved caches
    private Block      cachedInputBlock  = null;
    private FluidStack cachedOutputFluid = null;

    public FluidCollectorRecipe(String id, String inputBlockId,
                                String outputFluidId, int outputFluidAmount) {
        this.id               = id;
        this.inputBlockId     = parseId(inputBlockId,  "minecraft");
        this.outputFluidId    = parseId(outputFluidId, "omnitech");
        this.outputFluidAmount = outputFluidAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    public String getId() { return id; }

    /** The block that must be touching the collector's back face. */
    public Block getInputBlock() {
        if (cachedInputBlock == null)
            cachedInputBlock = BuiltInRegistries.BLOCK.getValue(inputBlockId);
        return cachedInputBlock;
    }

    /** A copy of the fluid produced per tick (resolved lazily). */
    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = (f != null) ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    public boolean matches(Block block) {
        return block == getInputBlock();
    }
}
