/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The cinnamon tree's log with its bark stripped off — see
 * {@link CinnamonLogBlock#useItemOn}, which handles the actual axe
 * interaction directly and carries {@link CinnamonLogBlock#NATURAL} over
 * onto the resulting state itself.
 *
 * <p>Only a log that started out {@link CinnamonLogBlock#NATURAL natural}
 * (i.e. was never a player-placed item) is even eligible to random-tick, and
 * even then it only rolls a chance to grow its bark back while touching
 * another cinnamon log — stripped or not — directly above or below it. A
 * single isolated stripped log, natural or otherwise, never recovers.
 */
public class StrippedCinnamonLogBlock extends RotatedPillarBlock {
    private static final float REGROW_CHANCE = 0.25F;

    public StrippedCinnamonLogBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CinnamonLogBlock.NATURAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CinnamonLogBlock.NATURAL);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(CinnamonLogBlock.NATURAL);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasCinnamonLogAboveOrBelow(level, pos)) return;
        if (random.nextFloat() >= REGROW_CHANCE) return;

        Direction.Axis axis = state.getValue(AXIS);
        level.setBlockAndUpdate(pos, OmniTechBlocks.CINNAMON_LOG.get().defaultBlockState()
                .setValue(AXIS, axis)
                .setValue(CinnamonLogBlock.NATURAL, true));
    }

    private static boolean hasCinnamonLogAboveOrBelow(ServerLevel level, BlockPos pos) {
        return isCinnamonLog(level.getBlockState(pos.above())) || isCinnamonLog(level.getBlockState(pos.below()));
    }

    private static boolean isCinnamonLog(BlockState state) {
        return state.is(OmniTechBlocks.CINNAMON_LOG.get()) || state.is(OmniTechBlocks.STRIPPED_CINNAMON_LOG.get());
    }
}
