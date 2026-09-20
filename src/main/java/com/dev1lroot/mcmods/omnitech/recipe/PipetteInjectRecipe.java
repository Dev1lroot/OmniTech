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
 * Crafting-table recipe: a filled {@link PipetteItem} + a {@link FlaskItem} → pours the
 * pipette's solution into the flask, clamped to whatever room the flask actually has. If it
 * doesn't all fit, the pipette keeps whatever's left over (same ratios, smaller total) — it
 * doesn't just vanish. The flask (now holding the merged solution) is the crafted result;
 * the pipette is what stays behind in the grid.
 */
public class PipetteInjectRecipe extends CustomRecipe {

    public static final PipetteInjectRecipe INSTANCE = new PipetteInjectRecipe();
    public static final MapCodec<PipetteInjectRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, PipetteInjectRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<PipetteInjectRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private static @Nullable Pair<ItemStack, ItemStack> findPipetteAndFlask(CraftingInput input) {
        if (input.ingredientCount() != 2) return null;
        ItemStack pipette = null;
        ItemStack flask = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof PipetteItem && !PipetteItem.isEmpty(stack)) pipette = stack;
            else if (stack.getItem() instanceof FlaskItem) flask = stack;
            else return null;
        }
        return (pipette != null && flask != null) ? Pair.of(pipette, flask) : null;
    }

    private static int transferAmount(ItemStack pipette, ItemStack flask) {
        return Math.min(PipetteItem.getTotalAmount(pipette), FlaskItem.getRemainingCapacity(flask));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        return found != null && transferAmount(found.getFirst(), found.getSecond()) > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        if (found == null) return ItemStack.EMPTY;
        ItemStack pipette = found.getFirst();
        ItemStack flask = found.getSecond();
        int transfer = transferAmount(pipette, flask);
        if (transfer <= 0) return ItemStack.EMPTY;

        Solution portion = PipetteItem.getSolution(pipette).scaledTo(transfer);
        Solution merged = FlaskItem.getSolution(flask).plus(portion);
        ItemStack result = flask.copyWithCount(1);
        FlaskItem.setSolution(result, merged);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Pair<ItemStack, ItemStack> found = findPipetteAndFlask(input);
        if (found == null) return remaining;
        ItemStack pipette = found.getFirst();
        int transfer = transferAmount(pipette, found.getSecond());
        if (transfer <= 0) return remaining;

        int pipetteTotal = PipetteItem.getTotalAmount(pipette);
        Solution leftover = PipetteItem.getSolution(pipette).scaledTo(pipetteTotal - transfer);

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack == pipette) {
                ItemStack next = stack.copyWithCount(1);
                PipetteItem.setSolution(next, leftover);
                remaining.set(i, next);
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<PipetteInjectRecipe> getSerializer() {
        return SERIALIZER;
    }
}
