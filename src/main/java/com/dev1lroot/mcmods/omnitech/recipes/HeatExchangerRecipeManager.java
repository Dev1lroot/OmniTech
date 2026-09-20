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

public class HeatExchangerRecipeManager {

    private static final Gson   GSON    = new Gson();
    private static final String PATH    = "machine_recipe/heat_exchanger";
    private static final Map<String, HeatExchangerRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    int maxHeat       = json.get("maxHeat").getAsInt();
                    int productionHeat = json.get("productionHeat").getAsInt();

                    JsonObject inFluidObj = json.getAsJsonObject("inputFluid");
                    String inFluid  = inFluidObj.get("fluid").getAsString();
                    int    inFAmt   = inFluidObj.get("amount").getAsInt();

                    JsonObject outFluidObj = json.getAsJsonObject("outputFluid");
                    String outFluid = outFluidObj.get("fluid").getAsString();
                    int    outFAmt  = outFluidObj.get("amount").getAsInt();

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    HeatExchangerRecipe recipe = new HeatExchangerRecipe(
                            recipeId, maxHeat, productionHeat, inFluid, inFAmt, outFluid, outFAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info(
                            "Loaded heat exchanger recipe: {} (max {}°C, +{}°C/batch, {} mB → {} mB)",
                            recipeId, maxHeat, productionHeat, inFAmt, outFAmt);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load heat exchanger recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan heat_exchanger_recipes/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} heat exchanger recipe(s)", RECIPES.size());
    }

    public static Optional<HeatExchangerRecipe> findRecipe(FluidStack fluid) {
        for (HeatExchangerRecipe r : RECIPES.values()) {
            if (r.matches(fluid)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<HeatExchangerRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
