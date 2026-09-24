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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads {@code data/omnitech/machine_recipe/press/*.json} — see {@link PressRecipe}. */
public class PressRecipeManager {

    private static final Gson GSON = new Gson();
    private static final String PATH = "machine_recipe/press";

    private static volatile List<PressRecipe> recipes = List.of();

    public static void loadRecipes(ResourceManager rm) {
        List<PressRecipe> loaded = new ArrayList<>();
        try {
            Map<Identifier, Resource> resources = rm.listResources(PATH, id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);

                    Identifier fluid = null;
                    int fluidAmount = 0;
                    if (json.has("outputFluid")) {
                        JsonObject out = json.getAsJsonObject("outputFluid");
                        fluid = Identifier.parse(out.get("fluid").getAsString());
                        fluidAmount = out.get("amount").getAsInt();
                    }

                    Identifier item = null;
                    int count = 0;
                    float chance = 1f;
                    if (json.has("outputItem")) {
                        JsonObject out = json.getAsJsonObject("outputItem");
                        item = Identifier.parse(out.get("item").getAsString());
                        count = out.has("count") ? out.get("count").getAsInt() : 1;
                        chance = out.has("chance") ? out.get("chance").getAsFloat() : 1f;
                    }

                    String recipeId = id.getPath().replace(PATH + "/", "").replace(".json", "");
                    loaded.add(new PressRecipe(recipeId, json.get("requiredKineticForce").getAsInt(),
                            Identifier.parse(json.get("input").getAsString()),
                            fluid, fluidAmount, item, count, chance));
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load press recipe: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan {}/", PATH, e);
        }
        recipes = List.copyOf(loaded);
        OmniTech.LOGGER.info("Loaded {} press recipe(s)", recipes.size());
    }

    public static List<PressRecipe> getAllRecipes() { return recipes; }

    public static Optional<PressRecipe> findRecipe(ItemStack stack) {
        for (PressRecipe r : recipes) if (r.matches(stack)) return Optional.of(r);
        return Optional.empty();
    }
}
