/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

/**
 * Server-side averaging meter for electric power and voltage.
 *
 * <p>The electric network delivers energy in bursts (every few ticks), so
 * instantaneous values flicker. The meter accumulates energy over a
 * {@value #WINDOW}-tick (1 s) window and publishes the average power and
 * voltage once per window.
 *
 * <p>Usage: call {@link #add} whenever energy crosses the metered point and
 * {@link #tick} once per server tick. Values are transient (not saved).
 */
public final class PowerMeter {

    /** Averaging window in ticks (1 second). */
    public static final int WINDOW = 20;

    private float energyAcc  = 0f;
    private float voltSum    = 0f;
    private int   voltCount  = 0;
    private int   ticks      = 0;

    private float avgUnitsPerTick = 0f;
    private float avgVolts        = 0f;

    /** Records {@code energy} kJ flowing through the meter (no voltage information). */
    public void add(float energy) {
        if (energy > 0f) energyAcc += energy;
    }

    /**
     * Records {@code energy} kJ flowing through the meter at {@code volts}.
     * The voltage is sampled even when {@code energy} is 0 (e.g. a full buffer
     * still sees line voltage).
     */
    public void add(float energy, float volts) {
        add(energy);
        if (volts > 0f) {
            voltSum += volts;
            voltCount++;
        }
    }

    /** Advances the window; publishes averages once every {@value #WINDOW} ticks. */
    public void tick() {
        if (++ticks < WINDOW) return;
        avgUnitsPerTick = energyAcc / ticks;
        avgVolts        = voltCount > 0 ? voltSum / voltCount : 0f;
        energyAcc = 0f;
        voltSum   = 0f;
        voltCount = 0;
        ticks     = 0;
    }

    /** Average energy flow over the last window in kJ/tick. */
    public float getUnitsPerTick() { return avgUnitsPerTick; }

    /** Average power over the last window in watts. */
    public double getWatts() { return ElectricUnits.toWatts(avgUnitsPerTick); }

    /** Average voltage over the last window in volts (0 = no supply). */
    public float getVolts() { return avgVolts; }

    // ── ContainerData encoding ───────────────────────────────────────────────

    /** Power in whole watts for ContainerData sync (NeoForge syncs full ints). */
    public int syncWatts() {
        return (int) Math.min(getWatts(), Integer.MAX_VALUE);
    }

    /** Voltage × 10 for ContainerData sync. */
    public int syncDeciVolts() {
        return encodeVolts(avgVolts);
    }

    public static int encodeVolts(float volts) {
        return (int) Math.min(volts * 10f, Integer.MAX_VALUE);
    }

    public static float decodeVolts(int deciVolts) {
        return deciVolts / 10f;
    }
}
