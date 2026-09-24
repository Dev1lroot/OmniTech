/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Balances a chemical equation by solving conservation-of-atoms as a homogeneous linear system
 * (reactant coefficients positive, product coefficients negative, solved exactly with
 * {@link Fraction} so there's no floating-point drift) — the standard algebraic method, not a
 * lookup table. Any reactant/product formula list that actually conserves every element has a
 * unique minimal positive-integer solution up to scale; this returns that solution.
 */
public final class EquationBalancer {

    private EquationBalancer() {}

    public static class BalanceException extends RuntimeException {
        public BalanceException(String message) { super(message); }
    }

    /** Coefficients in order: all of {@code reactants}, then all of {@code products}. */
    public static int[] balance(List<Formula> reactants, List<Formula> products) {
        List<Formula> all = new ArrayList<>(reactants);
        all.addAll(products);
        int n = all.size();

        LinkedHashSet<String> elementSet = new LinkedHashSet<>();
        for (Formula f : all) elementSet.addAll(f.elements());
        List<String> elements = new ArrayList<>(elementSet);
        int m = elements.size();

        Fraction[][] mat = new Fraction[m][n];
        for (int r = 0; r < m; r++) {
            String el = elements.get(r);
            for (int c = 0; c < n; c++) {
                int sign = c < reactants.size() ? 1 : -1;
                mat[r][c] = Fraction.of((long) sign * all.get(c).count(el));
            }
        }

        int pivotRow = 0;
        int[] pivotColForRow = new int[m];
        Arrays.fill(pivotColForRow, -1);
        boolean[] colHasPivot = new boolean[n];
        for (int col = 0; col < n && pivotRow < m; col++) {
            int sel = -1;
            for (int r = pivotRow; r < m; r++) {
                if (!mat[r][col].isZero()) { sel = r; break; }
            }
            if (sel == -1) continue;
            Fraction[] tmp = mat[sel];
            mat[sel] = mat[pivotRow];
            mat[pivotRow] = tmp;

            Fraction pivotVal = mat[pivotRow][col];
            for (int c2 = 0; c2 < n; c2++) mat[pivotRow][c2] = mat[pivotRow][c2].div(pivotVal);
            for (int r = 0; r < m; r++) {
                if (r == pivotRow) continue;
                Fraction factor = mat[r][col];
                if (factor.isZero()) continue;
                for (int c2 = 0; c2 < n; c2++) mat[r][c2] = mat[r][c2].sub(factor.mul(mat[pivotRow][c2]));
            }
            pivotColForRow[pivotRow] = col;
            colHasPivot[col] = true;
            pivotRow++;
        }

        List<Integer> freeCols = new ArrayList<>();
        for (int c = 0; c < n; c++) if (!colHasPivot[c]) freeCols.add(c);
        if (freeCols.isEmpty()) {
            throw new BalanceException("Equation has only the trivial all-zero solution — not a real reaction");
        }

        Fraction[] x = new Fraction[n];
        Arrays.fill(x, Fraction.ZERO);
        x[freeCols.get(freeCols.size() - 1)] = Fraction.ONE;

        for (int r = pivotRow - 1; r >= 0; r--) {
            int pc = pivotColForRow[r];
            Fraction sum = Fraction.ZERO;
            for (int c = 0; c < n; c++) {
                if (c == pc || x[c].isZero()) continue;
                sum = sum.add(mat[r][c].mul(x[c]));
            }
            x[pc] = sum.negate();
        }

        long lcm = 1;
        for (Fraction f : x) lcm = lcm(lcm, f.den);
        long[] intVals = new long[n];
        for (int i = 0; i < n; i++) intVals[i] = x[i].num * (lcm / x[i].den);

        long g = 0;
        for (long v : intVals) g = gcd(g, Math.abs(v));
        if (g > 1) for (int i = 0; i < n; i++) intVals[i] /= g;

        boolean anyNegative = false, anyPositive = false;
        for (long v : intVals) {
            if (v < 0) anyNegative = true;
            if (v > 0) anyPositive = true;
        }
        if (anyNegative && anyPositive) {
            throw new BalanceException("Balanced solution has mixed-sign coefficients — atoms don't conserve as classified");
        }
        if (anyNegative) for (int i = 0; i < n; i++) intVals[i] = -intVals[i];
        for (long v : intVals) {
            if (v == 0) throw new BalanceException("A compound balanced to zero coefficient — atoms don't conserve as classified");
        }

        int[] result = new int[n];
        for (int i = 0; i < n; i++) result[i] = Math.toIntExact(intVals[i]);
        return result;
    }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
    private static long lcm(long a, long b) { return a == 0 ? b : b == 0 ? a : a / gcd(a, b) * b; }
}
