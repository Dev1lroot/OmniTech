/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.mc;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.pcb.CircuitGraph;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.sim.TestBench;
import com.dev1lroot.mcmods.omnitech.pcb.sim.TestBenchRunner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads circuit test benches from {@code data/omnitech/circuit_test/*.json} (file
 * order = test order) on both sides, so the Soldering Station screen can run the
 * exact simulation the server uses to grade a board.
 */
public final class TestBenchLoader {

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "data/omnitech/circuit_test";
    private static final List<TestBench> BENCHES = new ArrayList<>();

    /** Recent results keyed by board + parts, so batch soldering simulates once. */
    private static final Map<Integer, List<TestBenchRunner.Outcome>> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Integer, List<TestBenchRunner.Outcome>> e) {
                    return size() > 32;
                }
            };

    private TestBenchLoader() {}

    public static void loadAll() {
        BENCHES.clear();
        TreeMap<String, TestBench> sorted = new TreeMap<>();
        ModList.get().getModFileById(OmniTech.MODID).getFile().getContents()
                .visitContent(DATA_PATH, (path, resource) -> {
                    if (!path.endsWith(".json")) return;
                    String id = path.substring(DATA_PATH.length() + 1).replace(".json", "");
                    if (id.contains("/")) return;
                    try (var reader = resource.bufferedReader()) {
                        sorted.put(id, TestBench.fromJson(id, GSON.fromJson(reader, JsonObject.class)));
                    } catch (Exception e) {
                        OmniTech.LOGGER.error("[TestBenchLoader] Failed to parse '{}': {}", path, e.getMessage());
                    }
                });
        BENCHES.addAll(sorted.values());
        OmniTech.LOGGER.info("[TestBenchLoader] {} circuit test benches loaded", BENCHES.size());
    }

    public static List<TestBench> all() { return BENCHES; }

    /** Runs every bench against the board (cached). */
    public static synchronized List<TestBenchRunner.Outcome> test(PcbDesign design, List<PlacedPart> parts) {
        int key = 31 * design.hashCode() + parts.hashCode();
        List<TestBenchRunner.Outcome> hit = CACHE.get(key);
        if (hit != null) return hit;
        CircuitGraph g = CircuitGraph.of(design, parts);
        List<TestBenchRunner.Outcome> out = new ArrayList<>();
        for (TestBench b : BENCHES) out.add(TestBenchRunner.run(g, b));
        CACHE.put(key, out);
        return out;
    }
}
