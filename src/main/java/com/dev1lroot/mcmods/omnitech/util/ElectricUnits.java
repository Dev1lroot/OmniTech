/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import java.util.Locale;

/**
 * SI unit conversions and formatting for the electric network.
 *
 * <h3>Internal unit</h3>
 * <p>All electric energy values stored in block entities, items and recipes
 * ({@code storedEu}, {@code energyStored}, {@code energyRequired}, …) are in
 * <b>kilojoules</b>: {@code 1 unit = 1 kJ = 1000 J}. A rate expressed per game
 * tick is therefore kJ/tick, and since a server tick is 1/20 s:
 * <pre>
 *   1 kJ/tick = 1000 J × 20 /s = 20 kW
 * </pre>
 *
 * <h3>Electrical quantities</h3>
 * <ul>
 *   <li>Power      P = E / t            (W = J/s)</li>
 *   <li>Current    I = P / U            (A)</li>
 *   <li>Resistance R = U² / P           (Ω, equivalent load resistance)</li>
 *   <li>Capacitor  E = ½ · C · U²       (F, used by the Electric Capacitor)</li>
 * </ul>
 */
public final class ElectricUnits {
    private ElectricUnits() {}

    /** Joules per internal energy unit (1 unit = 1 kJ). */
    public static final double JOULES_PER_UNIT = 1000.0;

    /** Server ticks per second. */
    public static final int TICKS_PER_SECOND = 20;

    /** Joules per kilowatt-hour. */
    public static final double JOULES_PER_KWH = 3_600_000.0;

    /** Nominal voltage of the machine grid (three-phase industrial line voltage). */
    public static final float GRID_VOLTAGE = 400f;

    // ── Conversions ───────────────────────────────────────────────────────────

    /** Internal energy units (kJ) → joules. */
    public static double toJoules(double units) {
        return units * JOULES_PER_UNIT;
    }

    /** Internal energy units per tick (kJ/t) → watts. */
    public static double toWatts(double unitsPerTick) {
        return unitsPerTick * JOULES_PER_UNIT * TICKS_PER_SECOND;
    }

    /** Watts → internal energy units per tick (kJ/t). */
    public static double fromWatts(double watts) {
        return watts / (JOULES_PER_UNIT * TICKS_PER_SECOND);
    }

    /** I = P / U. Returns 0 when there is no voltage. */
    public static double current(double watts, double volts) {
        return volts > 0.0 ? watts / volts : 0.0;
    }

    /** Equivalent load resistance R = U² / P. Returns +∞ for an open circuit (P = 0). */
    public static double resistance(double watts, double volts) {
        return watts > 0.0 ? volts * volts / watts : Double.POSITIVE_INFINITY;
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Formats internal energy units (kJ) as joules with an SI prefix, e.g. {@code "450 kJ"},
     * or in the energy unit matching the player's chosen power unit ({@link DisplayUnits}).
     */
    public static String formatEnergy(double units) {
        return DisplayUnits.energy(toJoules(units));
    }

    /** Formats internal energy units (kJ) as kilowatt-hours, e.g. {@code "13.9 kWh"}. */
    public static String formatKwh(double units) {
        if (DisplayUnits.powerUnit() != DisplayUnits.Power.WATTS) return formatEnergy(units);
        return formatSi(toJoules(units) / JOULES_PER_KWH * 1000.0, "Wh");
    }

    /** Formats watts with an SI prefix in the player's chosen power unit, e.g. {@code "20.0 kW"}. */
    public static String formatPower(double watts) {
        return DisplayUnits.power(watts);
    }

    /** Formats a kJ/tick rate as power, e.g. {@code 1.0 → "20.0 kW"}. */
    public static String formatPowerPerTick(double unitsPerTick) {
        return formatPower(toWatts(unitsPerTick));
    }

    public static String formatVoltage(double volts) {
        return formatSi(volts, "V");
    }

    public static String formatCurrent(double amps) {
        return formatSi(amps, "A");
    }

    public static String formatResistance(double ohms) {
        return Double.isInfinite(ohms) ? "∞ Ω" : formatSi(ohms, "Ω");
    }

    public static String formatCapacitance(double farads) {
        return formatSi(farads, "F");
    }

    /** Formats a duration in seconds as {@code "12.5 s"}, {@code "3 m 20 s"} or {@code "2 h 05 m"}. */
    public static String formatDuration(double seconds) {
        if (Double.isInfinite(seconds) || Double.isNaN(seconds)) return "∞";
        if (seconds < 60.0)   return String.format(Locale.ROOT, "%.1f s", seconds);
        long s = Math.round(seconds);
        if (s < 3600)         return String.format(Locale.ROOT, "%d m %02d s", s / 60, s % 60);
        return String.format(Locale.ROOT, "%d h %02d m", s / 3600, (s % 3600) / 60);
    }

    private static final String[] PREFIXES = {"n", "µ", "m", "", "k", "M", "G", "T", "P"};
    private static final int UNITY_INDEX = 3;

    /**
     * Formats {@code value} with an SI prefix and three significant digits,
     * e.g. {@code formatSi(20000, "W") → "20.0 kW"}, {@code formatSi(0.05, "A") → "50.0 mA"}.
     */
    public static String formatSi(double value, String unit) {
        if (value == 0.0 || Double.isNaN(value)) return "0 " + unit;
        double abs = Math.abs(value);
        int exp = (int) Math.floor(Math.log10(abs) / 3.0);
        int idx = Math.clamp(exp + UNITY_INDEX, 0, PREFIXES.length - 1);
        double scaled = value / Math.pow(1000.0, idx - UNITY_INDEX);

        // Rounding can push 999.95 up to 1000 — step to the next prefix.
        if (Math.abs(scaled) >= 999.5 && idx < PREFIXES.length - 1) {
            idx++;
            scaled /= 1000.0;
        }

        double a = Math.abs(scaled);
        String fmt = a >= 100.0 ? "%.0f" : a >= 10.0 ? "%.1f" : "%.2f";
        return String.format(Locale.ROOT, fmt, scaled) + " " + PREFIXES[idx] + unit;
    }
}
