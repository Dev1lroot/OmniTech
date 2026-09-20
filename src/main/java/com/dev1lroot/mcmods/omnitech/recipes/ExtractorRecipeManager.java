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

public class ExtractorRecipeManager {

    private static final Gson GSON = new Gson();
    private static final String PATH = "machine_recipe/extractor";
    private static final Map<String, ExtractorRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager rm) {
        RECIPES.clear();

        try {
            Map<Identifier, Resource> resources = rm.listResources(
                    PATH, id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(entry.getValue().open()))) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    float kf = json.get("requiredKineticForce").getAsFloat();

                    JsonObject itemObj = json.getAsJsonObject("inputItem");
                    String inItem    = itemObj.get("item").getAsString();
                    int    inItemAmt = itemObj.has("amount") ? itemObj.get("amount").getAsInt() : 1;

                    JsonObject fluidObj = json.getAsJsonObject("outputFluid");
                    String outFluid    = fluidObj.get("fluid").getAsString();
                    int    outFluidAmt = fluidObj.get("amount").getAsInt();

                    String residueItem = "none";
                    int    residueAmt  = 1;
                    if (json.has("residueItem")) {
                        JsonObject resObj = json.getAsJsonObject("residueItem");
                        residueItem = resObj.get("item").getAsString();
                        residueAmt  = resObj.has("amount") ? resObj.get("amount").getAsInt() : 1;
                    }

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    ExtractorRecipe recipe = new ExtractorRecipe(
                            recipeId, kf,
                            inItem, inItemAmt,
                            outFluid, outFluidAmt,
                            residueItem, residueAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded extractor recipe: {} ({} KF)", recipeId, kf);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load extractor recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan recipe/extractor/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} extractor recipe(s)", RECIPES.size());
    }

    public static Optional<ExtractorRecipe> findRecipe(ItemStack item) {
        for (ExtractorRecipe r : RECIPES.values()) {
            if (r.matches(item)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<ExtractorRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
