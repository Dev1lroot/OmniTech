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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * Shared behaviour for the tomato and jalapeño bushes — a simplified
 * {@link net.minecraft.world.level.block.SweetBerryBushBlock}: just two
 * states (unripe/{@link #RIPE}) instead of four growth stages, no
 * stuck-in-block slowdown or thorn damage (that's {@link NettleBlock}'s
 * job), and right-clicking while ripe pops the fruit and resets to unripe
 * instead of stepping an age counter down by one.
 */
public class BerryPlantBlock extends VegetationBlock {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final float RIPEN_CHANCE = 0.2F;
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 13.0);

    /** Lazy on purpose — resolved only when actually needed (harvest/pick-block), never at class-init time. */
    private final Supplier<Item> fruit;

    public BerryPlantBlock(Supplier<Item> fruit, BlockBehaviour.Properties properties) {
        super(properties);
        this.fruit = fruit;
        this.registerDefaultState(this.stateDefinition.any().setValue(RIPE, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPE);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(RIPE);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos.above(), 0) < 9) return;
        if (random.nextFloat() >= RIPEN_CHANCE) return;

        level.setBlock(pos, state.setValue(RIPE, true), 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(RIPE)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            int count = 1 + serverLevel.getRandom().nextInt(3);
            Block.popResource(serverLevel, pos, new ItemStack(this.fruit.get(), count));
            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                    1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
            BlockState unripe = state.setValue(RIPE, false);
            serverLevel.setBlock(pos, unripe, 2);
            serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(this.fruit.get());
    }
}
