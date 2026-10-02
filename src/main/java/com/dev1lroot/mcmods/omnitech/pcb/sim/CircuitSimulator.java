/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Small transient circuit simulator (modified nodal analysis).
 *
 * <p>Each time step is solved with Newton–Raphson; capacitors use backward-Euler
 * companion models. Devices:
 * <ul>
 *   <li>resistor, capacitor</li>
 *   <li>diode — Shockley equation; zener adds an exponential reverse breakdown</li>
 *   <li>NPN / PNP — Ebers–Moll transport model</li>
 *   <li>independent voltage source with an arbitrary waveform</li>
 * </ul>
 * Every node has a tiny conductance to ground so floating copper never makes the
 * matrix singular. Pure Java, no Minecraft dependencies.
 */
public final class CircuitSimulator {

    public static final class SimulationException extends RuntimeException {
        public SimulationException(String msg) { super(msg); }
    }

    private static final double VT = 0.025852;     // thermal voltage at 300 K
    private static final double GMIN = 1e-9;
    private static final double MAX_STEP_V = 5.0;   // coarse Newton damping; junctions are limited separately

    // Diode: roughly a 1N400x — ~0.7 V at tens of mA
    private static final double D_IS = 1e-9, D_N = 1.8;
    // Zener breakdown: 1 mA at Vz, steepening above
    private static final double Z_IBV = 1e-3, Z_N = 1.0;
    // Small-signal BJT: β ≈ 100
    private static final double Q_IS = 1e-14, Q_BF = 100, Q_BR = 2, Q_CJ = 1e-6;

    private enum Type { R, C, D, Z, NPN, PNP, V }

    private record Element(Type type, int[] n, double value, DoubleUnaryOperator wave) {}

    private final int nodes;
    private final int ground;
    private final List<Element> elements = new ArrayList<>();
    private int sources = 0;

    /** Capacitor voltages at the previous accepted time point. */
    private double[] capPrev;
    /** Last limited junction voltages per element (diode: vd; BJT: vbe, vbc). */
    private double[][] junction;
    /** False while evaluating a residual: junctions use raw voltages and keep their state. */
    private boolean limiting = true;

    public CircuitSimulator(int nodes, int ground) {
        this.nodes = nodes;
        this.ground = ground;
    }

    public void resistor(int a, int b, double ohms)   { elements.add(new Element(Type.R, new int[]{a, b}, Math.max(ohms, 1e-3), null)); }
    public void capacitor(int a, int b, double farad) { elements.add(new Element(Type.C, new int[]{a, b}, farad, null)); }
    public void diode(int anode, int cathode)         { elements.add(new Element(Type.D, new int[]{anode, cathode}, 0, null)); }
    public void zener(int anode, int cathode, double vz) { elements.add(new Element(Type.Z, new int[]{anode, cathode}, vz, null)); }
    public void bjt(int c, int b, int e, boolean pnp) {
        elements.add(new Element(pnp ? Type.PNP : Type.NPN, new int[]{c, b, e}, 0, null));
        // Junction capacitances: without them a regenerative switch (multivibrator)
        // flips in zero time and the implicit step has no solution to converge to.
        // Far larger than a real die's picofarads — they stand in for parasitics on
        // the millisecond time scale the benches sample at.
        capacitor(b, e, Q_CJ);
        capacitor(b, c, Q_CJ);
    }

    /** Voltage source forcing V(plus) − V(minus) = wave(t). */
    public void source(int plus, int minus, DoubleUnaryOperator wave) {
        if (plus == minus) throw new SimulationException("source shorted");
        elements.add(new Element(Type.V, new int[]{plus, minus}, sources++, wave));
    }

    // ── Transient run ─────────────────────────────────────────────────────────

    /**
     * @param probes  {@code [probe][sample]} differential voltages
     * @param sources {@code [source][sample]} current delivered by each voltage source
     */
    public record Result(double[][] probes, double[][] sources) {}

