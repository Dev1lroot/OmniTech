/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipe;

import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crafting-table recipe: an empty {@link PipetteItem} + a {@link FlaskItem} holding a
 * {@link Solution} → the pipette draws up to its configured target amount, proportionally
 * across whatever the flask's solution is made of (a 30 % water / 70 % ethanol flask gives a
 * pipette sample in that same ratio — real lab technique, not "pick one ingredient"). The flask
 * stays behind in the grid with the rest of its solution.
 */
public class PipetteExtractRecipe extends CustomRecipe {

    public static final PipetteExtractRecipe INSTANCE = new PipetteExtractRecipe();
    public static final MapCodec<PipetteExtractRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipetteExtractRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<PipetteExtractRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private static @Nullable Pair<ItemStack, ItemStack> findPipetteAndFlask(CraftingInput input) {
        if (input.ingredientCount() != 2) return null;
        ItemStack pipette = null;
        ItemStack flask = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof PipetteItem && PipetteItem.isEmpty(stack)) pipette = stack;
            else if (stack.getItem() instanceof FlaskItem && !FlaskItem.isEmpty(stack)) flask = stack;
            else return null;
        }
        return (pipette != null && flask != null) ? Pair.of(pipette, flask) : null;
    }

    private static int drawAmount(ItemStack pipette, ItemStack flask) {
        return Math.min(PipetteItem.getTargetAmount(pipette),
                Math.min(FlaskItem.getTotalAmount(flask), PipetteItem.MAX_AMOUNT));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        return found != null && drawAmount(found.getFirst(), found.getSecond()) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        if (found == null) return ItemStack.EMPTY;
        ItemStack pipette = found.getFirst();
        ItemStack flask = found.getSecond();
        int draw = drawAmount(pipette, flask);
        if (draw <= 0) return ItemStack.EMPTY;

        Solution sample = FlaskItem.getSolution(flask).scaledTo(draw);
        ItemStack result = pipette.copyWithCount(1);
        PipetteItem.setSolution(result, sample);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        if (found == null) return remaining;
        ItemStack flask = found.getSecond();
        int draw = drawAmount(found.getFirst(), flask);
        if (draw <= 0) return remaining;

        int flaskTotal = FlaskItem.getTotalAmount(flask);
        Solution leftover = FlaskItem.getSolution(flask).scaledTo(flaskTotal - draw);

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack == flask || (!stack.isEmpty() && stack.getItem() instanceof FlaskItem)) {
                ItemStack next = stack.copyWithCount(1);
                FlaskItem.setSolution(next, leftover);
                remaining.set(i, next);
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<PipetteExtractRecipe> getSerializer() {
        return SERIALIZER;
    }
}
