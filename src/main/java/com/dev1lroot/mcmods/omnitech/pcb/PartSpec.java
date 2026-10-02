/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Footprint and electrical model of a component that can be soldered onto a board.
 *
 * <p>Pin and body offsets are relative to the part's centre cell at rotation 0.
 * Two-lead parts span three cells (pin, body, pin); transistors are SOT-23 style:
 * collector on one side, base and emitter on the other, body in between, so every
 * leg can be reached by a trace from outside.
 *
 * @param value     ohms for resistors, farads for capacitors, breakdown volts for zeners;
 *                  NaN for a resistor whose bands are not painted yet
 * @param tolerance ±percent manufacturing spread (resistors: from the tolerance band)
 */
public record PartSpec(String itemId, Kind kind, double value, double tolerance, String[] pinNames,
                       int[][] pins, int[][] body) {

    /** Spread of parts that have no tolerance marking (capacitors, semiconductors). */
    public static final double DEFAULT_TOLERANCE = 3.0;

    public static final String RESISTOR = "omnitech:resistor";

    private PartSpec(String itemId, Kind kind, double value, String[] pinNames, int[][] pins, int[][] body) {
        this(itemId, kind, value, DEFAULT_TOLERANCE, pinNames, pins, body);
    }

    /** This spec with a concrete value — a colour-coded resistor. */
    public PartSpec withValue(double v, double tol) {
        return new PartSpec(itemId, kind, v, tol, pinNames, pins, body);
    }

    public enum Kind { RESISTOR, CAPACITOR, DIODE, ZENER, NPN, PNP }

    private static final int[][] TWO_PIN  = {{-1, 0}, {1, 0}};
    private static final int[][] TWO_BODY = {{0, 0}};
    private static final int[][] SOT_PINS = {{0, -1}, {-1, 1}, {1, 1}}; // C, B, E
    private static final int[][] SOT_BODY = {{-1, 0}, {0, 0}, {1, 0}};

    private static final Map<String, PartSpec> SPECS = new LinkedHashMap<>();

    static {
        // One resistor item; its value comes from the painted colour bands (see ResistorCode)
        add(new PartSpec(RESISTOR, Kind.RESISTOR, Double.NaN, new String[]{"1", "2"}, TWO_PIN, TWO_BODY));
        add(new PartSpec("omnitech:capacitor",        Kind.CAPACITOR, 100e-6,  new String[]{"+", "-"}, TWO_PIN, TWO_BODY));
        add(new PartSpec("omnitech:capacitor_1000uf", Kind.CAPACITOR, 1000e-6, new String[]{"+", "-"}, TWO_PIN, TWO_BODY));
        add(new PartSpec("omnitech:diode",            Kind.DIODE, 0,  new String[]{"A", "K"}, TWO_PIN, TWO_BODY));
        add(new PartSpec("omnitech:zener_diode",      Kind.ZENER, 5.1, new String[]{"A", "K"}, TWO_PIN, TWO_BODY));
        add(new PartSpec("omnitech:npn_transistor",   Kind.NPN, 0, new String[]{"C", "B", "E"}, SOT_PINS, SOT_BODY));
        add(new PartSpec("omnitech:pnp_transistor",   Kind.PNP, 0, new String[]{"C", "B", "E"}, SOT_PINS, SOT_BODY));
    }

    private static void add(PartSpec s) { SPECS.put(s.itemId, s); }

    public static Optional<PartSpec> of(String itemId) { return Optional.ofNullable(SPECS.get(itemId)); }

    public static Map<String, PartSpec> all() { return SPECS; }

    /** Rotates a centre-relative offset by {@code rot} quarter turns clockwise. */
    public static int[] rotate(int dx, int dy, int rot) {
        for (int i = 0; i < (rot & 3); i++) {
            int t = dx;
            dx = -dy;
            dy = t;
        }
        return new int[]{dx, dy};
    }

    /** Human-readable value, e.g. "4.7 kΩ", "100 µF". */
    public String valueLabel() {
        return switch (kind) {
            case RESISTOR -> Double.isNaN(value) ? "" : ResistorCode.format(value) + " " + ResistorCode.formatTolerance(tolerance);
            case CAPACITOR -> trim(value * 1e6) + " µF";
            case ZENER -> trim(value) + " V";
            default -> "";
        };
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
