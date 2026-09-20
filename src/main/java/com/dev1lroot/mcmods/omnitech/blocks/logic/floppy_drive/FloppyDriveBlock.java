/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive;

import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import com.dev1lroot.mcmods.omnitech.items.LogicGateTemplateItem;
import com.dev1lroot.mcmods.omnitech.util.LogicGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FloppyDriveBlock extends BaseEntityBlock
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_DISK = BooleanProperty.create("has_disk");

    public FloppyDriveBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_DISK, false)
        );
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloppyDriveBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_DISK);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HAS_DISK, false);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (player.isCrouching()) {
            return ejectDisk(state, level, pos, player);
        }
        if (stack.getItem() instanceof FloppyDiskItem) {
            // injectDisk
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof FloppyDriveBlockEntity be) {
                    ItemStack old = be.getDisk();
                    be.setDisk(stack.copyWithCount(1));
                    if (!old.isEmpty()) player.addItem(old);
                    stack.shrink(1);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    updateOutput(state, level, pos);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            // openGUI
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FloppyDriveBlockEntity fd) {
                ((ServerPlayer) player).openMenu(fd, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(fd.getDriveId());
                });
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private InteractionResult ejectDisk(BlockState state, Level level,
                                             BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof FloppyDriveBlockEntity be) {
                ItemStack template = be.getDisk();
                if (!template.isEmpty()) {
                    be.setDisk(ItemStack.EMPTY);
                    player.addItem(template);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    updateOutput(state, level, pos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void updateOutput(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        boolean hasDisk = false;

        if (level.getBlockEntity(pos) instanceof FloppyDriveBlockEntity be) {
            ItemStack template = be.getDisk();
            if (!template.isEmpty() && template.getItem() instanceof FloppyDiskItem ti) {
                hasDisk = true;
            }
        }

        BlockState newState = state
                .setValue(FACING, facing)
                .setValue(HAS_DISK, hasDisk);

        if (!newState.equals(state)) {
            level.setBlock(pos, newState, 2);
            level.updateNeighborsAt(pos, this);
        }
    }
}
