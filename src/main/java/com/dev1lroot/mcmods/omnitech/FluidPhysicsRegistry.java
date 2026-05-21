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
 * Per-fluid physical limits and phase diagram.
 *
 * <p>OmniTech fluids are populated by {@link FluidLoader} from
 * {@code data/omnitech/fluid/<name>.json}.  External fluids (vanilla, other mods)
 * can be registered programmatically via {@link #registerExternal}.
 *
 * <p>Queried by machines at process time to clamp temperature and pressure to
 * physically plausible ranges, by {@link FluidPhaseUtil} to determine the current
 * thermodynamic phase, and by the reactor to compute the neutron moderation factor.
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

    /**
     * @param neutronSlowing  fraction [0..1] — how effectively this fluid thermalises
     *                        fast neutrons.  1.0 = excellent moderator (e.g. light water);
     *                        0.0 = neutron absorber or inert (e.g. hafnium, cadmium).
     *                        Multiplied by fill-fraction to give the reactor moderation
     *                        factor used in neutron-flux calculations.
     */
    public record FluidPhysics(int minTemp, int maxTemp, int minPressure, int maxPressure,
                               @Nullable PhaseDiagram phaseDiagram, float neutronSlowing) {
        public static final FluidPhysics DEFAULT = new FluidPhysics(-273, 10_000, 0, 100_000, null, 0.0f);
    }

    // Keyed by full "namespace:path" so any mod's fluid can be registered.
    private static final Map<String, FluidPhysics> BY_ID = new HashMap<>();

    /** Called by {@link FluidLoader} for OmniTech fluids (registers source + flowing). */
    static void register(String name, FluidPhysics physics) {
        BY_ID.put(OmniTech.MODID + ":" + name,           physics);
        BY_ID.put(OmniTech.MODID + ":flowing_" + name,   physics);
    }

    /**
     * Register physics for a fluid belonging to another namespace.
     * Both the source ({@code namespace:name}) and the flowing variant
     * ({@code namespace:flowing_name}) are mapped to the same entry.
     */
    public static void registerExternal(String namespace, String name, FluidPhysics physics) {
        BY_ID.put(namespace + ":" + name,           physics);
        BY_ID.put(namespace + ":flowing_" + name,   physics);
    }

    public static FluidPhysics get(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null) return FluidPhysics.DEFAULT;
        return BY_ID.getOrDefault(key.toString(), FluidPhysics.DEFAULT);
    }
}
