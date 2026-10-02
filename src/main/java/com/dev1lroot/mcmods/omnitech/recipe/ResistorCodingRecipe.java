/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipe;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.items.ResistorItem;
import com.dev1lroot.mcmods.omnitech.pcb.ResistorCode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints colour bands on a blank resistor: one resistor plus 3–6 band items, read in
 * grid order (left to right, top to bottom). Dyes give the digit colours; anything in
 * {@code #omnitech:resistor_band/gold} or {@code /silver} gives a metallic band.
 * Only valid colour codes craft (see {@link ResistorCode#decode}).
 */
public class ResistorCodingRecipe extends CustomRecipe {

    public static final ResistorCodingRecipe INSTANCE = new ResistorCodingRecipe();
    public static final MapCodec<ResistorCodingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, ResistorCodingRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<ResistorCodingRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public static final TagKey<Item> GOLD_BAND =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(OmniTech.MODID, "resistor_band/gold"));
    public static final TagKey<Item> SILVER_BAND =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(OmniTech.MODID, "resistor_band/silver"));

    /** The band colour an ingredient paints, or null if it is not a band ingredient. */
    public static ResistorCode.@Nullable Band bandOf(ItemStack stack) {
        if (stack.is(GOLD_BAND)) return ResistorCode.Band.GOLD;
        if (stack.is(SILVER_BAND)) return ResistorCode.Band.SILVER;
        if (!(stack.getItem() instanceof DyeItem)) return null;
        DyeColor c = DyeColor.getColor(stack);
        if (c == null) return null;
        return switch (c) {
            case BLACK  -> ResistorCode.Band.BLACK;
            case BROWN  -> ResistorCode.Band.BROWN;
            case RED    -> ResistorCode.Band.RED;
            case ORANGE -> ResistorCode.Band.ORANGE;
            case YELLOW -> ResistorCode.Band.YELLOW;
            case GREEN  -> ResistorCode.Band.GREEN;
            case BLUE   -> ResistorCode.Band.BLUE;
            case PURPLE -> ResistorCode.Band.VIOLET;
            case GRAY   -> ResistorCode.Band.GREY;
            case WHITE  -> ResistorCode.Band.WHITE;
            default     -> null; // no IEC colour for lime, cyan, pink, magenta, light blue/gray
        };
    }

    /** The band list in reading order, or null when the grid isn't one blank resistor + bands. */
    private static @Nullable List<ResistorCode.Band> read(CraftingInput input) {
        boolean resistor = false;
        List<ResistorCode.Band> bands = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof ResistorItem) {
                if (resistor || !ResistorItem.getBands(s).isEmpty()) return null; // one blank only
                resistor = true;
                continue;
            }
            ResistorCode.Band b = bandOf(s);
            if (b == null) return null;
            bands.add(b);
        }
        return resistor ? bands : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ResistorCode.Band> bands = read(input);
        return bands != null && ResistorCode.decode(bands) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        List<ResistorCode.Band> bands = read(input);
        if (bands == null || ResistorCode.decode(bands) == null) return ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() instanceof ResistorItem) return ResistorItem.withBands(s.copyWithCount(1), ResistorCode.toCodes(bands));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<ResistorCodingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
