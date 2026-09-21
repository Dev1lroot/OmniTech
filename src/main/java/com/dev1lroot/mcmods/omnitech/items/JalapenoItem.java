/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A {@link BlockItem} (plants {@code omnitech:jalapeno}) that's also food —
 * eating uses the ordinary {@code Item.use}/{@code CONSUMABLE} path
 * unmodified, but finishing it sets the eater on fire for a few seconds.
 */
public class JalapenoItem extends BlockItem {

    public JalapenoItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(itemStack, level, entity);
        if (!level.isClientSide()) {
            entity.igniteForSeconds(3.0F);
        }
        return result;
    }
}
