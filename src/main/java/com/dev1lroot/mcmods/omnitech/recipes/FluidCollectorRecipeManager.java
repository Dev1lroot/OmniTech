package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FluidCollectorRecipeManager {

    private static final Gson   GSON    = new Gson();
    private static final String PATH    = "fluid_collector_recipes";
    private static final Map<String, FluidCollectorRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    String inputBlock = json.get("inputBlock").getAsString();

                    JsonObject outFluidObj = json.getAsJsonObject("outputFluid");
                    String outFluid = outFluidObj.get("fluid").getAsString();
                    int    outAmt   = outFluidObj.get("amount").getAsInt();

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    FluidCollectorRecipe recipe = new FluidCollectorRecipe(
                            recipeId, inputBlock, outFluid, outAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info("Loaded fluid_collector recipe: {} ({} → {} mB/t)",
                            recipeId, inputBlock, outAmt);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load fluid_collector recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan fluid_collector_recipes/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} fluid_collector recipe(s)", RECIPES.size());
    }

    public static Optional<FluidCollectorRecipe> findRecipe(Block block) {
        for (FluidCollectorRecipe r : RECIPES.values()) {
            if (r.matches(block)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<FluidCollectorRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
