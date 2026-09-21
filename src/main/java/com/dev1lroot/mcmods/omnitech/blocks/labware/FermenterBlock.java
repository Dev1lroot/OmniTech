/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Fermenter block — see {@link FermenterBlockEntity}. The vessel is symmetric, so it has no
 * facing: fluid goes in and out through any face, items are handled through the GUI (or a hopper).
 * Right-clicking with a bucket fills or drains the mixture directly, like the Fluid Tank; an empty
 * pipette or flask takes a proportional sample of it.
 */
public class FermenterBlock extends BaseEntityBlock implements IFluidContainer {

    public FermenterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FermenterBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.FERMENTER.get(),
                        FermenterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BucketItem) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                boolean interacted = FluidUtil.interactWithFluidHandler(
                        player, hand, level, pos, hit.getDirection());
                return interacted ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
        }
        // Empty pipette / flask -> take a sample of the mixture; if there is nothing to take
        // (empty vessel), fall through and open the GUI like a bare-handed click.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            boolean pipette = held.getItem() instanceof PipetteItem && PipetteItem.isEmpty(held);
            boolean flask   = held.getItem() instanceof FlaskItem && FlaskItem.isEmpty(held);
            if (!pipette && !flask) continue;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof FermenterBlockEntity fermenter) {
                boolean filled = pipette ? PipetteItem.tryFillFromFermenter(held, fermenter)
                                         : FlaskItem.tryFillFromFermenter(held, fermenter);
                if (filled) return InteractionResult.SUCCESS;
            }
        }
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FermenterBlockEntity fermenter) {
                ((ServerPlayer) player).openMenu(fermenter, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
