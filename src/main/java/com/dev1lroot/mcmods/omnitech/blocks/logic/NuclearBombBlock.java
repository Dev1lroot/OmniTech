/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic;

import com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Triggers a nuclear explosion when powered by a redstone signal.
 * The block removes itself before detonating.
 */
public class NuclearBombBlock extends Block {

    public NuclearBombBlock(Properties props) {
        super(props);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                 Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        tryTrigger(level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        tryTrigger(level, pos);
    }

    private static void tryTrigger(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (level.getBestNeighborSignal(pos) <= 0) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        NuclearExplosion.trigger(sl, pos);
    }
}
