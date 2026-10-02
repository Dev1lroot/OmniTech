/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Glass Blowing Station recipe: one input item shapes into one output item, but only once
 * the station is heated to at least {@link #requiredMinimalTemperature}. Several recipes can
 * share the same input (e.g. sand shapes into a block, a pane, a bottle, or a flask) — the
 * player picks which one from the station's stonecutter-style list.
 *
 * <p>The input is an item id or, prefixed with {@code #}, an item tag — so ordinary glass
 * takes any {@code #minecraft:smelts_to_glass} sand, while fused silica wants quartz.
 */
public class GlassBlowingRecipe {

    private final String id;
    private final int requiredMinimalTemperature;
    private final @Nullable Item input;
    private final @Nullable TagKey<Item> inputTag;
    private final Item output;
    private final int outputCount;

    public GlassBlowingRecipe(String id, int requiredMinimalTemperature,
            String inputId, String outputId, int outputCount) {
        this.id = id;
        this.requiredMinimalTemperature = requiredMinimalTemperature;
        if (inputId.startsWith("#")) {
            this.input = null;
            this.inputTag = TagKey.create(Registries.ITEM, resolve(inputId.substring(1)));
        } else {
            this.input = BuiltInRegistries.ITEM.getOptional(resolve(inputId)).orElse(null);
            this.inputTag = null;
        }
        this.output = BuiltInRegistries.ITEM.getOptional(resolve(outputId)).orElse(null);
        this.outputCount = outputCount;
    }

    private static Identifier resolve(String id) {
        return id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("omnitech", id);
    }

    public String getId() { return id; }
    public int getRequiredMinimalTemperature() { return requiredMinimalTemperature; }

    /** Every item the input accepts (the tag's members, or the one item). */
    public List<ItemStack> getInputStacks() {
        List<ItemStack> out = new ArrayList<>();
        if (inputTag != null) BuiltInRegistries.ITEM.getTagOrEmpty(inputTag).forEach(h -> out.add(new ItemStack(h)));
        else if (input != null) out.add(new ItemStack(input));
        return out;
    }

    public ItemStack getResult() {
        return output == null ? ItemStack.EMPTY : new ItemStack(output, outputCount);
    }

    public boolean matches(ItemStack inputStack) {
        if (output == null || inputStack.isEmpty()) return false;
        if (inputTag != null) return inputStack.is(inputTag);
        return input != null && inputStack.is(input);
    }
}
