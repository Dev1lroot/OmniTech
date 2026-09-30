/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Swappable cutting head for the {@link BoreItem}. The head decides what the bore can mine
 * (harvest tier), how fast, and how much energy each block costs; it wears down by one point
 * per block and can carry Unbreaking and Mending.
 *
 * <p>Registered from {@code data/omnitech/bore_head/*.json} by
 * {@link com.dev1lroot.mcmods.omnitech.BoreLoader}; stats are derived from the metal's real
 * Vickers hardness by {@code tools/bore_factory.py}.
 */
public class BoreHeadItem extends Item {

    /** Tier name shown in the tooltip: wood, stone, iron, diamond, netherite. */
    public final String tier;
    public final TagKey<Block> incorrectForDrops;
    public final float speed;
    /** Energy in kJ drawn from the battery per block mined. */
    public final int energyPerBlock;
    /** Vickers hardness of the head material, MPa. */
    public final int hardness;

    public BoreHeadItem(String tier, TagKey<Block> incorrectForDrops, float speed,
            int energyPerBlock, int hardness, Properties properties) {
        super(properties);
        this.tier = tier;
        this.incorrectForDrops = incorrectForDrops;
        this.speed = speed;
        this.energyPerBlock = energyPerBlock;
        this.hardness = hardness;
    }

    /** Same rules a pickaxe of this material would use (see {@code ToolMaterial.applyToolProperties}). */
    public float miningSpeed(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? speed : 1.0f;
    }

    public boolean isCorrectForDrops(BlockState state) {
        return !state.is(incorrectForDrops) && state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        appendStats(tooltip);
    }

    /** Head stats — shared with the bore tooltip. */
    public void appendStats(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.omnitech.bore_head.stats",
                        Component.translatable("tooltip.omnitech.bore_head.tier." + tier),
                        String.format("%.1f", speed), energyPerBlock)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.omnitech.bore_head.hardness", hardness)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
