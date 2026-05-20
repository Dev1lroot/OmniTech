/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

/**
 * Power Relay — bridges electric, analog, and logic cable networks on its two
 * facing axis faces (front and back) and disconnects when a redstone signal is
 * present.
 *
 * <p>No block entity is required: the relay is a pure routing element.
 * BFS traversal in {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil}
 * and {@link com.dev1lroot.mcmods.omnitech.util.AnalogNetworkUtil} recognises
 * this block and only continues in the two directions parallel to {@code FACING}.
 */
public class PowerRelayBlock extends Block {
    public static final MapCodec<PowerRelayBlock> CODEC = simpleCodec(PowerRelayBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty   POWERED = BlockStateProperties.POWERED;

    public PowerRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, look.getOpposite())
                .setValue(POWERED, powered);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, moving);
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
        }
    }

    /**
     * Returns {@code true} if a wire approaching from {@code dirFromWireToRelay} is
     * allowed to connect to this relay — i.e. it is touching the front or back face.
     */
    public static boolean allowsConnection(BlockState relayState, Direction dirFromWireToRelay) {
        Direction facing = relayState.getValue(FACING);
        return dirFromWireToRelay == facing || dirFromWireToRelay == facing.getOpposite();
    }

    /**
     * Returns the two directions (along the facing axis) that the BFS may
     * continue through when this relay is not powered.
     */
    public static Direction[] passthroughDirections(BlockState relayState) {
        Direction facing = relayState.getValue(FACING);
        return new Direction[]{ facing, facing.getOpposite() };
    }
}