    /**
     * Runs from t = 0 (everything discharged) to {@code duration}.
     *
     * @param probes pairs {plusNode, minusNode}; the differential voltage is recorded
     * @return one sample per step (sample 0 = t = step)
     */
    public Result run(double duration, double step, int[][] probes) {
        int steps = (int) Math.round(duration / step);
        double[][] out = new double[probes.length][steps];
        double[][] src = new double[sources][steps];
        double[] v = new double[nodes];
        capPrev = new double[elements.size()];
        junction = new double[elements.size()][2];

        for (int s = 0; s < steps; s++) {
            double t = (s + 1) * step;
            v = advance(v, t - step, step, 0);
            for (int p = 0; p < probes.length; p++) out[p][s] = v[probes[p][0]] - v[probes[p][1]];
            // the branch unknown is current into the + terminal; report current delivered
            for (int k = 0; k < sources; k++) src[k][s] = -lastSourceCurrents[k];
        }
        return new Result(out, src);
    }

    /** Advances one step, subdividing it when Newton fails to converge. */
    private double[] advance(double[] v, double t0, double dt, int depth) {
        double[] r = solveStep(v, t0 + dt, dt, depth == 0 && t0 == 0 ? 400 : 100);
        if (r != null) {
            commitCaps(r);
            return r;
        }
        if (depth >= 4) throw new SimulationException("did not converge at t=" + t0);
        double[] mid = advance(v, t0, dt / 4, depth + 1);
        for (int i = 1; i < 4; i++) mid = advance(mid, t0 + i * dt / 4, dt / 4, depth + 1);
        return mid;
    }

