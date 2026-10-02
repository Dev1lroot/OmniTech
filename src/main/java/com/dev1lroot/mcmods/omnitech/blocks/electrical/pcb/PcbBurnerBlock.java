/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Two-block-tall PCB station (the PCB Burner). Works like a door: the lower half owns the
 * block entity and draws the whole 32-pixel model, the upper half is an invisible stand-in
 * that forwards clicks down. Losing either half removes the other; only the lower half
 * drops the item (loot table matches {@code half=lower}).
 */
public class PcbBurnerBlock extends PcbStationBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** True for the short UV flash after each exposed board; the block glows at full light meanwhile. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public PcbBurnerBlock(Properties props, BiFunction<BlockPos, BlockState, BlockEntity> factory, boolean ticks) {
        super(props, factory, ticks);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER).setValue(LIT, false));
    }

    private static boolean isUpper(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.UPPER; }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return isUpper(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isUpper(state) ? null : super.newBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!isUpper(state)) return super.useWithoutItem(state, level, pos, player, hit);
        BlockPos below = pos.below();
        return super.useWithoutItem(level.getBlockState(below), level, below, player, hit);
    }

    // ── Two halves ────────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return super.getStateForPlacement(ctx).setValue(HALF, DoubleBlockHalf.LOWER).setValue(LIT, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!isUpper(state) || state.getBlock() != this) return super.canSurvive(state, level, pos);
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && !isUpper(below);
    }

    /** A half whose partner is gone turns to air (dropping the item if it was the lower one). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction dir, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        boolean towardsPartner = isUpper(state) ? dir == Direction.DOWN : dir == Direction.UP;
        if (towardsPartner && !(neighbour.is(this) && neighbour.getValue(HALF) != state.getValue(HALF))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbour, random);
    }

    /** Creative players breaking the top must not get the bottom's drop either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && isUpper(state)) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && !isUpper(lower)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Switches the flash on or off on both halves (the lower one at {@code lowerPos}). */
    public static void setLit(Level level, BlockPos lowerPos, boolean lit) {
        for (BlockPos p : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof PcbBurnerBlock && s.getValue(LIT) != lit) {
                level.setBlock(p, s.setValue(LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF, LIT);
    }
}
