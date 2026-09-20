/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A Glass Blowing Station recipe: one input item shapes into one output item, but only once
 * the station is heated to at least {@link #requiredMinimalTemperature}. Several recipes can
 * share the same input (e.g. sand shapes into a block, a pane, a bottle, or a flask) — the
 * player picks which one from the station's stonecutter-style list.
 */
public class GlassBlowingRecipe {

    private final String id;
    private final int requiredMinimalTemperature;
    private final Item input;
    private final Item output;
    private final int outputCount;

    public GlassBlowingRecipe(String id, int requiredMinimalTemperature,
            String inputId, String outputId, int outputCount) {
        this.id = id;
        this.requiredMinimalTemperature = requiredMinimalTemperature;
        this.input = BuiltInRegistries.ITEM.getOptional(resolve(inputId)).orElse(null);
        this.output = BuiltInRegistries.ITEM.getOptional(resolve(outputId)).orElse(null);
        this.outputCount = outputCount;
    }

    private static Identifier resolve(String id) {
        return id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("omnitech", id);
    }

    public String getId() { return id; }
    public int getRequiredMinimalTemperature() { return requiredMinimalTemperature; }
    public Item getInput() { return input; }

    public ItemStack getResult() {
        return output == null ? ItemStack.EMPTY : new ItemStack(output, outputCount);
    }

    public boolean matches(ItemStack inputStack) {
        return input != null && output != null && !inputStack.isEmpty() && inputStack.is(input);
    }
}
