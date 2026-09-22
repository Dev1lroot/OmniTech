/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipe.FluidAmount;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads everything the Fermenter needs from data packs:
 * <ul>
 *   <li>{@code machine_recipe/fermentation/*.json} — the timed reactions, see {@link FermentationRecipe}</li>
 *   <li>{@code machine_recipe/fermenter_dissolve/*.json} — items the input slot puts into the
 *       solution: {@code { "item": "omnitech:wheat_flour", "fluid": "omnitech:flour", "amount": 250,
 *       "dissolved": false }} ({@code dissolved} defaults to true; false = suspended dust)</li>
 *   <li>{@code machine_recipe/fermenter_microbes/*.json} — what can spontaneously appear in a batch
 *       with no live culture: {@code { "fluid": "omnitech:yeast", "amount": 1, "weight": 3,
 *       "dissolved": false, "requires": ["minecraft:water", "omnitech:sugar"] }}</li>
 * </ul>
 * Maps are sorted by file name so every server iterates reactions in the same order.
 */
public class FermentationRecipeManager {

    /** An item the input slot can dissolve into the solution. */
    public record Dissolution(Identifier itemId, FluidAmount fluid) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            return item != null && stack.is(item);
        }
    }

    /** A microbe that can appear on its own once its {@code requires} fluids are all present. */
    public record Microbe(FluidAmount fluid, int weight, List<Identifier> requires) {}

    private static final Gson GSON = new Gson();
    private static final String RECIPE_PATH   = "machine_recipe/fermentation";
    private static final String DISSOLVE_PATH = "machine_recipe/fermenter_dissolve";
    private static final String MICROBE_PATH  = "machine_recipe/fermenter_microbes";

    private static volatile List<FermentationRecipe> recipes = List.of();
    private static volatile List<Dissolution> dissolutions = List.of();
    private static volatile List<Microbe> microbes = List.of();

    public static void loadRecipes(ResourceManager rm) {
        Map<String, FermentationRecipe> loadedRecipes = new TreeMap<>();
        Map<String, Dissolution> loadedDissolutions = new TreeMap<>();
        Map<String, Microbe> loadedMicrobes = new TreeMap<>();

        scan(rm, RECIPE_PATH, (name, json) -> loadedRecipes.put(name, parseRecipe(name, json)));
        scan(rm, DISSOLVE_PATH, (name, json) -> loadedDissolutions.put(name, new Dissolution(
                parseId(json.get("item").getAsString()),
                new FluidAmount(parseId(json.get("fluid").getAsString()), json.get("amount").getAsInt(),
                        !json.has("dissolved") || json.get("dissolved").getAsBoolean()))));
        scan(rm, MICROBE_PATH, (name, json) -> {
            List<Identifier> requires = new ArrayList<>();
            if (json.has("requires")) {
                for (JsonElement e : json.getAsJsonArray("requires")) requires.add(parseId(e.getAsString()));
            }
            loadedMicrobes.put(name, new Microbe(
                    new FluidAmount(parseId(json.get("fluid").getAsString()),
                            json.has("amount") ? json.get("amount").getAsInt() : 1,
                            !json.has("dissolved") || json.get("dissolved").getAsBoolean()),
                    json.has("weight") ? json.get("weight").getAsInt() : 1,
                    List.copyOf(requires)));
        });

        recipes = List.copyOf(loadedRecipes.values());
        dissolutions = List.copyOf(loadedDissolutions.values());
        microbes = List.copyOf(loadedMicrobes.values());
        OmniTech.LOGGER.info("Loaded {} fermentation reaction(s), {} dissolvable item(s), {} microbe(s)",
                recipes.size(), dissolutions.size(), microbes.size());
    }

    private static void scan(ResourceManager rm, String path, EntryReader consumer) {
        Map<Identifier, Resource> resources = rm.listResources(path, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier id = entry.getKey();
            if (!id.getNamespace().equals(OmniTech.MODID)) continue;
            String name = id.getPath().substring(path.length() + 1, id.getPath().length() - ".json".length());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                consumer.accept(name, GSON.fromJson(reader, JsonObject.class));
            } catch (Exception e) {
                OmniTech.LOGGER.error("Failed to load {} entry: {}", path, id, e);
            }
        }
    }

    private interface EntryReader { void accept(String name, JsonObject json); }

    private static FermentationRecipe parseRecipe(String name, JsonObject json) {
        Identifier itemId = null;
        int itemAmount = 1;
        if (json.has("outputItem")) {
            JsonObject item = json.getAsJsonObject("outputItem");
            itemId = parseId(item.get("item").getAsString());
            itemAmount = item.has("amount") ? item.get("amount").getAsInt() : 1;
        }
        return new FermentationRecipe(name, json.get("interval").getAsInt(),
                parseFluids(json, "inputs"), parseFluids(json, "catalysts"), parseFluids(json, "outputs"),
                itemId, itemAmount);
    }

    private static List<FluidAmount> parseFluids(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        JsonArray array = json.getAsJsonArray(key);
        List<FluidAmount> out = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            out.add(new FluidAmount(parseId(o.get("fluid").getAsString()), o.get("amount").getAsInt(),
                    !o.has("dissolved") || o.get("dissolved").getAsBoolean()));
        }
        return List.copyOf(out);
    }

    private static Identifier parseId(String raw) {
        return raw.contains(":") ? Identifier.parse(raw) : Identifier.fromNamespaceAndPath("minecraft", raw);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static List<FermentationRecipe> getAllRecipes() { return recipes; }
    public static List<Microbe> getMicrobes()              { return microbes; }

    /** The dissolution rule for {@code stack}, or {@code null} if the input slot can't dissolve it. */
    public static @Nullable Dissolution findDissolution(ItemStack stack) {
        for (Dissolution d : dissolutions) if (d.matches(stack)) return d;
        return null;
    }

    /** True if {@code fluid} is one of the spontaneous microbes — i.e. counts as "alive" in a batch. */
    public static boolean isLiveMicrobe(Fluid fluid) {
        for (Microbe m : microbes) if (m.fluid().fluid() == fluid) return true;
        return false;
    }
}
