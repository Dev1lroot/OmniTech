/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.sim;

import com.dev1lroot.mcmods.omnitech.pcb.CircuitGraph;
import com.dev1lroot.mcmods.omnitech.pcb.PartSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wires a {@link CircuitGraph} into a {@link TestBench}, simulates it and grades the
 * probed signal. Nets that no device or instrument touches are dropped first, so
 * stray copper costs nothing.
 */
public final class TestBenchRunner {

    /** One line of the report: a lang key plus pre-formatted arguments. */
    public record Note(boolean ok, String key, String... args) {}

    /**
     * @param trace  probed voltage, downsampled for the scope view (empty when not run)
     */
    public record Outcome(TestBench bench, boolean applicable, boolean passed,
                          List<Note> notes, float[] trace) {}

    public static final int TRACE_POINTS = 160;

    private TestBenchRunner() {}

    public static Outcome run(CircuitGraph g, TestBench bench) {
        List<Note> notes = new ArrayList<>();
        for (String t : bench.terminals()) {
            if (!g.terminals().containsKey(t)) {
                notes.add(new Note(false, "pcb.omnitech.test.missing_terminal", t));
            }
        }
        if (!notes.isEmpty()) return new Outcome(bench, false, false, notes, new float[0]);
        if (g.devices().isEmpty()) {
            notes.add(new Note(false, "pcb.omnitech.test.no_parts"));
            return new Outcome(bench, true, false, notes, new float[0]);
        }

        // Compact node numbering: only nets something is attached to
        Map<Integer, Integer> node = new HashMap<>();
        for (CircuitGraph.Device d : g.devices()) for (int n : d.nets()) node.putIfAbsent(n, node.size());
        for (String t : bench.terminals()) node.putIfAbsent(g.terminals().get(t), node.size());

        int groundNet = g.terminals().getOrDefault("GND", g.terminals().get(bench.probeMinus()));
        CircuitSimulator sim = new CircuitSimulator(node.size(), node.get(groundNet));

        for (int idx = 0; idx < g.devices().size(); idx++) {
            CircuitGraph.Device d = g.devices().get(idx);
            int[] n = new int[d.nets().length];
            for (int i = 0; i < n.length; i++) n[i] = node.get(d.nets()[i]);
            PartSpec s = d.spec();
            // Real parts are never identical: a fixed spread within each part's tolerance
            // (resistors: their tolerance band) lets symmetric circuits leave their
            // balance point, and makes 1 % parts behave more predictably than 20 % ones
            double tol = 1 + s.tolerance() / 100.0 * ((((idx * 7919) % 7) - 3) / 3.0);
            switch (s.kind()) {
                case RESISTOR  -> sim.resistor(n[0], n[1], s.value() * tol);
                case CAPACITOR -> sim.capacitor(n[0], n[1], s.value() * tol);
                case DIODE     -> sim.diode(n[0], n[1]);
                case ZENER     -> sim.zener(n[0], n[1], s.value());
                case NPN       -> sim.bjt(n[0], n[1], n[2], false);
                case PNP       -> sim.bjt(n[0], n[1], n[2], true);
            }
        }

        for (TestBench.Source src : bench.sources()) {
            int p = node.get(g.terminals().get(src.plus())), m = node.get(g.terminals().get(src.minus()));
            if (p == m) {
                notes.add(new Note(false, "pcb.omnitech.test.shorted", src.plus(), src.minus()));
                return new Outcome(bench, true, false, notes, new float[0]);
            }
            sim.source(p, m, src.wave());
        }
        for (TestBench.Load l : bench.loads()) {
            int a = node.get(g.terminals().get(l.a())), b = node.get(g.terminals().get(l.b()));
            if (a != b) sim.resistor(a, b, l.ohms());
        }

        int pp = node.get(g.terminals().get(bench.probePlus()));
        int pm = node.get(g.terminals().get(bench.probeMinus()));
        double[] v;
        double[][] currents;
        try {
            CircuitSimulator.Result r = sim.run(bench.duration(), bench.step(), new int[][]{{pp, pm}});
            v = r.probes()[0];
            currents = r.sources();
        } catch (CircuitSimulator.SimulationException e) {
            notes.add(new Note(false, "pcb.omnitech.test.diverged"));
            return new Outcome(bench, true, false, notes, new float[0]);
        }

        boolean pass = true;
        for (TestBench.Check c : bench.checks()) {
            Note n = c.type().equals("balance")
                    ? balance(c, currents[(int) c.source()], bench.step())
                    : check(c, v, bench.step());
            notes.add(n);
            pass &= n.ok();
        }
        return new Outcome(bench, true, pass, notes, downsample(v));
    }

