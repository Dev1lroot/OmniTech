/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid valve — right-click to cycle through 8 flow levels (0–7).
 *
 * <p>Level 0 = fully closed (0 mb/tick). Levels 1–7 map linearly to
 * ~143–1000 mb/tick. The valve connects to pipes on exactly two faces:
 * <ul>
 *   <li>{@code VERTICAL=false}: the two faces perpendicular to FACING (left/right)</li>
 *   <li>{@code VERTICAL=true}: UP and DOWN</li>
 * </ul>
 *
 * <p>A spinning wheel on the front face ({@code FACING} direction) is rendered
 * by {@link com.dev1lroot.mcmods.omnitech.client.ValveRenderer}; the wheel
 * angle is {@code LEVEL × 45°}.
 */
public class ValveBlock extends BaseEntityBlock implements IFluidContainer
{

    /** Direction the wheel faces (front of the valve). */
    public static final EnumProperty<Direction> FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * When {@code false} the two fluid ports are left/right relative to FACING.
     * When {@code true} the ports are UP and DOWN.
     */
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

    /** 0 = closed; 1–7 linearly maps to ~143–1000 mb/tick. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public ValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING,   Direction.NORTH)
                .setValue(VERTICAL, false)
                .setValue(LEVEL,    0));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext ctx) {
        return SHAPE;
    }

    // ── IFluidContainer ───────────────────────────────────────────────────────

    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        return isPortFace(state, face);
    }

    // ── Placement & interaction ───────────────────────────────────────────────

    /**
     * The wheel faces the direction the player is looking when placing.
     * Sneak-place to get VERTICAL variant.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction horizontal = ctx.getHorizontalDirection();
        boolean vertical = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        return defaultBlockState()
                .setValue(FACING, horizontal)
                .setValue(VERTICAL, vertical);
    }

    /** Right-click cycles LEVEL: 0 → 1 → … → 7 → 0. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int next = (state.getValue(LEVEL) + 1) % 8;
        level.setBlock(pos, state.setValue(LEVEL, next), 3);
        return InteractionResult.CONSUME;
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OmniTechBlockEntities.VALVE.get(),
                ValveBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VERTICAL, LEVEL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the two port directions (the faces fluid can enter/exit through).
     * {@code VERTICAL=false} → left and right relative to FACING.
     * {@code VERTICAL=true}  → UP and DOWN.
     */
    public static Direction[] getPorts(BlockState state) {
        if (state.getValue(VERTICAL)) {
            return new Direction[]{ Direction.UP, Direction.DOWN };
        }
        Direction facing = state.getValue(FACING);
        return new Direction[]{ facing.getClockWise(), facing.getCounterClockWise() };
    }

    /** Returns {@code true} if {@code face} is one of the two port faces. */
    public static boolean isPortFace(BlockState state, Direction face) {
        for (Direction port : getPorts(state)) {
            if (port == face) return true;
        }
        return false;
    }

    /**
     * Returns the flow rate in mb/tick for the given block state.
     * Level 0 → 0 mb/tick. Level 1–7 → linearly up to 1000 mb/tick.
     */
    public static int getFlowRate(BlockState state) {
        int level = state.getValue(LEVEL);
        if (level == 0) return 0;
        return Math.round(1000f * level / 7f);
    }
}
