package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ReactorCell extends Block {

    public static final EnumProperty<ReactorCellState> CELL_STATE =
            EnumProperty.create("reactor_cell_state", ReactorCellState.class);
    public static final EnumProperty<ReactorCellType> CELL_TYPE =
            EnumProperty.create("reactor_cell_type", ReactorCellType.class);

    public ReactorCell(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(CELL_STATE, ReactorCellState.COOL)
                .setValue(CELL_TYPE,  ReactorCellType.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL_STATE, CELL_TYPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ReactorPort.openFromStructure(level, pos, (ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
                                                BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ReactorStructure.invalidateNearbyMaster(level, pos);
    }
}
