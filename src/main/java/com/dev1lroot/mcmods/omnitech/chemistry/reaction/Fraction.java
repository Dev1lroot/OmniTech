/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

/** Exact rational arithmetic, used so equation-balancing never accumulates floating-point drift. */
public final class Fraction {

    public static final Fraction ZERO = new Fraction(0, 1);
    public static final Fraction ONE = new Fraction(1, 1);

    public final long num;
    public final long den;

    private Fraction(long num, long den) {
        if (den == 0) throw new ArithmeticException("Zero denominator");
        if (den < 0) { num = -num; den = -den; }
        long g = gcd(Math.abs(num), den);
        this.num = g == 0 ? 0 : num / g;
        this.den = g == 0 ? 1 : den / g;
    }

    public static Fraction of(long num, long den) { return new Fraction(num, den); }
    public static Fraction of(long whole) { return new Fraction(whole, 1); }

    public Fraction add(Fraction o) { return new Fraction(num * o.den + o.num * den, den * o.den); }
    public Fraction sub(Fraction o) { return new Fraction(num * o.den - o.num * den, den * o.den); }
    public Fraction mul(Fraction o) { return new Fraction(num * o.num, den * o.den); }
    public Fraction div(Fraction o) { return new Fraction(num * o.den, den * o.num); }
    public Fraction negate() { return new Fraction(-num, den); }
    public boolean isZero() { return num == 0; }

    private static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }

    @Override
    public String toString() { return den == 1 ? Long.toString(num) : num + "/" + den; }
}
