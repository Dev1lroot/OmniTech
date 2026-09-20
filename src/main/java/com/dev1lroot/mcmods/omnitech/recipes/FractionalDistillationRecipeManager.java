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
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FractionalDistillationRecipeManager {

    private static final Gson   GSON    = new Gson();
    private static final String FOLDER  = "machine_recipe/fractional_distillation";
    private static final Map<String, FractionalDistillationRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager manager) {
        RECIPES.clear();

        try {
            Map<Identifier, Resource> resources = manager.listResources(
                    FOLDER, id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int    reqTemp = json.get("requiredTemperature").getAsInt();
                    int    time    = json.get("productionTime").getAsInt();

                    JsonObject inputObj    = json.getAsJsonObject("input");
                    String     inputFluid  = inputObj.get("fluid").getAsString();
                    int        inputAmount = inputObj.get("amount").getAsInt();

                    Integer minInputTemp     = inputObj.has("min_temp")     ? inputObj.get("min_temp").getAsInt()     : null;
                    Integer maxInputTemp     = inputObj.has("max_temp")     ? inputObj.get("max_temp").getAsInt()     : null;
                    Integer minInputPressure = inputObj.has("min_pressure") ? inputObj.get("min_pressure").getAsInt() : null;
                    Integer maxInputPressure = inputObj.has("max_pressure") ? inputObj.get("max_pressure").getAsInt() : null;

                    JsonArray       outputArr     = json.getAsJsonArray("outputs");
                    List<String>    outputFluids  = new ArrayList<>();
                    List<Integer>   outputAmounts = new ArrayList<>();
                    int[]           outputTemps     = new int[outputArr.size()];
                    int[]           outputPressures = new int[outputArr.size()];

                    for (int i = 0; i < outputArr.size(); i++) {
                        JsonObject o = outputArr.get(i).getAsJsonObject();
                        outputFluids.add(o.get("fluid").getAsString());
                        outputAmounts.add(o.get("amount").getAsInt());
                        outputTemps[i]     = o.has("temp")     ? o.get("temp").getAsInt()     : Integer.MIN_VALUE;
                        outputPressures[i] = o.has("pressure") ? o.get("pressure").getAsInt() : Integer.MIN_VALUE;
                    }

                    String recipeId = id.getPath()
                            .replace(FOLDER + "/", "")
                            .replace(".json", "");

                    FractionalDistillationRecipe recipe = new FractionalDistillationRecipe(
                            recipeId, reqTemp, time,
                            inputFluid, inputAmount,
                            minInputTemp, maxInputTemp,
                            minInputPressure, maxInputPressure,
                            outputFluids, outputAmounts,
                            outputTemps, outputPressures);

                    RECIPES.put(recipeId, recipe);
                    OmniTech.LOGGER.info(
                            "Loaded fractional distillation recipe: {} ({}°C, {}t, {} outputs)",
                            recipeId, reqTemp, time, outputFluids.size());

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load fractional distillation recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan fractional distillation recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} fractional distillation recipes", RECIPES.size());
    }

    /**
     * Finds the first recipe that:
     * <ul>
     *   <li>matches the input fluid and has enough volume</li>
     *   <li>satisfies the temperature condition for {@code storedHeat}</li>
     *   <li>has exactly {@code outputCount} output products</li>
     * </ul>
     */
    public static Optional<FractionalDistillationRecipe> findRecipe(
            FluidStack inputTank, float temperature, int outputCount) {
        for (FractionalDistillationRecipe recipe : RECIPES.values()) {
            if (recipe.getOutputCount() == outputCount
                    && recipe.matchesInput(inputTank)
                    && recipe.temperatureMet(temperature)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static List<FractionalDistillationRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
