/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.gui.guidebook.GuidebookLoader;
import com.dev1lroot.mcmods.omnitech.gui.guidebook.GuidebookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class GuidebookItem extends Item {

    public GuidebookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            Minecraft.getInstance().gui.setScreen(new GuidebookScreen(GuidebookLoader.getPages()));
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
