/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Hydrothermal "black smoker" chimney on the floor of Europa's sulfur-vent biome.
 *
 * <p>A tapering column of basalt and blackstone, crusted with vanilla sulfur, around a
 * magma throat capped by vanilla {@code potent_sulfur} — magma below it makes it a
 * periodic geyser underwater — and a sulfur apron around the base. The
 * placed feature is expected to put the origin on the seafloor (first water block
 * above solid ground). Only water is ever replaced.
 */
public record EuropaSulfurChimneyFeature() implements Feature {

    public static final MapCodec<EuropaSulfurChimneyFeature> CODEC = MapCodec.unit(EuropaSulfurChimneyFeature::new);

    @Override
    public MapCodec<EuropaSulfurChimneyFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!level.getBlockState(origin).is(Blocks.WATER)) return false;

        BlockState sulfur = Blocks.SULFUR.defaultBlockState();
        int height = 5 + random.nextInt(14);          // 5–18 tall
        float baseRadius = 1.2f + random.nextFloat() * 1.3f;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int dy = 0; dy < height; dy++) {
            float r = baseRadius * (1f - 0.7f * dy / height);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    float d = (float) Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getBlockState(m).is(Blocks.WATER)) continue;
                    // hollow throat, sulfur-crusted rim, dark rock body
                    BlockState state = d < r - 1.1f && dy < height - 1 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : random.nextFloat() < 0.3f ? sulfur
                            : random.nextBoolean() ? Blocks.BASALT.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState();
                    level.setBlock(m, state, 2);
                    placed = true;
                }
            }
        }
        if (!placed) return false;

        // vent: magma under a potent sulfur cap = vanilla periodic geyser
        m.set(origin.getX(), origin.getY() + height - 1, origin.getZ());
        level.setBlock(m, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        m.set(origin.getX(), origin.getY() + height, origin.getZ());
        if (level.getBlockState(m).is(Blocks.WATER)) {
            level.setBlock(m, Blocks.POTENT_SULFUR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.PotentSulfurBlock.STATE,
                              net.minecraft.world.level.block.state.properties.PotentSulfurState.DORMANT), 2);
        }

        // sulfur apron on the surrounding seafloor
        int apron = 3 + random.nextInt(3);
        for (int dx = -apron; dx <= apron; dx++) {
            for (int dz = -apron; dz <= apron; dz++) {
                if (dx * dx + dz * dz > apron * apron || random.nextFloat() < 0.35f) continue;
                m.set(origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz);
                BlockState below = level.getBlockState(m);
                if (!below.isAir() && below.getFluidState().isEmpty()) {
                    level.setBlock(m, sulfur, 2);
                }
            }
        }
        return true;
    }
}
