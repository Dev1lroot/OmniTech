/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-fluid physical limits and phase diagram loaded from
 * {@code data/omnitech/fluid/<name>.json}.
 *
 * <p>Populated by {@link FluidLoader} at startup. Queried by machines at process
 * time to clamp temperature and pressure to physically plausible ranges, and by
 * {@link FluidPhaseUtil} to determine the current thermodynamic phase.
 */
public class FluidPhysicsRegistry {

    /**
     * Full phase diagram for a fluid.
     *
     * <p>The boiling-point formula used by {@link FluidPhaseUtil} is:
     * {@code T_boil(P) = boilingPointC + boilingSlope × ln(P / 101)},
     * where {@code boilingSlope ≈ (T_c - T_boil_101) / ln(P_c / 101)}.
     *
     * <p>{@code plasmaTempC < 0} means plasma is not modelled for this fluid.
     */
    public record PhaseDiagram(
        int     meltingPointC,       // normal melting point at 101 kPa  (°C)
        int     boilingPointC,       // normal boiling point at 101 kPa  (°C)
        float   boilingSlope,        // d(T_boil) / d(ln P), °C per unit
        int     criticalTempC,       // critical point temperature        (°C)
        int     criticalPressureKPa, // critical point pressure           (kPa)
        int     tripleTempC,         // triple point temperature          (°C)
        int     triplePressureKPa,   // triple point pressure             (kPa)
        int     plasmaTempC,         // plasma onset temperature; -1 = none
        boolean hasSolid             // false for fluids with no solid phase
    ) {}

    public record FluidPhysics(int minTemp, int maxTemp, int minPressure, int maxPressure,
                               @Nullable PhaseDiagram phaseDiagram) {
        public static final FluidPhysics DEFAULT = new FluidPhysics(-273, 10_000, 0, 100_000, null);
    }

    private static final Map<String, FluidPhysics> BY_PATH = new HashMap<>();

    static void register(String name, FluidPhysics physics) {
        BY_PATH.put(name, physics);
        BY_PATH.put("flowing_" + name, physics);
    }

    public static FluidPhysics get(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null || !key.getNamespace().equals(OmniTech.MODID)) return FluidPhysics.DEFAULT;
        return BY_PATH.getOrDefault(key.getPath(), FluidPhysics.DEFAULT);
    }
}
