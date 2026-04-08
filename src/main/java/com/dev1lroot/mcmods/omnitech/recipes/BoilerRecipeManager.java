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

public class BoilerRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, BoilerRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "boiler_recipes";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(
                    path, id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int minTemp  = json.get("requiredMinimalTemperature").getAsInt();
                    int time     = json.get("productionTime").getAsInt();
                    int heatCost = json.has("heatConsumptionPerTick")
                            ? json.get("heatConsumptionPerTick").getAsInt() : 3;

                    JsonObject inputObj  = json.getAsJsonObject("input");
                    String inputFluid    = inputObj.get("fluid").getAsString();
                    int    inputAmount   = inputObj.get("amount").getAsInt();

                    JsonObject outputObj = json.getAsJsonObject("output");
                    String outputFluid   = outputObj.get("fluid").getAsString();
                    int    outputAmount  = outputObj.get("amount").getAsInt();

                    String resultItem  = "";
                    int    resultCount = 1;
                    float  resultChance = 0f;
                    if (json.has("result")) {
                        JsonObject resultObj = json.getAsJsonObject("result");
                        resultItem   = resultObj.get("item").getAsString();
                        resultCount  = resultObj.has("amount") ? resultObj.get("amount").getAsInt() : 1;
                        resultChance = resultObj.has("chance") ? resultObj.get("chance").getAsFloat() : 1.0f;
                    }

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    BoilerRecipe recipe = new BoilerRecipe(recipeId, minTemp, time, heatCost,
                            inputFluid, inputAmount, outputFluid, outputAmount,
                            resultItem, resultCount, resultChance);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded boiler recipe: {} ({}°C, {}t, -{}/tick, {} -> {})",
                            recipeId, minTemp, time, heatCost, inputFluid, outputFluid);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load boiler recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan boiler recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} boiler recipes", RECIPES.size());
    }

    public static Optional<BoilerRecipe> findRecipe(FluidStack inputTank) {
        for (BoilerRecipe recipe : RECIPES.values()) {
            if (recipe.matchesInput(inputTank)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    public static List<BoilerRecipe> getAllRecipes() { return new ArrayList<>(RECIPES.values()); }
    public static boolean isInitialized() { return initialized; }
}
