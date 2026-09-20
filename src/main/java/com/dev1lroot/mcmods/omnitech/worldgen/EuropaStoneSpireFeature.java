/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Generates upward-pointing {@code europa_stone} spires rising from the ocean
 * floor into the subsurface water column.
 *
 * <p>Each feature invocation places a cluster of 4–9 spires near the origin.
 * Every spire grows from the topmost solid block (the ocean floor) upward
 * through water, tapering from a base radius of 1–2 blocks to a sharp tip.
 * Spires only overwrite water — they will not disturb existing ice or stone.
 *
 * <p>Placement should target Y ≈ 30–45 so the scanner can locate the ocean
 * floor reliably.
 */
public record EuropaStoneSpireFeature() implements Feature {

    public static final MapCodec<EuropaStoneSpireFeature> CODEC = MapCodec.unit(EuropaStoneSpireFeature::new);

    @Override
    public MapCodec<EuropaStoneSpireFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

        int spireCount = 4 + random.nextInt(6);  // 4–9 spires per cluster
        boolean anyPlaced = false;

        for (int s = 0; s < spireCount; s++) {
            // Scatter spires randomly within a 10-block radius of the origin.
            int ox = origin.getX() + random.nextInt(21) - 10;
            int oz = origin.getZ() + random.nextInt(21) - 10;

            // Find the ocean floor at this XZ: scan down from placement Y.
            int floorY = findFloor(level, ox, origin.getY(), oz);
            if (floorY < 5) continue;   // too close to bedrock

            // Spire must start in water (one block above solid floor).
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(ox, floorY + 1, oz);
            if (!level.getBlockState(mutable).is(Blocks.WATER)) continue;

            int height    = 6  + random.nextInt(10);  // 6–15 blocks tall
            int maxRadius = random.nextBoolean() ? 1 : 2;  // base radius 1 or 2

            BlockState spireBlock = OmniTechBlocks.EUROPA_STONE.get().defaultBlockState();
            boolean spireBuilt = false;

            for (int dy = 0; dy < height; dy++) {
                int y = floorY + 1 + dy;

                // Linear taper: full radius at base, zero at tip.
                float progress = (float) dy / Math.max(1, height - 1);
                int   radius   = Math.round(maxRadius * (1.0f - progress));

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz > radius * radius) continue;
                        mutable.set(ox + dx, y, oz + dz);
                        // Only place into water — never overwrite solid blocks.
                        if (level.getBlockState(mutable).is(Blocks.WATER)) {
                            level.setBlock(mutable, spireBlock, 2);
                            spireBuilt = true;
                        }
                    }
                }

                // Stop if we have left the water column (hit ice or open space).
                mutable.set(ox, y + 1, oz);
                BlockState above = level.getBlockState(mutable);
                if (!above.is(Blocks.WATER) && !above.isAir()) break;
            }

            if (spireBuilt) anyPlaced = true;
        }

        return anyPlaced;
    }

    /** Scans downward from {@code startY} to find the topmost solid block's Y. */
    private static int findFloor(WorldGenLevel level, int x, int startY, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 10; dy >= -20; dy--) {
            m.set(x, startY + dy, z);
            BlockState st = level.getBlockState(m);
            if (!st.isAir() && !st.is(Blocks.WATER) && !st.is(Blocks.LAVA)) {
                return startY + dy;
            }
        }
        return -1;
    }
}
