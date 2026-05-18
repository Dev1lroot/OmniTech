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

public class ChemicalInfuserRecipeManager {

    private static final Gson GSON = new Gson();
    private static final String PATH = "recipe/chemical_infuser";
    private static final Map<String, ChemicalInfuserRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    float kf   = json.get("requiredKineticForce").getAsFloat();
                    int   temp = json.has("requiredTemperature")
                            ? json.get("requiredTemperature").getAsInt() : 15;

                    JsonObject fluidObj = json.getAsJsonObject("inputFluid");
                    String inFluid = fluidObj.get("fluid").getAsString();
                    int    inFluidAmt = fluidObj.get("amount").getAsInt();

                    JsonObject itemObj = json.getAsJsonObject("inputItem");
                    String inItem = itemObj.get("item").getAsString();
                    int    inItemAmt = itemObj.has("amount") ? itemObj.get("amount").getAsInt() : 1;

                    JsonObject outObj = json.getAsJsonObject("outputItem");
                    String outItem = outObj.get("item").getAsString();
                    int    outAmt  = outObj.has("amount") ? outObj.get("amount").getAsInt() : 1;

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    ChemicalInfuserRecipe recipe = new ChemicalInfuserRecipe(
                            recipeId, kf, temp,
                            inFluid, inFluidAmt,
                            inItem,  inItemAmt,
                            outItem, outAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded chemical_infuser recipe: {} ({} KF, temp ≥ {})",
                            recipeId, kf, temp);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load chemical_infuser recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan recipe/chemical_infuser/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} chemical_infuser recipe(s)", RECIPES.size());
    }

    public static Optional<ChemicalInfuserRecipe> findRecipe(FluidStack fluid, ItemStack item,
            int currentTemp) {
        for (ChemicalInfuserRecipe r : RECIPES.values()) {
            if (r.matches(fluid, item, currentTemp)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<ChemicalInfuserRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
