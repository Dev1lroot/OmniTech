/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Generates a rare hydrothermal trench in Europa's subsurface ocean floor.
 *
 * <p>The trench is a narrow, elongated rift carved downward through the solid rock
 * below the ocean, fully filled with water, with several {@link Blocks#MAGMA_BLOCK}
 * patches at the very bottom to simulate hydrothermal vent activity.
 *
 * <p>Placement should target Y ≈ 27 (the stone–water boundary in Europa's noise
 * settings) so the feature carves downward into solid rock.  The feature is rare
 * by design — use {@code minecraft:rarity_filter} in the placed-feature JSON.
 */
public class EuropaTrenchFeature extends Feature<NoneFeatureConfiguration> {

    public EuropaTrenchFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel  level  = ctx.level();
        BlockPos       origin = ctx.origin();
        RandomSource   random = ctx.random();

        // ── Find the actual ocean floor ────────────────────────────────────────
        // Scan a window around the placement Y to find the topmost solid block.
        BlockPos.MutableBlockPos mutable = origin.mutable();
        int floorY = -1;
        for (int dy = 8; dy >= -15; dy--) {
            mutable.set(origin.getX(), origin.getY() + dy, origin.getZ());
            if (isSolid(level.getBlockState(mutable))) {
                floorY = origin.getY() + dy;
                break;
            }
        }
        if (floorY < 10) return false;   // too close to bedrock — abort

        // ── Trench parameters ──────────────────────────────────────────────────
        int length    = 75 + random.nextInt(75);  // 75–149 blocks long
        int halfWidth = 9  + random.nextInt(9);   // 9–17 — gives total width 19–35 at centre
        int depth     = 14 + random.nextInt(8);   // 14–21 blocks deep (unchanged)

        // Random rift axis: true = rift runs N↔S (varies along Z), false = E↔W (varies along X)
        boolean alongZ = random.nextBoolean();

        int bottomY = Math.max(4, floorY - depth);
        BlockState waterState = Blocks.WATER.defaultBlockState();
        boolean carved = false;

        // ── Carve the trench ───────────────────────────────────────────────────
        // Walk the length axis; taper cross-section width toward both ends.
        for (int l = -(length / 2); l <= length / 2; l++) {
            float taper = 1.0f - (float) Math.abs(l) / ((length / 2.0f) + 1.0f);
            int   w     = Math.round(halfWidth * taper);

            for (int side = -w; side <= w; side++) {
                int dx = alongZ ? side : l;
                int dz = alongZ ? l    : side;

                for (int y = floorY; y >= bottomY; y--) {
                    mutable.set(origin.getX() + dx, y, origin.getZ() + dz);
                    BlockState existing = level.getBlockState(mutable);
                    if (existing.is(Blocks.BEDROCK)) break;    // never carve bedrock
                    if (isSolid(existing)) {
                        level.setBlock(mutable, waterState, 2);
                        carved = true;
                    }
                }
            }
        }

        if (!carved) return false;

        // ── Clear the water column above the trench opening ───────────────────
        // The trench runs at decoration step 7, after stone spires (step 4), so
        // any spires that grew above the trench footprint are still solid.
        // Replace everything solid (but not bedrock) in the 20 blocks above
        // the floor with water so the trench entrance is unobstructed.
        for (int l = -(length / 2); l <= length / 2; l++) {
            float taper = 1.0f - (float) Math.abs(l) / ((length / 2.0f) + 1.0f);
            int   w     = Math.round(halfWidth * taper);

            for (int side = -w; side <= w; side++) {
                int dx = alongZ ? side : l;
                int dz = alongZ ? l    : side;

                for (int dy = 1; dy <= 20; dy++) {
                    mutable.set(origin.getX() + dx, floorY + dy, origin.getZ() + dz);
                    BlockState above = level.getBlockState(mutable);
                    if (isSolid(above) && !above.is(Blocks.BEDROCK)) {
                        level.setBlock(mutable, waterState, 2);
                    }
                }
            }
        }

        // ── Place magma blocks on the trench floor ─────────────────────────────
        // Scatter 3–7 magma blocks at the very bottom of the trench to represent
        // hydrothermal activity heating Europa's hidden ocean from below.
        int magmaTarget = 3 + random.nextInt(5);
        int placed = 0;
        int attempts = 0;

        while (placed < magmaTarget && attempts < magmaTarget * 8) {
            attempts++;
            int l    = random.nextInt(length) - length / 2;
            int side = halfWidth > 0 ? random.nextInt(halfWidth * 2 + 1) - halfWidth : 0;
            int dx   = alongZ ? side : l;
            int dz   = alongZ ? l    : side;

            // Walk down the column to find the first water-block that sits on solid ground.
            for (int y = floorY; y >= bottomY; y--) {
                mutable.set(origin.getX() + dx, y, origin.getZ() + dz);
                if (!level.getBlockState(mutable).is(Blocks.WATER)) continue;

                BlockPos belowPos = mutable.below();
                if (isSolid(level.getBlockState(belowPos))) {
                    level.setBlock(mutable, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                    placed++;
                    break;
                }
            }
        }

        return true;
    }

    /** Returns {@code true} if the block is neither air nor a fluid — i.e. it can be carved out. */
    private static boolean isSolid(BlockState state) {
        return !state.isAir() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA);
    }
}
