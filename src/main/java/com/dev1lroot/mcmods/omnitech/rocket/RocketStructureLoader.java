/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.rocket;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads rocket multiblock templates from {@code data/omnitech/rocket_structure/<id>.json}.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "controller": "omnitech:rocket_controller",
 *   "key": {
 *     "C": "omnitech:rocket_controller",
 *     "P": "omnitech:titanium_block",
 *     "F": "omnitech:steel_block"
 *   },
 *   "layers": [
 *     ["  F  ", "  F  ", "FFCFF", "  F  ", "  F  "],
 *     ["     ", " PPP ", " PPP ", " PPP ", "     "]
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code layers} stacks bottom-to-top; each layer is a list of rows along
 * world Z, each row a string of one-character symbols along world X. A blank
 * space means "don't care" (any block, including air) — the mechanism that
 * lets a template describe fins, tapering, or any other non-cuboid shape
 * instead of a plain box. The block mapped to {@code "controller"} must
 * appear in the pattern exactly once; that cell becomes the anchor lined up
 * with the placed controller block at match time.
 */
public class RocketStructureLoader {
    private static final Gson GSON = new Gson();
    private static final String FOLDER = "rocket_structure";
    private static final Map<String, RocketStructureDef> STRUCTURES = new ConcurrentHashMap<>();

    public static void loadStructures(ResourceManager manager) {
        STRUCTURES.clear();

        try {
            Map<Identifier, Resource> resources = manager.listResources(
                    FOLDER, id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {

                    RocketStructureDef def = parse(id, GSON.fromJson(reader, JsonObject.class));
                    STRUCTURES.put(def.getId(), def);
                    OmniTech.LOGGER.info("Loaded rocket structure: {} ({}x{}x{})",
                            def.getId(), def.getWidth(), def.getHeight(), def.getDepth());

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load rocket structure: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan rocket structures", e);
        }

        OmniTech.LOGGER.info("Loaded {} rocket structures", STRUCTURES.size());
    }

    private static RocketStructureDef parse(Identifier id, JsonObject json) {
        Identifier controllerId = Identifier.parse(json.get("controller").getAsString());

        Map<Character, Identifier> key = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("key").entrySet()) {
            String symbol = e.getKey();
            if (symbol.length() != 1)
                throw new IllegalArgumentException("key symbols must be a single character: '" + symbol + "'");
            key.put(symbol.charAt(0), Identifier.parse(e.getValue().getAsString()));
        }

        JsonArray layersArr = json.getAsJsonArray("layers");
        List<List<String>> layers = new ArrayList<>();
        int width = -1, depth = -1;
        for (JsonElement layerEl : layersArr) {
            JsonArray rowsArr = layerEl.getAsJsonArray();
            List<String> rows = new ArrayList<>();
            for (JsonElement rowEl : rowsArr) rows.add(rowEl.getAsString());

            if (depth == -1) depth = rows.size();
            else if (rows.size() != depth)
                throw new IllegalArgumentException("every layer must have the same number of rows");

            for (String row : rows) {
                if (width == -1) width = row.length();
                else if (row.length() != width)
                    throw new IllegalArgumentException("every row must have the same length");
            }
            layers.add(rows);
        }
        int height = layers.size();

        Integer anchorX = null, anchorY = null, anchorZ = null;
        for (int y = 0; y < height; y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c == ' ') continue;

                    Identifier blockId = key.get(c);
                    if (blockId == null)
                        throw new IllegalArgumentException("symbol '" + c + "' has no entry in \"key\"");

                    if (blockId.equals(controllerId)) {
                        if (anchorX != null)
                            throw new IllegalArgumentException("controller block must appear exactly once in the pattern");
                        anchorX = x; anchorY = y; anchorZ = z;
                    }
                }
            }
        }
        if (anchorX == null)
            throw new IllegalArgumentException("controller block '" + controllerId + "' not found in pattern");

        String structureId = id.getPath().replace(FOLDER + "/", "").replace(".json", "");
        return new RocketStructureDef(structureId, controllerId, key, layers, width, depth, height,
                anchorX, anchorY, anchorZ);
    }

    /** Loaded structures whose controller symbol resolves to the given block, in load order. */
    public static List<RocketStructureDef> forController(Block block) {
        List<RocketStructureDef> result = new ArrayList<>();
        for (RocketStructureDef def : STRUCTURES.values()) {
            if (def.hasController(block)) result.add(def);
        }
        return result;
    }

    public static Collection<RocketStructureDef> all() { return STRUCTURES.values(); }
}
