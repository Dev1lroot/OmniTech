/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ManualCentrifugeRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, ManualCentrifugeRecipe> RECIPES = new ConcurrentHashMap<>();

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "recipe/manual_centrifuge";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                    id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int requiredKineticForce = json.get("requiredKineticForce").getAsInt();
                    String input = json.get("input").getAsString();

                    List<ManualCentrifugeRecipe.Output> outputs = new ArrayList<>();
                    JsonArray outputsArray = json.getAsJsonArray("output");
                    for (int i = 0; i < outputsArray.size(); i++) {
                        JsonObject outObj = outputsArray.get(i).getAsJsonObject();
                        String itemId = outObj.get("item").getAsString();
                        int count = outObj.has("count") ? outObj.get("count").getAsInt() : 1;
                        float chance = outObj.has("chance") ? outObj.get("chance").getAsFloat() : 1.0f;
                        BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId))
                                .ifPresent(item -> outputs.add(new ManualCentrifugeRecipe.Output(item, count, chance)));
                    }

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    ManualCentrifugeRecipe recipe = new ManualCentrifugeRecipe(recipeId, requiredKineticForce, input, outputs);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded manual centrifuge recipe: {} (force: {})", recipeId, requiredKineticForce);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load manual centrifuge recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load manual centrifuge recipes", e);
        }

        OmniTech.LOGGER.info("Loaded {} manual centrifuge recipes", RECIPES.size());
    }

    public static Optional<ManualCentrifugeRecipe> findRecipe(ItemStack input) {
        for (ManualCentrifugeRecipe recipe : RECIPES.values()) {
            if (recipe.matches(input)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static List<ManualCentrifugeRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }
}
