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

public class SolvationRecipeManager {

    private static final Gson GSON    = new Gson();
    private static final String PATH  = "recipe/solvation";
    private static final Map<String, SolvationRecipe> RECIPES = new ConcurrentHashMap<>();
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

                    float kf = json.get("requiredKineticForce").getAsFloat();

                    JsonObject inFluidObj = json.getAsJsonObject("inputFluid");
                    String inFluid  = inFluidObj.get("fluid").getAsString();
                    int    inFAmt   = inFluidObj.get("amount").getAsInt();

                    JsonObject inItemObj = json.getAsJsonObject("inputItem");
                    String inItem   = inItemObj.get("item").getAsString();
                    int    inIAmt   = inItemObj.get("amount").getAsInt();

                    JsonObject outFluidObj = json.getAsJsonObject("outputFluid");
                    String outFluid = outFluidObj.get("fluid").getAsString();
                    int    outFAmt  = outFluidObj.get("amount").getAsInt();

                    String recipeId = id.getPath()
                            .replace(PATH + "/", "").replace(".json", "");

                    SolvationRecipe recipe = new SolvationRecipe(
                            recipeId, kf, inFluid, inFAmt, inItem, inIAmt, outFluid, outFAmt);
                    RECIPES.put(recipeId, recipe);

                    OmniTech.LOGGER.info(
                            "Loaded solvation recipe: {} ({} KF, {} mB → {} mB)",
                            recipeId, kf, inFAmt, outFAmt);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load solvation recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan solvation_recipes/", e);
        }

        initialized = true;
        OmniTech.LOGGER.info("Loaded {} solvation recipe(s)", RECIPES.size());
    }

    public static Optional<SolvationRecipe> findRecipe(ItemStack item, FluidStack fluid) {
        for (SolvationRecipe r : RECIPES.values()) {
            if (r.matches(item, fluid)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static List<SolvationRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES.values());
    }

    public static boolean isInitialized() { return initialized; }
}
