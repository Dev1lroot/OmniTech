/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Places a single {@code omnitech:vanilla_vine} block, exactly like vanilla's
 * own {@link net.minecraft.world.level.levelgen.feature.VinesFeature} but
 * targeting our block instead of {@code minecraft:vine}. Added on top of
 * (not replacing) vanilla jungle vine generation, at a very low rate — see
 * {@code data/omnitech/worldgen/placed_feature/vanilla_vine.json} and its
 * biome modifier — so it occasionally turns up as a rare variant among the
 * regular vines.
 */
public record VanillaVineFeature() implements Feature {
    public static final MapCodec<VanillaVineFeature> CODEC = MapCodec.unit(VanillaVineFeature::new);

    @Override
    public MapCodec<VanillaVineFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!level.isEmptyBlock(origin)) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && VineBlock.isAcceptableNeighbour(level, origin.relative(direction), direction)) {
                level.setBlock(origin, OmniTechBlocks.VANILLA_VINE.get().defaultBlockState()
                        .setValue(VineBlock.getPropertyForFace(direction), true), 2);
                return true;
            }
        }

        return false;
    }
}
