package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

public class AlloyFurnaceRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, AlloyFurnaceRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "recipe/alloy_furnace";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int minTemperature = json.get("minTemperature").getAsInt();

                    List<String> ingredients = new ArrayList<>();
                    JsonArray ingredientsArray = json.getAsJsonArray("ingredients");
                    for (int i = 0; i < ingredientsArray.size(); i++) {
                        ingredients.add(ingredientsArray.get(i).getAsString());
                    }

                    List<String> outputs = new ArrayList<>();
                    JsonArray outputsArray = json.getAsJsonArray("output");
                    for (int i = 0; i < outputsArray.size(); i++) {
                        outputs.add(outputsArray.get(i).getAsString());
                    }

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    AlloyFurnaceRecipe recipe = new AlloyFurnaceRecipe(recipeId, minTemperature, ingredients, outputs);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded alloy furnace recipe: {} (temp: {}C)", recipeId, minTemperature);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load alloy furnace recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} alloy furnace recipes", RECIPES.size());
    }

    public static Optional<AlloyFurnaceRecipe> findRecipe(List<ItemStack> inputs) {
        for (AlloyFurnaceRecipe recipe : RECIPES.values()) {
            if (recipe.matches(inputs)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static List<AlloyFurnaceRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
