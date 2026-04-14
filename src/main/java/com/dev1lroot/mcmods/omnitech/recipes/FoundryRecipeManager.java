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

public class FoundryRecipeManager {
    private static final Gson GSON = new Gson();
    private static final Map<String, FoundryRecipe> RECIPES = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void loadRecipes(ResourceManager resourceManager) {
        RECIPES.clear();
        String path = "recipe/foundry";

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(path,
                    id -> id.getPath().endsWith(".json"));

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    int minTemp = json.get("requiredMinimalTemperature").getAsInt();

                    JsonObject inputObj = json.getAsJsonObject("input");
                    String fluidName   = inputObj.get("fluid").getAsString();
                    int fluidAmount    = inputObj.get("amount").getAsInt();

                    String templateId  = json.get("template").getAsString();
                    String outputId    = json.get("output").getAsString();

                    String recipeId = id.getPath().replace(path + "/", "").replace(".json", "");
                    FoundryRecipe recipe = new FoundryRecipe(recipeId, minTemp,
                            fluidName, fluidAmount, templateId, outputId);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded foundry recipe: {} ({}°C, {} mb {} → {})",
                            recipeId, minTemp, fluidAmount, fluidName, outputId);
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load foundry recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to load foundry recipes", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} foundry recipes", RECIPES.size());
    }

    /**
     * Find a recipe matching the template item AND the fluid currently in the machine.
     * Both must be present — the fluid type in the tank determines which recipe is active.
     */
    public static Optional<FoundryRecipe> findRecipe(ItemStack templateStack, FluidStack fluid) {
        if (templateStack.isEmpty() || fluid.isEmpty()) return Optional.empty();
        for (FoundryRecipe recipe : RECIPES.values()) {
            if (!recipe.matchesTemplate(templateStack)) continue;
            if (recipe.matchesFluid(fluid)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    public static List<FoundryRecipe> getAllRecipes()  { return new ArrayList<>(RECIPES.values()); }
    public static boolean isInitialized()              { return initialized; }
}
