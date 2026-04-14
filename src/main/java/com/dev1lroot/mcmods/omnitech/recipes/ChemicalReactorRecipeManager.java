package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChemicalReactorRecipeManager {

    private static final Gson   GSON   = new Gson();
    private static final String FOLDER = "recipe/chemical_reactor";
    private static final Map<String, ChemicalReactorRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager rm) {
        RECIPES.clear();

        try {
            Map<Identifier, Resource> resources = rm.listResources(
                    FOLDER, id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(entry.getValue().open()))) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int reqTemp      = json.has("requiredTemperature")
                            ? json.get("requiredTemperature").getAsInt() : 0;
                    int prodTime     = json.has("productionTime")
                            ? json.get("productionTime").getAsInt() : 100;

                    JsonArray inputs = json.getAsJsonArray("inputs");
                    JsonObject in1   = inputs.get(0).getAsJsonObject();
                    JsonObject in2   = inputs.get(1).getAsJsonObject();
                    String input1Fluid  = in1.get("fluid").getAsString();
                    int    input1Amount = in1.get("amount").getAsInt();
                    String input2Fluid  = in2.get("fluid").getAsString();
                    int    input2Amount = in2.get("amount").getAsInt();

                    JsonObject outObj   = json.getAsJsonObject("output");
                    String outputFluid  = outObj.get("fluid").getAsString();
                    int    outputAmount = outObj.get("amount").getAsInt();

                    String catalystItem  = null;
                    int    catalystDamage = 0;
                    if (json.has("catalyst") && !json.get("catalyst").isJsonNull()) {
                        JsonObject catObj = json.getAsJsonObject("catalyst");
                        catalystItem  = catObj.get("item").getAsString();
                        catalystDamage = catObj.has("damage") ? catObj.get("damage").getAsInt() : 0;
                    }

                    String recipeId = id.getPath()
                            .replace(FOLDER + "/", "").replace(".json", "");

                    ChemicalReactorRecipe recipe = new ChemicalReactorRecipe(
                            recipeId, reqTemp, prodTime,
                            input1Fluid, input1Amount,
                            input2Fluid, input2Amount,
                            outputFluid, outputAmount,
                            catalystItem != null ? catalystItem : "none", catalystDamage);

                    RECIPES.put(recipeId, recipe);
                    OmniTech.LOGGER.info(
                            "Loaded chemical reactor recipe: {} ({}°C, {}t, catalyst={})",
                            recipeId, reqTemp, prodTime,
                            catalystItem != null ? catalystItem : "none");

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load chemical reactor recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan chemical_reactor_recipes/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} chemical reactor recipe(s)", RECIPES.size());
    }

    public static Optional<ChemicalReactorRecipe> findRecipe(
            FluidStack tank1, FluidStack tank2, ItemStack catalyst, int heat) {
        for (ChemicalReactorRecipe r : RECIPES.values()) {
            if (r.matches(tank1, tank2, catalyst, heat)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<ChemicalReactorRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
