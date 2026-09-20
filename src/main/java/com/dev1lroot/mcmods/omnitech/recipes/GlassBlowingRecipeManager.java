/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlassBlowingRecipeManager {

    private static final Gson GSON = new Gson();
    private static final Map<String, GlassBlowingRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "machine_recipe/glass_blowing";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                    id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int minTemp = json.get("requiredMinimalTemperature").getAsInt();
                    String input = json.get("input").getAsString();

                    JsonObject outputObj = json.getAsJsonObject("output");
                    String outputItem = outputObj.get("item").getAsString();
                    int outputCount = outputObj.has("count") ? outputObj.get("count").getAsInt() : 1;

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    GlassBlowingRecipe recipe = new GlassBlowingRecipe(
                            recipeId, minTemp, input, outputItem, outputCount);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded glass blowing recipe: {} ({}°C, {} → {}x {})",
                            recipeId, minTemp, input, outputCount, outputItem);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load glass blowing recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load glass blowing recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} glass blowing recipes", RECIPES.size());
    }

    /** All recipes whose input matches {@code stack}, in a stable order (by id). */
    public static List<GlassBlowingRecipe> findRecipesFor(ItemStack stack) {
        List<GlassBlowingRecipe> matches = new ArrayList<>();
        if (stack.isEmpty()) return matches;
        for (GlassBlowingRecipe recipe : RECIPES.values()) {
            if (recipe.matches(stack)) matches.add(recipe);
        }
        matches.sort(java.util.Comparator.comparing(GlassBlowingRecipe::getId));
        return matches;
    }

    public static List<GlassBlowingRecipe> getAllRecipes() { return new ArrayList<>(RECIPES.values()); }

    public static boolean isInitialized() { return initialized; }
}
