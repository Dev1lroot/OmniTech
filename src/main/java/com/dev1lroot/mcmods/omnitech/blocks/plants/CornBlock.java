/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A single, reusable block for every segment of a corn plant (1-3 tall).
 * {@link #PART} has three roles rather than a fixed per-height texture:
 * <ul>
 *   <li>{@code TOP} — always the topmost segment, purely a growth tip/cap.
 *       Random-ticks to grow the plant taller (up to {@link #MAX_HEIGHT}):
 *       relabels itself {@code BASE} and places a fresh {@code TOP} above.</li>
 *   <li>{@code BASE} — any segment below the top. Random-ticks to ripen into
 *       {@code GROWN}.</li>
 *   <li>{@code GROWN} — right-clicking it pops 1-3 corn and resets it to
 *       {@code BASE}, which can ripen again later — a renewable harvest, not
 *       a one-shot break.</li>
 * </ul>
 * A single freshly-planted/generated block is just {@code TOP} sitting on
 * the ground — nothing to harvest until it grows at least one {@code BASE}
 * segment beneath it.
 *
 * <p>Breaking <em>any</em> segment (see {@link #playerWillDestroy}) takes
 * the whole plant down with it — every connected segment above and below is
 * removed — and drops 1-3 corn for each segment that happened to be
 * {@code GROWN} at the time; segments that were still {@code BASE} or the
 * {@code TOP} cap give nothing.
 */
public class CornBlock extends VegetationBlock {
    public static final EnumProperty<CornBlock.Part> PART = EnumProperty.create("part", CornBlock.Part.class);
    private static final int MAX_HEIGHT = 3;
    private static final float GROW_CHANCE = 0.125F;
    private static final float RIPEN_CHANCE = 0.125F;
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

    public CornBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, Part.TOP));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level.getBlockState(pos.below()).is(this)) return true;
        return super.canSurvive(state, level, pos);
    }

    /** Right-clicking a {@code GROWN} segment harvests it without destroying the plant. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(PART) != Part.GROWN) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            int count = 1 + serverLevel.getRandom().nextInt(3);
            popResource(serverLevel, pos, new ItemStack(OmniTechItems.CORN.get(), count));
            serverLevel.setBlock(pos, state.setValue(PART, Part.BASE), 2);
            serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }

        return InteractionResult.SUCCESS;
    }

    /** Breaking any segment takes the whole connected column down with it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            destroyWholePlant(level, pos, !player.preventsBlockDrops());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void destroyWholePlant(Level level, BlockPos originPos, boolean giveDrops) {
        BlockPos top = originPos;
        while (level.getBlockState(top.above()).is(this)) top = top.above();
        BlockPos bottom = originPos;
        while (level.getBlockState(bottom.below()).is(this)) bottom = bottom.below();

        // Top-down: removing a segment only ever destabilizes canSurvive for whatever is
        // ABOVE it, never below, so processing top-to-bottom means every segment is still
        // genuinely intact (and its PART accurately readable) by the time we reach it —
        // nothing gets silently cascade-removed out from under us before we can drop for it.
        RandomSource random = level.getRandom();
        for (BlockPos p = top; p.getY() >= bottom.getY(); p = p.below()) {
            BlockState segment = level.getBlockState(p);
            if (!segment.is(this)) continue;

            if (giveDrops && segment.getValue(PART) == Part.GROWN) {
                popResource(level, p, new ItemStack(OmniTechItems.CORN.get(), 1 + random.nextInt(3)));
            }
            if (!p.equals(originPos)) {
                level.removeBlock(p, false);
            }
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(PART) != Part.GROWN;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(PART)) {
            case TOP -> growTaller(state, level, pos, random);
            case BASE -> ripen(state, level, pos, random);
            case GROWN -> {}
        }
    }

    private void growTaller(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos abovePos = pos.above();
        if (!level.isEmptyBlock(abovePos)) return;
        if (level.getRawBrightness(abovePos, 0) < 9) return;
        if (random.nextFloat() >= GROW_CHANCE) return;
        if (heightAt(level, pos) >= MAX_HEIGHT) return;

        level.setBlock(pos, state.setValue(PART, Part.BASE), 2);
        level.setBlockAndUpdate(abovePos, this.defaultBlockState());
    }

    private void ripen(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos.above(), 0) < 9) return;
        if (random.nextFloat() >= RIPEN_CHANCE) return;

        level.setBlock(pos, state.setValue(PART, Part.GROWN), 2);
    }

    /** Walks downward counting consecutive corn blocks, including this one. */
    private int heightAt(Level level, BlockPos pos) {
        int height = 1;
        BlockPos check = pos.below();
        while (level.getBlockState(check).is(this)) {
            height++;
            check = check.below();
        }
        return height;
    }

    public enum Part implements StringRepresentable {
        TOP("top"),
        BASE("base"),
        GROWN("grown");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
