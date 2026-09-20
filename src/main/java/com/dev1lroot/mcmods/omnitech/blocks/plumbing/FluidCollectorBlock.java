/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid Collector block.
 *
 * <p>Placed like an observer — {@code FACING} is the output face (fluid is pushed
 * toward pipes there); the opposite face (back) detects the adjacent block and
 * determines what fluid to produce:
 * <ul>
 *   <li>Adjacent fluid source block (water, lava, …) → produces that fluid.</li>
 *   <li>Adjacent air → produces {@code omnitech:air}.</li>
 *   <li>Any other block → looks up a {@code fluid_collector_recipe} for that block type.</li>
 * </ul>
 *
 * <p>Fluids are never consumed from the source — the collector produces them at a
 * fixed rate into an internal output tank and then pushes them into the output network.
 *
 * <p>Can be waterlogged.
 */
public class FluidCollectorBlock extends BaseEntityBlock implements IFluidContainer, SimpleWaterloggedBlock {

    /** All 6 directions — placed like an observer. Output = FACING, sensor = FACING.getOpposite(). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty   WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public FluidCollectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING,      Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    // ── IFluidContainer ───────────────────────────────────────────────────────

    /** Only the output (front) face connects to pipes. */
    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    // ── BaseEntityBlock ───────────────────────────────────────────────────────

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    /**
     * Observer-style placement: you aim at the block you want to collect from,
     * right-click its face, and the output side faces you.
     * {@code FACING = clickedFace}: the direction FROM the source block TO the
     * new collector = the output direction.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState()
                .setValue(FACING,      ctx.getClickedFace())
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    // ── Waterlogging ──────────────────────────────────────────────────────────

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false)
                                           : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction,
                neighborPos, neighborState, random);
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidCollectorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.FLUID_COLLECTOR.get(),
                        FluidCollectorBlockEntity::serverTick);
    }
}
