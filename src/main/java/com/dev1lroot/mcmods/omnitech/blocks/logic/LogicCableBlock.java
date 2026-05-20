/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_wire.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay.PowerRelayBlock;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Passive connector block that the LogicMachine uses to find GPIO ports via BFS. */
public class LogicCableBlock extends Block
{
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty UP    = BooleanProperty.create("up");
    public static final BooleanProperty DOWN  = BooleanProperty.create("down");

    // ── VoxelShapes (wire cross-section: 4×4, occupying positions 6-10) ───────
    private static final VoxelShape CORE      = Block.box( 4,  4,  4, 12, 12, 12);
    private static final VoxelShape ARM_NORTH = Block.box( 4,  4,  0, 12, 12,  4);
    private static final VoxelShape ARM_SOUTH = Block.box( 4,  4, 12, 12, 12, 16);
    private static final VoxelShape ARM_EAST  = Block.box(10,  4,  4, 16, 12, 12);
    private static final VoxelShape ARM_WEST  = Block.box( 0,  4,  4,  4, 12, 12);
    private static final VoxelShape ARM_UP    = Block.box( 4, 10,  4, 12, 16, 12);
    private static final VoxelShape ARM_DOWN  = Block.box( 4,  0,  4, 12,  4, 12);

    public LogicCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return calculateState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
                                  ScheduledTickAccess scheduledTickAccess, BlockPos pos,
                                  Direction direction, BlockPos neighborPos, BlockState neighborState,
                                  RandomSource random) {
        return state.setValue(propertyFor(direction), canConnectTo(level, neighborPos, direction, neighborState));
    }

    // ── VoxelShape ────────────────────────────────────────────────────────────

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, ARM_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
        if (state.getValue(EAST))  shape = Shapes.or(shape, ARM_EAST);
        if (state.getValue(WEST))  shape = Shapes.or(shape, ARM_WEST);
        if (state.getValue(UP))    shape = Shapes.or(shape, ARM_UP);
        if (state.getValue(DOWN))  shape = Shapes.or(shape, ARM_DOWN);
        return shape;
    }

    // ── Connection logic ──────────────────────────────────────────────────────

    private static boolean canConnectTo(LevelReader level, BlockPos neighborPos,
            Direction fromCableToNeighbor, BlockState neighborState) {
        if (neighborState.getBlock() instanceof LogicCableBlock) return true;
        if (neighborState.getBlock() instanceof PowerRelayBlock)
            return PowerRelayBlock.allowsConnection(neighborState, fromCableToNeighbor);
        return false; // TODO: logic machine / GPIO port interfaces
    }

    /** Recomputes all 6 connection properties from the current level state. */
    private static BlockState calculateState(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            state = state.setValue(propertyFor(dir),
                    canConnectTo(level, neighborPos, dir, level.getBlockState(neighborPos)));
        }
        return state;
    }

    /** Maps a {@link Direction} to the corresponding connection {@link BooleanProperty}. */
    public static BooleanProperty propertyFor(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            case UP    -> UP;
            case DOWN  -> DOWN;
        };
    }
}
