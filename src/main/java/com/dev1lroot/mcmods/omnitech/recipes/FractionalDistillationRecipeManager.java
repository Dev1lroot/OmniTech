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
    private static final String FOLDER  = "recipe/fractional_distillation";
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

                    JsonObject inputObj  = json.getAsJsonObject("input");
                    String     inputFluid  = inputObj.get("fluid").getAsString();
                    int        inputAmount = inputObj.get("amount").getAsInt();

                    JsonArray       outputArr    = json.getAsJsonArray("outputs");
                    List<String>    outputFluids = new ArrayList<>();
                    List<Integer>   outputAmounts = new ArrayList<>();

                    for (int i = 0; i < outputArr.size(); i++) {
                        JsonObject o = outputArr.get(i).getAsJsonObject();
                        outputFluids.add(o.get("fluid").getAsString());
                        outputAmounts.add(o.get("amount").getAsInt());
                    }

                    String recipeId = id.getPath()
                            .replace(FOLDER + "/", "")
                            .replace(".json", "");

                    FractionalDistillationRecipe recipe = new FractionalDistillationRecipe(
                            recipeId, reqTemp, time,
                            inputFluid, inputAmount,
                            outputFluids, outputAmounts);

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
            FluidStack inputTank, int storedHeat, int outputCount) {
        for (FractionalDistillationRecipe recipe : RECIPES.values()) {
            if (recipe.getOutputCount() == outputCount
                    && recipe.matchesInput(inputTank)
                    && recipe.temperatureMet(storedHeat)) {
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
