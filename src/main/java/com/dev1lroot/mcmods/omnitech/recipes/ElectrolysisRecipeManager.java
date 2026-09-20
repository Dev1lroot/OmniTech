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
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ElectrolysisRecipeManager {

    private static final Gson   GSON    = new Gson();
    private static final String PATH    = "machine_recipe/electrolysis";
    private static final Map<String, ElectrolysisRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    float energy = json.has("energyRequired")
                            ? json.get("energyRequired").getAsFloat() : 200.0f;
                    int requiredTemperature = json.has("requiredTemperature")
                            ? json.get("requiredTemperature").getAsInt() : 0;

                    JsonObject inFluidObj = json.getAsJsonObject("inputFluid");
                    String inFluid  = inFluidObj.get("fluid").getAsString();
                    int    inFAmt   = inFluidObj.get("amount").getAsInt();

                    JsonObject outAnodeObj = json.getAsJsonObject("outputAnode");
                    String outAnode    = outAnodeObj.get("fluid").getAsString();
                    int    outAnodeAmt = outAnodeObj.get("amount").getAsInt();

                    JsonObject outCathodeObj = json.getAsJsonObject("outputCathode");
                    String outCathode    = outCathodeObj.get("fluid").getAsString();
                    int    outCathodeAmt = outCathodeObj.get("amount").getAsInt();

                    JsonObject outSolutionObj = json.getAsJsonObject("outputSolution");
                    String outSolution    = outSolutionObj.get("fluid").getAsString();
                    int    outSolutionAmt = outSolutionObj.get("amount").getAsInt();

                    JsonObject anodeItemObj = json.getAsJsonObject("anode");
                    String anodeItem   = anodeItemObj.get("item").getAsString();
                    int    anodeDamage = anodeItemObj.get("damage").getAsInt();

                    JsonObject cathodeItemObj = json.getAsJsonObject("cathode");
                    String cathodeItem   = cathodeItemObj.get("item").getAsString();
                    int    cathodeDamage = cathodeItemObj.get("damage").getAsInt();

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    ElectrolysisRecipe recipe = new ElectrolysisRecipe(
                            recipeId, energy, requiredTemperature,
                            inFluid, inFAmt,
                            outAnode, outAnodeAmt,
                            outCathode, outCathodeAmt,
                            outSolution, outSolutionAmt,
                            anodeItem, anodeDamage,
                            cathodeItem, cathodeDamage);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info(
                            "Loaded electrolysis recipe: {} ({} EU, {} mB in → {}/{}/{} mB out)",
                            recipeId, energy, inFAmt, outAnodeAmt, outCathodeAmt, outSolutionAmt);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load electrolysis recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan electrolysis_recipes/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} electrolysis recipe(s)", RECIPES.size());
    }

    public static Optional<ElectrolysisRecipe> findRecipe(FluidStack fluid,
            ItemStack anodeSlot, ItemStack cathodeSlot) {
        for (ElectrolysisRecipe r : RECIPES.values()) {
            if (r.matches(fluid, anodeSlot, cathodeSlot)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<ElectrolysisRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
