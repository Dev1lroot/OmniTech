/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AlloyFurnaceRecipe {
    private final String id;
    private final int minTemperature;
    private final List<Item> ingredients;
    private final List<Item> outputs;
    private final boolean valid;

    public AlloyFurnaceRecipe(String id, int minTemperature, List<String> ingredientIds, List<String> outputIds) {
        this.id = id;
        this.minTemperature = minTemperature;
        this.ingredients = new ArrayList<>();
        this.outputs = new ArrayList<>();

        for (String ingredientId : ingredientIds) {
            BuiltInRegistries.ITEM.getOptional(Identifier.parse(ingredientId))
                    .ifPresentOrElse(ingredients::add, () -> OmniTech.LOGGER.error(
                            "Alloy furnace recipe '{}': unknown ingredient '{}' — recipe disabled", id, ingredientId));
        }
        // A dropped ingredient would otherwise make the recipe match far too much
        this.valid = ingredients.size() == ingredientIds.size() && !ingredients.isEmpty();

        for (String outputId : outputIds) {
            BuiltInRegistries.ITEM.getOptional(Identifier.parse(outputId))
                    .ifPresent(outputs::add);
        }
    }

    public String getId() {
        return id;
    }

    public int getMinTemperature() {
        return minTemperature;
    }

    public List<Item> getIngredients() {
        return ingredients;
    }

    public List<Item> getOutputs() {
        return outputs;
    }

    public List<ItemStack> getOutputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : outputs) {
            stacks.add(new ItemStack(item));
        }
        return stacks;
    }

    public boolean matches(List<ItemStack> inputStacks) {
        if (!valid) return false;
        // Count available items in input slots
        List<Item> availableItems = new ArrayList<>();
        for (ItemStack stack : inputStacks) {
            if (!stack.isEmpty()) {
                availableItems.add(stack.getItem());
            }
        }

        // Check if all required ingredients are present
        for (Item required : ingredients) {
            if (!availableItems.contains(required)) {
                return false;
            }
            availableItems.remove(required);
        }

        return true;
    }
}
