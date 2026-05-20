/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-fluid physical limits loaded from {@code data/omnitech/fluid/<name>.json}.
 *
 * <p>Populated by {@link FluidLoader} at startup. Queried by machines at process
 * time to clamp temperature and pressure to physically plausible ranges.
 * Vanilla fluids (water, lava) that have no JSON entry get {@link FluidPhysics#DEFAULT}.
 */
public class FluidPhysicsRegistry {

    public record FluidPhysics(int minTemp, int maxTemp, int minPressure, int maxPressure) {
        /** Fallback for fluids with no JSON entry (e.g. vanilla water/lava). */
        public static final FluidPhysics DEFAULT = new FluidPhysics(-273, 10_000, 0, 100_000);
    }

    private static final Map<String, FluidPhysics> BY_PATH = new HashMap<>();

    /** Called by {@link FluidLoader} for each parsed fluid JSON. Package-private. */
    static void register(String name, FluidPhysics physics) {
        BY_PATH.put(name, physics);
        BY_PATH.put("flowing_" + name, physics);
    }

    /**
     * Returns the physics limits for the given fluid, or {@link FluidPhysics#DEFAULT}
     * if no entry exists (vanilla fluids, unknown fluids).
     */
    public static FluidPhysics get(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null || !key.getNamespace().equals(OmniTech.MODID)) return FluidPhysics.DEFAULT;
        return BY_PATH.getOrDefault(key.getPath(), FluidPhysics.DEFAULT);
    }
}
