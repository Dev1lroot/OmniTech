/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the thermodynamic {@link FluidPhase} of a fluid from its
 * temperature, pressure, and {@link FluidPhysicsRegistry.PhaseDiagram}.
 *
 * <h3>Phase boundaries</h3>
 * <ol>
 *   <li>{@code T ≥ plasma_temp} → {@link FluidPhase#PLASMA}</li>
 *   <li>{@code T ≥ T_c AND P ≥ P_c} → {@link FluidPhase#SUPERCRITICAL}</li>
 *   <li>{@code T ≥ T_c} (P below critical) → {@link FluidPhase#GAS}</li>
 *   <li>{@code T ≥ T_boil(P)} → {@link FluidPhase#VAPOUR}, where
 *       {@code T_boil(P) = T_boil_101 + slope × ln(P/101)}</li>
 *   <li>{@code T ≤ melting_point} → {@link FluidPhase#SOLID} (if has_solid)</li>
 *   <li>otherwise → {@link FluidPhase#LIQUID}</li>
 * </ol>
 *
 * <p>{@link #getPhaseLabelComponent} is <b>client-side only</b>; it resolves
 * fluid-specific lang overrides ({@code omnitech.fluid.<name>.<phase>}) and falls
 * back to generic ones ({@code omnitech.fluid.phase.<phase>}).
 */
public final class FluidPhaseUtil {

    private FluidPhaseUtil() {}

    /**
     * Computes the phase of a fluid at the given conditions.
     * Returns {@code null} if {@code diagram} is {@code null} (no phase data).
     */
    @Nullable
    public static FluidPhase getPhase(int tempC, int pressureKPa,
            @Nullable FluidPhysicsRegistry.PhaseDiagram diagram) {
        if (diagram == null) return null;

        if (diagram.plasmaTempC() >= 0 && tempC >= diagram.plasmaTempC())
            return FluidPhase.PLASMA;

        if (tempC >= diagram.criticalTempC() && pressureKPa >= diagram.criticalPressureKPa())
            return FluidPhase.SUPERCRITICAL;

        if (tempC >= diagram.criticalTempC())
            return FluidPhase.GAS;

        int boilAtP = boilingPointAtPressure(pressureKPa, diagram);
        if (tempC >= boilAtP)
            return FluidPhase.VAPOUR;

        if (diagram.hasSolid() && tempC <= diagram.meltingPointC())
            return FluidPhase.SOLID;

        return FluidPhase.LIQUID;
    }

    /**
     * Returns the phase label component for a fluid stack, or {@code null} if
     * no phase diagram is defined for that fluid.
     *
     * <p>Lang key resolution order:
     * <ol>
     *   <li>{@code omnitech.fluid.<fluid_path>.<phase>} — fluid-specific name
     *       (e.g. "Ice", "Steam")</li>
     *   <li>{@code omnitech.fluid.phase.<phase>} — generic fallback
     *       (e.g. "Solid", "Vapour")</li>
     * </ol>
     *
     * <p><b>Client-side only.</b>
     */
    @Nullable
    public static Component getPhaseLabelComponent(FluidStack fluid) {
        if (fluid.isEmpty()) return null;

        var physics = FluidPhysicsRegistry.get(fluid.getFluid());
        var diagram = physics.phaseDiagram();
        if (diagram == null) return null;

        Integer tempBox     = fluid.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        Integer pressureBox = fluid.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        int temp     = tempBox     != null ? tempBox     : 20;
        int pressure = pressureBox != null ? pressureBox : 101;

        FluidPhase phase = getPhase(temp, pressure, diagram);
        if (phase == null) return null;

        var fluidKey = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        String path  = fluidKey != null ? fluidKey.getPath() : "unknown";
        if (path.startsWith("flowing_")) path = path.substring(8);

        String specificKey = "omnitech.fluid." + path + "." + phase.phaseKey();
        String genericKey  = "omnitech.fluid.phase." + phase.phaseKey();

        Language lang = Language.getInstance();
        return Component.translatable(lang != null && lang.has(specificKey) ? specificKey : genericKey);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static int boilingPointAtPressure(int pressureKPa,
            FluidPhysicsRegistry.PhaseDiagram d) {
        if (pressureKPa <= 0) pressureKPa = 1;

        if (pressureKPa >= d.criticalPressureKPa())
            return d.criticalTempC();

        double boilAtP = d.boilingPointC() + d.boilingSlope() * Math.log((double) pressureKPa / 101.0);
        return (int) Math.min(d.criticalTempC(), Math.max(d.meltingPointC(), boilAtP));
    }
}
