/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SmelterRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, SmelterRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "machine_recipe/smelting";

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

                    List<String> ingredients = new ArrayList<>();
                    JsonArray inputArray = json.getAsJsonArray("input");
                    for (int i = 0; i < inputArray.size(); i++) {
                        ingredients.add(inputArray.get(i).getAsString());
                    }

                    JsonObject outputObj = json.getAsJsonObject("output");
                    String fluidName = outputObj.get("fluid").getAsString();
                    int fluidAmount = outputObj.get("amount").getAsInt();

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    SmelterRecipe recipe = new SmelterRecipe(recipeId, minTemp, ingredients, fluidName, fluidAmount);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded smelter recipe: {} ({}°C → {} mb {})",
                            recipeId, minTemp, fluidAmount, fluidName);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load smelter recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load smelter recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} smelter recipes", RECIPES.size());
    }

    public static Optional<SmelterRecipe> findRecipe(List<ItemStack> inputs) {
        for (SmelterRecipe recipe : RECIPES.values()) {
            if (recipe.matches(inputs)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    public static List<SmelterRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
