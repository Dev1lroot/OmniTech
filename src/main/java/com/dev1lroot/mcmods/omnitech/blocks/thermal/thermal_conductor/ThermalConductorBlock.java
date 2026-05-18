/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Thermal Conductor — omnidirectional heat/cold pipe with a BER that renders
 * a sin()-pulsing red/blue glow overlay showing live thermal flow.
 *
 * <p>The block is stateless in blockstate terms (only 6 directional connection
 * booleans). The {@link ThermalConductorBlockEntity} stores {@code heatFlow}
 * and {@code coldFlow} values that the {@link com.dev1lroot.mcmods.omnitech.util.ThermalNetworkUtil}
 * writes each tick. The BER reads those values and animates the overlay.
 */
public class ThermalConductorBlock extends BaseEntityBlock {

    public static final MapCodec<ThermalConductorBlock> CODEC = simpleCodec(ThermalConductorBlock::new);

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty UP    = BooleanProperty.create("up");
    public static final BooleanProperty DOWN  = BooleanProperty.create("down");

    // ── VoxelShapes — 4×4 cross-section, identical to ElectricWire ───────────
    private static final VoxelShape CORE      = Block.box( 4,  4,  4, 12, 12, 12);
    private static final VoxelShape ARM_NORTH = Block.box( 4,  4,  0, 12, 12,  4);
    private static final VoxelShape ARM_SOUTH = Block.box( 4,  4, 12, 12, 12, 16);
    private static final VoxelShape ARM_EAST  = Block.box(10,  4,  4, 16, 12, 12);
    private static final VoxelShape ARM_WEST  = Block.box( 0,  4,  4,  4, 12, 12);
    private static final VoxelShape ARM_UP    = Block.box( 4, 10,  4, 12, 16, 12);
    private static final VoxelShape ARM_DOWN  = Block.box( 4,  0,  4, 12,  4, 12);

    public ThermalConductorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    // Block model renders normally; BER adds the glow overlay on top.
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    // ── Block state ───────────────────────────────────────────────────────────

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
        return state.setValue(propertyFor(direction), canConnectTo(level, neighborPos, direction));
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

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThermalConductorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.THERMAL_CONDUCTOR.get(),
                        ThermalConductorBlockEntity::serverTick);
    }

    // ── Connection logic ──────────────────────────────────────────────────────

    /**
     * Returns true if a conductor arm in {@code dir} should extend toward {@code neighborPos}.
     *
     * <p>Radiators are directional: a conductor only connects to a radiator's back face.
     * The back is {@code FACING.getOpposite()}, so the conductor is on the back when
     * {@code dir == radiatorFacing} (the conductor lies in the direction the front faces).
     */
    public static boolean canConnectTo(LevelReader level, BlockPos neighborPos, Direction dir) {
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof ThermalConductorBlock) return true;
        if (neighborState.getBlock() instanceof
                com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlock) {
            return dir == neighborState.getValue(
                    com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlock.FACING);
        }
        BlockEntity be = level.getBlockEntity(neighborPos);
        return be instanceof IThermalNode || be instanceof IHeatReceiver || be instanceof IColdReceiver;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThermalConductorBlockEntity tce) {
                sp.sendSystemMessage(
                    Component.literal(String.format("%.1f °C", tce.getTemperature())), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static BlockState calculateState(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(propertyFor(dir), canConnectTo(level, pos.relative(dir), dir));
        }
        return state;
    }

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
