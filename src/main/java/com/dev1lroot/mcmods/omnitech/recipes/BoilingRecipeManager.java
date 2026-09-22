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
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads {@code data/omnitech/machine_recipe/boiling/*.json} — see {@link BoilingRecipe}. */
public class BoilingRecipeManager {

    private static final Gson GSON   = new Gson();
    private static final String PATH = "machine_recipe/boiling";
    private static final Map<String, BoilingRecipe> RECIPES = new ConcurrentHashMap<>();

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

                    JsonObject in = json.getAsJsonObject("input");
                    String inFluid = in.get("fluid").getAsString();
                    int    inAmt   = in.get("amount").getAsInt();

                    String outFluid = null;
                    int    outAmt   = 0;
                    if (json.has("outputFluid")) {
                        JsonObject out = json.getAsJsonObject("outputFluid");
                        outFluid = out.get("fluid").getAsString();
                        outAmt   = out.get("amount").getAsInt();
                    }

                    String item   = null;
                    int    count  = 0;
                    float  chance = 1f;
                    if (json.has("result")) {
                        JsonObject res = json.getAsJsonObject("result");
                        item   = res.get("item").getAsString();
                        count  = res.has("amount") ? res.get("amount").getAsInt()    : 1;
                        chance = res.has("chance") ? res.get("chance").getAsFloat()  : 1f;
                    }

                    String recipeId = id.getPath().replace(PATH + "/", "").replace(".json", "");
                    RECIPES.put(recipeId, new BoilingRecipe(
                            recipeId, inFluid, inAmt, outFluid, outAmt, item, count, chance));
                    OmniTech.LOGGER.info("Loaded boiling recipe: {}", recipeId);

                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load boiling recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan {}/", PATH, e);
        }

        OmniTech.LOGGER.info("Loaded {} boiling recipe(s)", RECIPES.size());
    }

    /** The recipe for what happens when {@code fluid} boils, or {@code null} if it just evaporates. */
    @Nullable
    public static BoilingRecipe find(Fluid fluid) {
        for (BoilingRecipe r : RECIPES.values()) if (r.matches(fluid)) return r;
        return null;
    }

    public static BoilingRecipe get(String id) { return RECIPES.get(id); }

    public static List<BoilingRecipe> getAllRecipes() { return new ArrayList<>(RECIPES.values()); }
}
