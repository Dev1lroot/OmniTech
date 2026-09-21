/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The cinnamon tree's log. Identical to a plain {@link RotatedPillarBlock}
 * except for {@link #NATURAL}, which records whether this exact log came
 * from tree generation rather than a player placing the item — the
 * configured feature ({@code data/omnitech/worldgen/feature/cinnamon_tree.json})
 * sets it {@code true} on its trunk, while {@link #getStateForPlacement}
 * (inherited, via {@link #defaultBlockState()}) always leaves it {@code false}
 * for a hand-placed block.
 *
 * <p>Right-clicking with an axe strips the bark directly here rather than
 * through the vanilla stripping mechanism — this MC version resolves that
 * through a {@code BlockTransformer} data component chained off a
 * {@code neoforge:strippables} data map, and that pipeline turned out not to
 * pick up modded log entries reliably. Handling it ourselves also makes the
 * bonus cinnamon drop trivial, instead of needing a separate hook for it.
 */
public class CinnamonLogBlock extends RotatedPillarBlock {
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");

    public CinnamonLogBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(NATURAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NATURAL);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack itemStack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!itemStack.is(ItemTags.AXES)) {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockState strippedState = OmniTechBlocks.STRIPPED_CINNAMON_LOG.get().defaultBlockState()
                    .setValue(AXIS, state.getValue(AXIS))
                    .setValue(NATURAL, state.getValue(NATURAL));
            serverLevel.setBlock(pos, strippedState, 11);
            serverLevel.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);

            int count = 1 + serverLevel.getRandom().nextInt(3);
            Block.popResource(serverLevel, pos, new ItemStack(OmniTechItems.CINNAMON.get(), count));

            if (!player.hasInfiniteMaterials()) {
                itemStack.hurtAndBreak(1, player, hand);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
