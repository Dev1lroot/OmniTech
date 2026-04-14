package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ManualMaceratorRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, ManualMaceratorRecipe> RECIPES = new ConcurrentHashMap<>();

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "recipe/manual_macerator";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                    id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int requiredKineticForce = json.get("requiredKineticForce").getAsInt();
                    String input = json.get("input").getAsString();

                    // Логика парсинга объектов вместо строк
                    List<ManualMaceratorRecipe.Output> outputs = new ArrayList<>();
                    JsonArray outputsArray = json.getAsJsonArray("output");

                    for (int i = 0; i < outputsArray.size(); i++) {
                        JsonObject outObj = outputsArray.get(i).getAsJsonObject();
                        String itemId = outObj.get("item").getAsString();

                        // Читаем количество (по умолчанию 1) и шанс (по умолчанию 1.0)
                        int count = outObj.has("count") ? outObj.get("count").getAsInt() : 1;
                        float chance = outObj.has("chance") ? outObj.get("chance").getAsFloat() : 1.0f;

                        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
                        if (item != null) {
                            outputs.add(new ManualMaceratorRecipe.Output(item, count, chance));
                        }
                    }

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    ManualMaceratorRecipe recipe = new ManualMaceratorRecipe(recipeId, requiredKineticForce, input, outputs);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded manual macerator recipe: {} (force: {})", recipeId, requiredKineticForce);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load manual macerator recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load manual macerator recipes", e);
        }

        OmniTech.LOGGER.info("Loaded {} manual macerator recipes", RECIPES.size());
    }

    public static Optional<ManualMaceratorRecipe> findRecipe(ItemStack input) {
        for (ManualMaceratorRecipe recipe : RECIPES.values()) {
            if (recipe.matches(input)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    public static List<ManualMaceratorRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }
}