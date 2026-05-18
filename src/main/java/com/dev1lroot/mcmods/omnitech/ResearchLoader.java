/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.items.BlueprintItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads research definitions from {@code data/omnitech/research/*.json} at startup.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "research":     "d_flip_flop",
 *   "display_name": "D Flip-Flop",
 *   "inputs": [
 *     {"item": "minecraft:paper", "count": 1},
 *     {"item": "omnitech:gate_template_nand", "count": 4},
 *     {"item": "omnitech:blueprint", "count": 1, "research_tag": "d_flip_flop"}
 *   ],
 *   "wins_required": 4
 * }
 * }</pre>
 * Inputs with "research_tag" must be Blueprint items carrying that tag.
 * Blueprint inputs are never consumed on research completion.
 */
public class ResearchLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DATA_PATH = "data/omnitech/research";

    private static final Map<String, ResearchDefinition> RESEARCH = new LinkedHashMap<>();

    // ── Public types ─────────────────────────────────────────────────────────

    public record InputRequirement(String itemId, int count, @Nullable String researchTag) {
        /** Blueprint inputs are never consumed when research completes. */
        public boolean isBlueprint() { return researchTag != null; }
    }

    public record ResearchDefinition(
            String id,
            String displayName,
            List<InputRequirement> inputs,
            int winsRequired) {}

    // ── Loading ──────────────────────────────────────────────────────────────

    public static void loadAll() {
        RESEARCH.clear();

        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        int[] count = {0};
        contents.visitContent(DATA_PATH, (path, resource) -> {
            if (!path.endsWith(".json")) return;
            String remainder = path.substring(DATA_PATH.length() + 1);
            if (remainder.contains("/")) return;

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                String id          = json.get("research").getAsString();
                String displayName = json.has("display_name")
                        ? json.get("display_name").getAsString()
                        : prettify(id);
                int winsRequired   = json.has("wins_required")
                        ? json.get("wins_required").getAsInt()
                        : 4;

                List<InputRequirement> inputs = new ArrayList<>();
                JsonArray arr = json.getAsJsonArray("inputs");
                for (var el : arr) {
                    JsonObject inp  = el.getAsJsonObject();
                    String item     = inp.get("item").getAsString();
                    int    cnt      = inp.has("count") ? inp.get("count").getAsInt() : 1;
                    String resTag   = inp.has("research_tag")
                            ? inp.get("research_tag").getAsString() : null;
                    inputs.add(new InputRequirement(item, cnt, resTag));
                }

                RESEARCH.put(id, new ResearchDefinition(id, displayName, inputs, winsRequired));
                count[0]++;
                OmniTech.LOGGER.debug("[ResearchLoader] Loaded: {} (wins={})", id, winsRequired);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[ResearchLoader] Failed to parse '{}': {}", path, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[ResearchLoader] {} research definitions loaded", count[0]);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public static Optional<ResearchDefinition> find(String id) {
        return Optional.ofNullable(RESEARCH.get(id));
    }

    /**
     * Returns the first research definition whose input requirements are fully
     * satisfied by the items in the provided list (slots 0-8).
     */
    public static Optional<ResearchDefinition> findMatch(List<ItemStack> slots) {
        for (ResearchDefinition res : RESEARCH.values()) {
            if (satisfies(res, slots)) return Optional.of(res);
        }
        return Optional.empty();
    }

    /**
     * Consumes (shrinks) the non-blueprint inputs from the slot list.
     * Blueprint inputs are left untouched.
     */
    public static void consumeNonBlueprints(List<ItemStack> slots, ResearchDefinition res) {
        for (InputRequirement req : res.inputs()) {
            if (req.isBlueprint()) continue;
            int needed = req.count();
            for (ItemStack stack : slots) {
                if (needed <= 0) break;
                if (!matchesItem(stack, req)) continue;
                int take = Math.min(stack.getCount(), needed);
                stack.shrink(take);
                needed -= take;
            }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static boolean satisfies(ResearchDefinition res, List<ItemStack> slots) {
        int[] counts = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) counts[i] = slots.get(i).getCount();

        for (InputRequirement req : res.inputs()) {
            int needed = req.count();
            for (int i = 0; i < slots.size() && needed > 0; i++) {
                if (!matchesItem(slots.get(i), req)) continue;
                int take = Math.min(counts[i], needed);
                counts[i] -= take;
                needed -= take;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    private static boolean matchesItem(ItemStack stack, InputRequirement req) {
        if (stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!req.itemId().equals(id.toString())) return false;
        if (req.researchTag() != null) {
            String tag = stack.get(OmniTechDataComponents.RESEARCH_NAME.get());
            return req.researchTag().equals(tag);
        }
        return true;
    }

    private static String prettify(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
