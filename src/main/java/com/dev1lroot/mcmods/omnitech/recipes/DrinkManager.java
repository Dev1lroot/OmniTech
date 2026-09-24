/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which fluids are meant to be drunk from a Drinking Bottle, and what one sip of each does. A
 * fluid with no entry here tastes foul (and poisons if toxic) — see
 * {@link com.dev1lroot.mcmods.omnitech.items.DrinkingBottleItem} — so unfiltered wine still full
 * of dead yeast should go through a Filter Press first. An effect with {@code "scale": false}
 * hits in full however little of the fluid is in the sip (methanol's blindness).
 *
 * <p>JSON layout (in {@code data/omnitech/drink/}); all but {@code fluid} are optional:
 * <pre>{@code
 * {
 *   "fluid": "omnitech:wine",
 *   "nutrition": 1, "saturation": 0.2,
 *   "clears_effects": false,
 *   "effects": [ { "id": "minecraft:nausea", "duration": 200, "amplifier": 0, "chance": 0.3, "scale": true } ]
 * }
 * }</pre>
 */
public class DrinkManager {

    /** {@code scaled}: duration shrinks with the fluid's share of the sip (false = even a trace hits in full). */
    public record Effect(Identifier id, int duration, int amplifier, float chance, boolean scaled) {}

    public record Drink(Identifier fluidId, int nutrition, float saturation, boolean clearsEffects, List<Effect> effects) {
        public @Nullable Fluid fluid() { return BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null); }
    }

    private static final Gson GSON = new Gson();
    private static final String PATH = "drink";

    private static volatile List<Drink> drinks = List.of();

    public static void loadRecipes(ResourceManager rm) {
        List<Drink> loaded = new ArrayList<>();
        try {
            Map<Identifier, Resource> resources = rm.listResources(PATH, id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    List<Effect> effects = new ArrayList<>();
                    if (json.has("effects")) {
                        for (JsonElement el : json.getAsJsonArray("effects")) {
                            JsonObject e = el.getAsJsonObject();
                            effects.add(new Effect(Identifier.parse(e.get("id").getAsString()),
                                    e.has("duration") ? e.get("duration").getAsInt() : 200,
                                    e.has("amplifier") ? e.get("amplifier").getAsInt() : 0,
                                    e.has("chance") ? e.get("chance").getAsFloat() : 1f,
                                    !e.has("scale") || e.get("scale").getAsBoolean()));
                        }
                    }
                    loaded.add(new Drink(Identifier.parse(json.get("fluid").getAsString()),
                            json.has("nutrition") ? json.get("nutrition").getAsInt() : 0,
                            json.has("saturation") ? json.get("saturation").getAsFloat() : 0f,
                            json.has("clears_effects") && json.get("clears_effects").getAsBoolean(),
                            List.copyOf(effects)));
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load drink: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan {}/", PATH, e);
        }
        drinks = List.copyOf(loaded);
        OmniTech.LOGGER.info("Loaded {} drink(s)", drinks.size());
    }

    public static List<Drink> getAll() { return drinks; }

    public static @Nullable Drink find(Fluid fluid) {
        for (Drink d : drinks) if (d.fluid() == fluid) return d;
        return null;
    }
}
