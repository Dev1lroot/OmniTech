/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Steel Scaffolding — a full-size steel lattice cube. The model shows only the
 * frame (cutout texture, with inward-facing faces so the far side shows
 * through), the collision box is a full block.
 *
 * <p>It can be climbed from any side: an entity pressed against any vertical
 * face climbs like on a ladder (see {@code LivingEntityScaffoldClimbMixin},
 * which calls {@link #isClimbableFrom}).
 */
public class SteelScaffoldingBlock extends Block {

    /** How close (in blocks) an entity must be to a face to grab the lattice. */
    private static final double REACH = 0.08;

    public SteelScaffoldingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    /** Neighbouring lattices share one wall instead of drawing two coplanar ones. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /**
     * Whether {@code entity} is pressed against the side of any steel scaffolding.
     * Only blocks overlapping the entity's height count, so standing on top of a
     * lattice is not climbing.
     */
    public static BlockPos isClimbableFrom(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        AABB reach = new AABB(box.minX - REACH, box.minY + 0.01, box.minZ - REACH,
                box.maxX + REACH, box.maxY - 0.01, box.maxZ + REACH);
        BlockGetter level = entity.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            if (level.getBlockState(pos).getBlock() instanceof SteelScaffoldingBlock) {
                return pos.immutable();
            }
        }
        return null;
    }
}
