/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resistor colour code (IEC 60062), read from the body's bands left to right.
 *
 * <ul>
 *   <li>3 bands: digit, digit, multiplier — ±20 %</li>
 *   <li>4 bands: digit, digit, multiplier, tolerance</li>
 *   <li>5 bands: digit, digit, digit, multiplier, tolerance</li>
 *   <li>6 bands: as 5, plus temperature coefficient</li>
 * </ul>
 * Pure Java so the circuit simulator and its tests can decode values without Minecraft.
 */
public final class ResistorCode {

    /** One band colour. {@code tolerance} / {@code tempco} are NaN / 0 where the colour has none. */
    public enum Band {
        BLACK (0,  0,  Double.NaN, 250, 0x1A1A1A),
        BROWN (1,  1,  1.0,        100, 0x6B3A12),
        RED   (2,  2,  2.0,        50,  0xC0261C),
        ORANGE(3,  3,  Double.NaN, 15,  0xE07B1A),
        YELLOW(4,  4,  Double.NaN, 25,  0xE8D020),
        GREEN (5,  5,  0.5,        20,  0x2E9E3A),
        BLUE  (6,  6,  0.25,       10,  0x2A5BD8),
        VIOLET(7,  7,  0.1,        5,   0x8A3FC0),
        GREY  (8,  8,  0.05,       1,   0x8A8A8A),
        WHITE (9,  9,  Double.NaN, 0,   0xF0F0F0),
        GOLD  (-1, -1, 5.0,        0,   0xD4AF37),
        SILVER(-1, -2, 10.0,       0,   0xC8C8D0);

        public final int digit;          // -1: not a digit colour
        public final int exponent;       // as a multiplier: ×10^exponent
        public final double tolerance;   // as a tolerance band, ±percent
        public final int tempco;         // as a 6th band, ppm/K (0 = not a tempco colour)
        public final int rgb;

        Band(int digit, int exponent, double tolerance, int tempco, int rgb) {
            this.digit = digit;
            this.exponent = exponent;
            this.tolerance = tolerance;
            this.tempco = tempco;
            this.rgb = rgb;
        }

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final int MIN_BANDS = 3, MAX_BANDS = 6;

    /** Carbon film (3–4 bands) is beige; metal film (5–6 bands) is light blue. */
    public static final int CARBON_BODY = 0xD9BE86;
    public static final int METAL_BODY  = 0x7FB2E0;
    /** A blank resistor, before any bands are painted. */
    public static final int BLANK_BODY  = 0xCFC2A6;

    /**
     * @param ohms      nominal resistance
     * @param tolerance ±percent
     * @param tempco    ppm/K, 0 when not specified
     */
    public record Value(double ohms, double tolerance, int tempco) {}

    private ResistorCode() {}

    public static int bodyColor(List<Band> bands) {
        if (bands.isEmpty()) return BLANK_BODY;
        return bands.size() >= 5 ? METAL_BODY : CARBON_BODY;
    }

    /** Decodes the bands, or returns null when they are not a valid code. */
    public static Value decode(List<Band> bands) {
        int n = bands.size();
        if (n < MIN_BANDS || n > MAX_BANDS) return null;
        int digits = n >= 5 ? 3 : 2;

        long mantissa = 0;
        for (int i = 0; i < digits; i++) {
            Band b = bands.get(i);
            if (b.digit < 0) return null;            // gold / silver are never digits
            if (i == 0 && b.digit == 0) return null; // no leading zero
            mantissa = mantissa * 10 + b.digit;
        }
        Band mult = bands.get(digits);
        double ohms = mantissa * Math.pow(10, mult.exponent);

        double tolerance = 20.0;
        if (n >= 4) {
            tolerance = bands.get(digits + 1).tolerance;
            if (Double.isNaN(tolerance)) return null;
        }
        int tempco = 0;
        if (n == 6) {
            tempco = bands.get(5).tempco;
            if (tempco == 0) return null;
        }
        return new Value(ohms, tolerance, tempco);
    }

    public static Value decodeCodes(List<Integer> codes) {
        return decode(fromCodes(codes));
    }

    public static List<Band> fromCodes(List<Integer> codes) {
        List<Band> out = new ArrayList<>();
        for (int c : codes) if (c >= 0 && c < Band.values().length) out.add(Band.values()[c]);
        return out;
    }

    public static List<Integer> toCodes(List<Band> bands) {
        List<Integer> out = new ArrayList<>();
        for (Band b : bands) out.add(b.ordinal());
        return out;
    }

    /** Shortest band list for a value at a tolerance, e.g. (4700, GOLD) → yellow violet red gold. */
    public static List<Band> encode(double ohms, Band toleranceBand) {
        int exp = (int) Math.floor(Math.log10(ohms)) - 1;
        long two = Math.round(ohms / Math.pow(10, exp));
        if (two >= 100) { two /= 10; exp++; }
        List<Band> out = new ArrayList<>();
        out.add(Band.values()[(int) (two / 10)]);
        out.add(Band.values()[(int) (two % 10)]);
        out.add(exp == -1 ? Band.GOLD : exp == -2 ? Band.SILVER : Band.values()[exp]);
        out.add(toleranceBand);
        return out;
    }

    /** "4.7 kΩ", "100 Ω", "2.2 MΩ". */
    public static String format(double ohms) {
        String unit = "";
        double v = ohms;
        if (ohms >= 1e6) { v = ohms / 1e6; unit = "M"; }
        else if (ohms >= 1e3) { v = ohms / 1e3; unit = "k"; }
        String num = v == Math.rint(v) ? String.valueOf((long) v)
                : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        return num + " " + unit + "Ω";
    }

    public static String formatTolerance(double pct) {
        return "±" + (pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct)) + "%";
    }
}
