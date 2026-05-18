/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

public enum LogicGate {
    AND("and",         new int[]{0, 0, 0, 1}),
    OR("or",           new int[]{0, 1, 1, 1}),
    NAND("nand",       new int[]{1, 1, 1, 0}),
    NOR("nor",         new int[]{1, 0, 0, 0}),
    XOR("xor",         new int[]{0, 1, 1, 0}),
    XNOR("xnor",       new int[]{1, 0, 0, 1}),
    BUFFER_A("buffer_a", new int[]{0, 0, 1, 1}),
    NOT_A("not_a",     new int[]{1, 1, 0, 0}),
    BUFFER_B("buffer_b", new int[]{0, 1, 0, 1}),
    NOT_B("not_b",     new int[]{1, 0, 1, 0}),
    ALWAYS_ON("always_on",   new int[]{1, 1, 1, 1}),
    ALWAYS_OFF("always_off", new int[]{0, 0, 0, 0}),
    IMPLY("imply",           new int[]{1, 1, 0, 1}),  // A→B: false only when A=1,B=0
    NIMPLY("nimply",         new int[]{0, 0, 1, 0});   // NOT(A→B): true only when A=1,B=0

    public final String id;
    // Output values for canonical inputs: A=0/B=0, A=0/B=1, A=1/B=0, A=1/B=1
    private final int[] pattern;

    LogicGate(String id, int[] pattern) {
        this.id = id;
        this.pattern = pattern;
    }

    /** Evaluates the gate output for the given input pair. */
    public boolean evaluate(boolean a, boolean b) {
        return pattern[(a ? 2 : 0) | (b ? 1 : 0)] != 0;
    }

    /** Returns the gate whose id matches, or null if not found. */
    public static @Nullable LogicGate byId(String id) {
        for (LogicGate g : values()) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }

    /**
     * Decodes 12 packed bits into a 4×3 truth table and matches it against
     * all known gates. Bits are laid out row-major: bit (r*3+c) is row r,
     * column c, where c=0→A, c=1→B, c=2→Out.
     *
     * Returns null when the A/B columns do not cover all four input combinations
     * exactly once (i.e. the table is ambiguous or incomplete), or when no
     * known gate matches the output column.
     */
    public static LogicGate fromBits(int bits) {
        int[] canonicalOut = new int[4];
        boolean[] seen = new boolean[4];
        for (int r = 0; r < 4; r++) {
            int a   = (bits >> (r * 3))     & 1;
            int b   = (bits >> (r * 3 + 1)) & 1;
            int out = (bits >> (r * 3 + 2)) & 1;
            int key = (a << 1) | b;
            if (seen[key]) return null;
            seen[key] = true;
            canonicalOut[key] = out;
        }
        for (boolean s : seen) if (!s) return null;

        for (LogicGate gate : values()) {
            if (Arrays.equals(gate.pattern, canonicalOut)) return gate;
        }
        return null;
    }
}
