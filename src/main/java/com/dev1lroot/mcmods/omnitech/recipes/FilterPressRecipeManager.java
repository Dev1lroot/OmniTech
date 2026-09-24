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
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FilterPressRecipeManager {

    private static final Gson GSON   = new Gson();
    private static final String PATH = "machine_recipe/filter_press";
    private static final Map<String, FilterPressRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    JsonObject inFluidObj = json.getAsJsonObject("inputFluid");
                    String inFluid = inFluidObj.get("fluid").getAsString();
                    int    inFAmt  = inFluidObj.get("amount").getAsInt();

                    // Optional: straining a mixture needs no filtrate declared (see FilterPressRecipe)
                    JsonObject outFluidObj = json.getAsJsonObject("outputFluid");
                    String outFluid = outFluidObj != null ? outFluidObj.get("fluid").getAsString() : "minecraft:empty";
                    int    outFAmt  = outFluidObj != null ? outFluidObj.get("amount").getAsInt()   : 0;

                    JsonObject outItemObj = json.getAsJsonObject("outputItem");
                    String outItem = outItemObj.get("item").getAsString();
                    int    outIAmt = outItemObj.has("amount") ? outItemObj.get("amount").getAsInt() : 1;

                    String recipeId = id.getPath().replace(PATH + "/", "").replace(".json", "");

                    FilterPressRecipe recipe = new FilterPressRecipe(
                            recipeId, kf, inFluid, inFAmt, outFluid, outFAmt, outItem, outIAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info(
                            "Loaded filter press recipe: {} ({} KF, {} mB {} → {} mB {} + {}x {})",
                            recipeId, kf, inFAmt, inFluid, outFAmt, outFluid, outIAmt, outItem);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load filter press recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan filter_press recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} filter press recipe(s)", RECIPES.size());
    }

    public static Optional<FilterPressRecipe> findRecipe(FluidStack inputFluid) {
        for (FilterPressRecipe r : RECIPES.values()) {
            if (r.matches(inputFluid)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<FilterPressRecipe> getAllRecipes() { return new ArrayList<>(RECIPES.values()); }

    public static boolean isInitialized() { return initialized; }
}