    private void commitCaps(double[] v) {
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (e.type == Type.C) capPrev[i] = v[e.n[0]] - v[e.n[1]];
        }
    }

    /** Source currents of the last accepted solution, reused as the next initial guess. */
    private double[] lastSourceCurrents;

    /**
     * Damped Newton–Raphson for one time point with a backtracking line search on the
     * true (unlimited) residual. The line search is what carries the solution across a
     * regenerative edge, where plain Newton just bounces around a fold.
     *
     * @return node voltages, or null when Newton fails
     */
    private double[] solveStep(double[] start, double t, double dt, int maxIter) {
        int vars = nodes - 1 + sources;
        if (lastSourceCurrents == null || lastSourceCurrents.length != sources) lastSourceCurrents = new double[sources];
        double[] x = new double[vars];
        for (int k = 0; k < nodes; k++) if (var(k) >= 0) x[var(k)] = start[k];
        System.arraycopy(lastSourceCurrents, 0, x, nodes - 1, sources);
        double res = residual(x, t, dt);

        for (int iter = 0; iter < maxIter; iter++) {
            double[][] a = new double[vars][vars];
            double[] b = new double[vars];
            limiting = true;
            stampAll(a, b, nodeVoltages(x), t, dt);
            double[] xn;
            try {
                xn = solve(a, b);
            } catch (SimulationException ex) {
                return null;
            }

            double maxDelta = 0;
            for (int i = 0; i < nodes - 1; i++) maxDelta = Math.max(maxDelta, Math.abs(xn[i] - x[i]));
            if (Double.isNaN(maxDelta)) return null;
            if (maxDelta < 1e-6) {
                System.arraycopy(xn, nodes - 1, lastSourceCurrents, 0, sources);
                return nodeVoltages(xn);
            }

            double alpha = Math.min(1, MAX_STEP_V / maxDelta);
            double[] cand = new double[vars];
            double r = res;
            for (int k = 0; k < 12; k++) {
                for (int i = 0; i < vars; i++) cand[i] = x[i] + alpha * (xn[i] - x[i]);
                r = residual(cand, t, dt);
                if (r < res * (1 - 1e-4 * alpha)) break;
                alpha /= 2;
            }
            x = cand.clone();
            res = r;
        }
        return null;
    }

    private double[] nodeVoltages(double[] x) {
        double[] v = new double[nodes];
        for (int k = 0; k < nodes; k++) v[k] = var(k) < 0 ? 0 : x[var(k)];
        return v;
    }

    /** Infinity norm of the KCL / source-equation mismatch at {@code x}, without limiting. */
    private double residual(double[] x, double t, double dt) {
        int vars = x.length;
        double[][] a = new double[vars][vars];
        double[] b = new double[vars];
        limiting = false;
        stampAll(a, b, nodeVoltages(x), t, dt);
        double worst = 0;
        for (int r = 0; r < vars; r++) {
            double s = -b[r];
            for (int c = 0; c < vars; c++) s += a[r][c] * x[c];
            worst = Math.max(worst, Math.abs(s));
        }
        return worst;
    }

    private int var(int node) {
        if (node == ground) return -1;
        return node < ground ? node : node - 1;
    }

    // ── Stamping ──────────────────────────────────────────────────────────────

    private void stampAll(double[][] a, double[] b, double[] v, double t, double dt) {
        for (int k = 0; k < nodes; k++) {
            int i = var(k);
            if (i >= 0) a[i][i] += GMIN;
        }
        for (int idx = 0; idx < elements.size(); idx++) {
            Element e = elements.get(idx);
            switch (e.type) {
                case R -> conductance(a, e.n[0], e.n[1], 1 / e.value);
                case C -> {
                    double g = e.value / dt;
                    conductance(a, e.n[0], e.n[1], g);
                    double ieq = g * capPrev[idx];
                    inject(b, e.n[0], ieq);
                    inject(b, e.n[1], -ieq);
                }
                case D, Z -> {
                    double raw = v[e.n[0]] - v[e.n[1]];
                    double vd = limiting ? pnjlim(raw, junction[idx][0], D_N * VT, D_IS) : raw;
                    if (limiting && e.type == Type.Z && raw < -e.value / 2) {
                        // limit the breakdown junction the same way, mirrored around −Vz
                        double vr = pnjlim(-raw - e.value, -junction[idx][0] - e.value, Z_N * VT, Z_IBV);
                        vd = -vr - e.value;
                    }
                    if (limiting) junction[idx][0] = vd;
                    double x = vd / (D_N * VT);
                    double id = D_IS * (limexp(x) - 1);
                    double g = D_IS * dlimexp(x) / (D_N * VT);
                    if (e.type == Type.Z) {
                        double xr = -(vd + e.value) / (Z_N * VT);
                        id -= Z_IBV * limexp(xr);
                        g += Z_IBV * dlimexp(xr) / (Z_N * VT);
                    }
                    g += GMIN;
                    id += GMIN * vd;
                    double c = id - g * vd; // I = c + g·(Va − Vk)
                    nonlinear(a, b, e.n, new double[]{c, -c}, new double[][]{{g, -g}, {-g, g}});
                }
                case NPN, PNP -> bjt(a, b, v, e.n, e.type == Type.PNP, junction[idx]);
                case V -> {
                    int s = nodes - 1 + (int) e.value;
                    int p = var(e.n[0]), m = var(e.n[1]);
                    if (p >= 0) { a[p][s] += 1; a[s][p] += 1; }
                    if (m >= 0) { a[m][s] -= 1; a[s][m] -= 1; }
                    b[s] = e.wave.applyAsDouble(t);
                }
            }
        }
    }

    private void bjt(double[][] a, double[] b, double[] v, int[] n, boolean pnp, double[] junc) {
        double sign = pnp ? -1 : 1;
        double vc = sign * v[n[0]], vb = sign * v[n[1]], ve = sign * v[n[2]];
        double vbe = limiting ? pnjlim(vb - ve, junc[0], VT, Q_IS) : vb - ve;
        double vbc = limiting ? pnjlim(vb - vc, junc[1], VT, Q_IS) : vb - vc;
        if (limiting) {
            junc[0] = vbe;
            junc[1] = vbc;
        }
        double xf = vbe / VT, xr = vbc / VT;
        double iF = Q_IS * (limexp(xf) - 1), iR = Q_IS * (limexp(xr) - 1);
        double gF = Q_IS * dlimexp(xf) / VT + GMIN, gR = Q_IS * dlimexp(xr) / VT + GMIN;

        double ic = iF - iR - iR / Q_BR;
        double ib = iF / Q_BF + iR / Q_BR;
        // Partial derivatives with respect to the junction voltages
        double icF = gF, icR = -gR * (1 + 1 / Q_BR);
        double ibF = gF / Q_BF, ibR = gR / Q_BR;
        // Constant part of the linearisation: I = c + ∂I/∂vbe·vbe + ∂I/∂vbc·vbc
        double cc = ic - icF * vbe - icR * vbc;
        double cb = ib - ibF * vbe - ibR * vbc;
        // vbe = Vb − Ve, vbc = Vb − Vc → node Jacobian over (C, B, E)
        double[] dIc = {-icR, icF + icR, -icF};
        double[] dIb = {-ibR, ibF + ibR, -ibF};
        double[] dIe = {-(dIc[0] + dIb[0]), -(dIc[1] + dIb[1]), -(dIc[2] + dIb[2])};
        // PNP: I(V) = −I_npn(−V) — the constant flips sign, the Jacobian does not
        nonlinear(a, b, n,
                new double[]{sign * cc, sign * cb, sign * -(cc + cb)},
                new double[][]{dIc, dIb, dIe});
    }

    /**
     * Linearised device: the current leaving node {@code n[k]} into the device is
     * {@code c[k] + Σ j[k][m]·V(n[m])}.
     */
    private void nonlinear(double[][] a, double[] b, int[] n, double[] c, double[][] j) {
        for (int k = 0; k < n.length; k++) {
            int row = var(n[k]);
            if (row < 0) continue;
            for (int m = 0; m < n.length; m++) {
                int col = var(n[m]);
                if (col >= 0) a[row][col] += j[k][m];
            }
            b[row] -= c[k];
        }
    }

    /**
     * SPICE junction-voltage limiting: above the critical voltage a p-n junction may
     * only move logarithmically per Newton iteration, which keeps the exponential
     * from overshooting when a transistor snaps on.
     */
    private static double pnjlim(double vnew, double vold, double vt, double is) {
        double vcrit = vt * Math.log(vt / (Math.sqrt(2) * is));
        if (vnew > vcrit && Math.abs(vnew - vold) > 2 * vt) {
            if (vold > 0) {
                double arg = 1 + (vnew - vold) / vt;
                return arg > 0 ? vold + vt * Math.log(arg) : vcrit;
            }
            return vt * Math.log(vnew / vt);
        }
        return vnew;
    }

    private void conductance(double[][] a, int n1, int n2, double g) {
        int i = var(n1), j = var(n2);
        if (i >= 0) a[i][i] += g;
        if (j >= 0) a[j][j] += g;
        if (i >= 0 && j >= 0) { a[i][j] -= g; a[j][i] -= g; }
    }

    /** Current {@code amps} injected into {@code node}. */
    private void inject(double[] b, int node, double amps) {
        int i = var(node);
        if (i >= 0) b[i] += amps;
    }

    private static final double EXP_LIM = 40;

    /** exp(x), continued linearly above {@link #EXP_LIM} so Newton can't overflow. */
    private static double limexp(double x) {
        return x < EXP_LIM ? Math.exp(x) : Math.exp(EXP_LIM) * (1 + x - EXP_LIM);
    }

    private static double dlimexp(double x) {
        return x < EXP_LIM ? Math.exp(x) : Math.exp(EXP_LIM);
    }

    /** Gaussian elimination with partial pivoting; solves in place. */
    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) piv = r;
            if (Math.abs(a[piv][c]) < 1e-18) throw new SimulationException("singular");
            double[] tr = a[c]; a[c] = a[piv]; a[piv] = tr;
            double tb = b[c]; b[c] = b[piv]; b[piv] = tb;
            for (int r = c + 1; r < n; r++) {
                double f = a[r][c] / a[c][c];
                if (f == 0) continue;
                for (int k = c; k < n; k++) a[r][k] -= f * a[c][k];
                b[r] -= f * b[c];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int k = r + 1; k < n; k++) s -= a[r][k] * x[k];
            x[r] = s / a[r][r];
        }
        return x;
    }
}
