/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * Leaves of a fruit tree (lemon): ordinary {@link LeavesBlock} decay, plus fruit that ripens on
 * the leaves of a living tree and is picked by right-clicking — the tree-top version of
 * {@link BerryPlantBlock}. Only natural leaves still attached to a trunk bear fruit; leaves a
 * player placed (persistent) or that are cut off from the tree never do.
 */
public class FruitLeavesBlock extends LeavesBlock {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final float RIPEN_CHANCE = 0.05F;

    /** Lazy on purpose — resolved only on harvest, never at class-init time. */
    private final Supplier<Item> fruit;

    public FruitLeavesBlock(Supplier<Item> fruit, AmbientLeavesBlockSoundPlayer sounds, BlockBehaviour.Properties properties) {
        super(sounds, properties);
        this.fruit = fruit;
        this.registerDefaultState(this.defaultBlockState().setValue(RIPE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RIPE);
    }

    private static boolean canBearFruit(BlockState state) {
        return !state.getValue(RIPE) && !state.getValue(PERSISTENT) && state.getValue(DISTANCE) < 7;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || canBearFruit(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this) || !canBearFruit(state)) return;
        if (level.getRawBrightness(pos.above(), 0) < 9 || random.nextFloat() >= RIPEN_CHANCE) return;
        level.setBlock(pos, state.setValue(RIPE, true), 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(RIPE)) return super.useWithoutItem(state, level, pos, player, hitResult);

        if (level instanceof ServerLevel serverLevel) {
            int count = 1 + serverLevel.getRandom().nextInt(2);
            Block.popResource(serverLevel, pos, new ItemStack(this.fruit.get(), count));
            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                    1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
            serverLevel.setBlock(pos, state.setValue(RIPE, false), 2);
            serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
