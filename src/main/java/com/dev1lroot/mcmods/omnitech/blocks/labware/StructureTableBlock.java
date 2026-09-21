/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Structure Table — sketch a molecule's structural formula, get its systematic name, print it
 * onto paper as a {@link com.dev1lroot.mcmods.omnitech.items.ChemicalFormulaItem}.
 *
 * <p>Deliberately has no block entity: the sketch is client-local scratch work (see
 * {@link com.dev1lroot.mcmods.omnitech.gui.StructureTableScreen}), and the only thing that ever
 * touches the server is the single "print" packet once the player is done, which pulls its
 * paper straight from the player's own inventory — there's nothing here that needs to persist
 * on the block itself.
 */
public class StructureTableBlock extends Block {

    public StructureTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new com.dev1lroot.mcmods.omnitech.gui.StructureTableScreen());
        }
        return InteractionResult.SUCCESS;
    }
}
