/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by any block entity that participates in temperature-based thermal diffusion.
 *
 * <p>Each game tick, {@link com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity}
 * reads the temperature of every adjacent IThermalNode and exchanges heat with it proportional
 * to the temperature difference (Fourier conduction law, simplified).  This creates a natural
 * temperature gradient along conductor chains — distant tiles are cooler/warmer than those
 * adjacent to the source.
 *
 * <p>Heat-SOURCE machines (Heater, HeatExchanger, Decompressor) implement this interface so
 * conductors can pull thermal energy from them.  Heat-SINK machines (ChemicalReactor, etc.)
 * use the separate {@link IHeatReceiver}/{@link IColdReceiver} interfaces and receive
 * discrete heat units from hot/cold conductors.
 */
public interface IThermalNode {

    /** Planetary ambient temperature in °C. */
    float AMBIENT_TEMP = 20f;

    /** Current temperature of this node in °C. */
    float getTemperature();

    /**
     * Apply a temperature change to this node.
     *
     * @param dT positive = node gains heat (gets hotter), negative = node loses heat (cools)
     */
    void applyHeat(float dT);
}
