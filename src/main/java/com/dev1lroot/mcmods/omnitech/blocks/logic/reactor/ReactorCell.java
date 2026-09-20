/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")

public class ReactorCell extends BaseEntityBlock {

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
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL_STATE, CELL_TYPE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorCellBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ReactorPort.openFromStructure(level, pos, (ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            // Nuclear explosion if reactor is hot and player breaks a cell
            ReactorStructure.findMasterNear(level, pos).ifPresent(master -> {
                if (master.isFormed() && master.getCoreTemperature() >= 300 && !master.hasExploded()) {
                    master.markExploded();
                    NuclearExplosion.trigger((ServerLevel) level, pos);
                }
            });

            if (!player.isCreative()
                    && level.getBlockEntity(pos) instanceof ReactorCellBlockEntity cbe
                    && !cbe.isEmpty()) {
                ItemStack stack = cbe.removeItemNoUpdate(0);
                ReactorRodItem.removeReactorTags(stack);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
                                                BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ReactorStructure.invalidateNearbyMaster(level, pos);
    }
}
