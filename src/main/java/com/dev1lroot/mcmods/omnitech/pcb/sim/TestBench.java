/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * A virtual test fixture ({@code data/omnitech/circuit_test/*.json}): instruments wired
 * to labelled terminals, a measured signal and pass criteria. A board that passes
 * becomes {@link #resultItem}.
 *
 * <pre>{@code
 * {
 *   "result": "omnitech:stabilizer_circuit",
 *   "terminals": ["AC1", "AC2", "OUT", "GND"],
 *   "duration": 0.2, "step": 0.0001,
 *   "sources": [{"plus": "AC1", "minus": "AC2", "sine": 12, "frequency": 50}],
 *   "loads":   [{"a": "OUT", "b": "GND", "ohms": 1000}],
 *   "probe":   {"plus": "OUT", "minus": "GND"},
 *   "checks":  [{"type": "mean", "from": 0.1, "min": 4.0, "max": 6.0}]
 * }
 * }</pre>
 * Source kinds: {@code "dc": volts}, {@code "sine": amplitude} + {@code frequency},
 * {@code "steps": [[t, volts], ...]}. Check types: {@code mean}, {@code ripple}
 * (max − min ≤ max), {@code swing} (max − min ≥ min), {@code transitions}
 * (mid-level crossings ≥ min), {@code balance} (charge drawn from AC source
 * {@code source} on its two half-waves differs by ≤ max, as a fraction).
 * {@code from}/{@code to} are seconds; {@code to}
 * defaults to the end of the run.
 */
public record TestBench(String id, String resultItem, List<String> terminals,
                        double duration, double step, List<Source> sources, List<Load> loads,
                        String probePlus, String probeMinus, List<Check> checks) {

    /** DC rails ramp up over this long, like a real supply, which also helps convergence. */
    private static final double RAMP = 0.002;

    public record Source(String plus, String minus, DoubleUnaryOperator wave) {}

    public record Load(String a, String b, double ohms) {}

    /** {@code source} is the source index for {@code balance} checks. */
    public record Check(String type, double from, double to, double min, double max, double source) {}

    public static TestBench fromJson(String id, JsonObject j) {
        List<String> terms = new ArrayList<>();
        for (JsonElement e : j.getAsJsonArray("terminals")) terms.add(e.getAsString());

        List<Source> sources = new ArrayList<>();
        for (JsonElement e : j.getAsJsonArray("sources")) {
            JsonObject s = e.getAsJsonObject();
            sources.add(new Source(s.get("plus").getAsString(), s.get("minus").getAsString(), wave(s)));
        }

        List<Load> loads = new ArrayList<>();
        if (j.has("loads")) {
            for (JsonElement e : j.getAsJsonArray("loads")) {
                JsonObject l = e.getAsJsonObject();
                loads.add(new Load(l.get("a").getAsString(), l.get("b").getAsString(), l.get("ohms").getAsDouble()));
            }
        }

        double duration = j.get("duration").getAsDouble();
        List<Check> checks = new ArrayList<>();
        for (JsonElement e : j.getAsJsonArray("checks")) {
            JsonObject c = e.getAsJsonObject();
            checks.add(new Check(c.get("type").getAsString(),
                    num(c, "from", 0), num(c, "to", duration),
                    num(c, "min", Double.NEGATIVE_INFINITY), num(c, "max", Double.POSITIVE_INFINITY),
                    num(c, "source", 0)));
        }

        JsonObject probe = j.getAsJsonObject("probe");
        return new TestBench(id, j.get("result").getAsString(), terms, duration,
                j.get("step").getAsDouble(), sources, loads,
                probe.get("plus").getAsString(), probe.get("minus").getAsString(), checks);
    }

    private static double num(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    private static DoubleUnaryOperator wave(JsonObject s) {
        if (s.has("sine")) {
            double amp = s.get("sine").getAsDouble(), f = s.get("frequency").getAsDouble();
            return t -> amp * Math.sin(2 * Math.PI * f * t);
        }
        if (s.has("steps")) {
            JsonArray arr = s.getAsJsonArray("steps");
            double[][] pts = new double[arr.size()][2];
            for (int i = 0; i < arr.size(); i++) {
                pts[i][0] = arr.get(i).getAsJsonArray().get(0).getAsDouble();
                pts[i][1] = arr.get(i).getAsJsonArray().get(1).getAsDouble();
            }
            return t -> {
                double v = 0, prev = 0;
                for (double[] p : pts) {
                    if (t < p[0]) break;
                    // short linear edge so the step isn't a numerical discontinuity
                    double edge = Math.min(1, (t - p[0]) / RAMP);
                    v = prev + (p[1] - prev) * edge;
                    prev = p[1];
                }
                return v;
            };
        }
        double dc = s.get("dc").getAsDouble();
        return t -> dc * Math.min(1, t / RAMP);
    }
}