    /** First passing bench among those whose terminals the board provides, or null. */
    public static Outcome firstPass(List<Outcome> outcomes) {
        for (Outcome o : outcomes) if (o.passed()) return o;
        return null;
    }

    private static Note check(TestBench.Check c, double[] v, double step) {
        int a = Math.max(0, (int) (c.from() / step)), b = Math.min(v.length, (int) Math.ceil(c.to() / step));
        if (b <= a) return new Note(false, "pcb.omnitech.check.window");
        double sum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = a; i < b; i++) {
            sum += v[i];
            lo = Math.min(lo, v[i]);
            hi = Math.max(hi, v[i]);
        }
        String window = f(c.from()) + "–" + f(c.to());
        return switch (c.type()) {
            case "mean" -> {
                double mean = sum / (b - a);
                yield new Note(mean >= c.min() && mean <= c.max(), "pcb.omnitech.check.mean",
                        window, f(mean), range(c));
            }
            case "ripple" -> new Note(hi - lo <= c.max(), "pcb.omnitech.check.ripple",
                    window, f(hi - lo), f(c.max()));
            case "swing" -> new Note(hi - lo >= c.min(), "pcb.omnitech.check.swing",
                    window, f(hi - lo), f(c.min()));
            case "transitions" -> {
                // Schmitt-style counting around the mid level, 10 % hysteresis
                double mid = (hi + lo) / 2, hyst = (hi - lo) * 0.1;
                int count = 0;
                boolean high = v[a] > mid;
                for (int i = a; i < b; i++) {
                    if (high && v[i] < mid - hyst) { high = false; count++; }
                    else if (!high && v[i] > mid + hyst) { high = true; count++; }
                }
                if (hi - lo < 0.5) count = 0; // noise is not oscillation
                yield new Note(count >= c.min(), "pcb.omnitech.check.transitions",
                        window, String.valueOf(count), f(c.min()));
            }
            default -> new Note(false, "pcb.omnitech.check.unknown", c.type());
        };
    }

    /**
     * Charge drawn on the positive vs the negative half of an AC source. A bridge
     * draws evenly; a half-wave rectifier pulls DC through the transformer.
     */
    private static Note balance(TestBench.Check c, double[] i, double step) {
        int a = Math.max(0, (int) (c.from() / step)), b = Math.min(i.length, (int) Math.ceil(c.to() / step));
        double pos = 0, neg = 0;
        for (int k = a; k < b; k++) {
            if (i[k] > 0) pos += i[k];
            else neg -= i[k];
        }
        double imbalance = pos + neg < 1e-9 ? 1 : Math.abs(pos - neg) / (pos + neg);
        return new Note(imbalance <= c.max(), "pcb.omnitech.check.balance",
                f(c.from()) + "–" + f(c.to()), f(imbalance * 100), f(c.max() * 100));
    }

    private static String range(TestBench.Check c) {
        if (Double.isInfinite(c.min())) return "≤ " + f(c.max());
        if (Double.isInfinite(c.max())) return "≥ " + f(c.min());
        return f(c.min()) + "–" + f(c.max());
    }

    private static String f(double d) {
        return String.format(Locale.ROOT, Math.abs(d) >= 100 || d == Math.rint(d) ? "%.0f" : "%.2f", d);
    }

    private static float[] downsample(double[] v) {
        int n = Math.min(TRACE_POINTS, v.length);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            // keep the extreme of each bucket so fast edges and ripple stay visible
            int s = (int) ((long) i * v.length / n), e = (int) ((long) (i + 1) * v.length / n);
            double best = v[s];
            for (int k = s; k < e; k++) if (Math.abs(v[k] - v[s]) > Math.abs(best - v[s])) best = v[k];
            out[i] = (float) best;
        }
        return out;
    }
}
