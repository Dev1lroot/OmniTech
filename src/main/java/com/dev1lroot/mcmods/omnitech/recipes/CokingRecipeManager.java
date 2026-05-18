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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CokingRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, CokingRecipe> RECIPES = new ConcurrentHashMap<>();

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "recipe/coking";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                    id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    String inputItem      = json.get("input_item").getAsString();
                    String outputItem     = json.get("output_item").getAsString();
                    int outputCount       = json.has("output_count") ? json.get("output_count").getAsInt() : 1;
                    int creosoteAmount    = json.has("creosote_amount") ? json.get("creosote_amount").getAsInt() : 250;
                    int productionTime    = json.has("production_time") ? json.get("production_time").getAsInt() : 600;

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    CokingRecipe recipe = new CokingRecipe(recipeId, inputItem, outputItem,
                            outputCount, creosoteAmount, productionTime);
                    RECIPES.put(recipeId, recipe);
                    OmniTech.LOGGER.info("Loaded coking recipe: {}", recipeId);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load coking recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load coking recipes", e);
        }
        OmniTech.LOGGER.info("Loaded {} coking recipes", RECIPES.size());
    }

    public static Optional<CokingRecipe> findRecipe(ItemStack stack) {
        for (CokingRecipe recipe : RECIPES.values()) {
            if (recipe.matches(stack)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    public static List<CokingRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }
}
