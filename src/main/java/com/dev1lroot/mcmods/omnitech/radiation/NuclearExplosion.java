/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Nuclear explosion: three-zone spherical block destruction.
 * Zone 1 (0–32 blocks): 100% destruction, executed immediately.
 * Zone 2 (32–64 blocks): 75% destruction, queued for gradual processing.
 * Zone 3 (64–128 blocks): 25% destruction (sampled), queued for gradual processing.
 */
public class NuclearExplosion {

    private static final int R1 = 32;
    private static final int R2 = 64;
    private static final int R3 = 128;

    // Zone 3 uses random sampling rather than full iteration for performance.
    private static final int ZONE3_SAMPLES = 25_000;

    public static void trigger(ServerLevel level, BlockPos center) {
        RadiationSavedData data = RadiationSavedData.get(level);
        data.addCenter(center.immutable());

        // ── Zone 1: 0–32 blocks, 100%, immediate ──────────────────────────────
        int r1sq = R1 * R1;
        for (int dx = -R1; dx <= R1; dx++) {
            for (int dy = -R1; dy <= R1; dy++) {
                for (int dz = -R1; dz <= R1; dz++) {
                    if (dx*dx + dy*dy + dz*dz > r1sq) continue;
                    BlockPos target = center.offset(dx, dy, dz);
                    if (!level.getBlockState(target).isAir()
                            && level.getBlockState(target).getDestroySpeed(level, target) >= 0) {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }

        // ── Zone 2: 32–64 blocks, 75%, queued ────────────────────────────────
        List<BlockPos> zone2 = new ArrayList<>();
        int r2sq = R2 * R2;
        for (int dx = -R2; dx <= R2; dx++) {
            for (int dy = -R2; dy <= R2; dy++) {
                for (int dz = -R2; dz <= R2; dz++) {
                    int dsq = dx*dx + dy*dy + dz*dz;
                    if (dsq <= r1sq || dsq > r2sq) continue;
                    if (level.getRandom().nextFloat() < 0.75f) {
                        zone2.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        data.queueExplosion(zone2);

        // ── Zone 3: 64–128 blocks, 25% via random sampling, queued ───────────
        List<BlockPos> zone3 = new ArrayList<>(ZONE3_SAMPLES / 4);
        for (int i = 0; i < ZONE3_SAMPLES; i++) {
            // Uniform random point on sphere surface, then random radius in [R2, R3]
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0;
            double phi   = Math.acos(1.0 - 2.0 * level.getRandom().nextDouble());
            double r     = R2 + level.getRandom().nextDouble() * (R3 - R2);
            int dx = (int) Math.round(Math.sin(phi) * Math.cos(theta) * r);
            int dy = (int) Math.round(Math.cos(phi) * r);
            int dz = (int) Math.round(Math.sin(phi) * Math.sin(theta) * r);
            // 25% chance to queue this sampled position
            if (level.getRandom().nextFloat() < 0.25f) {
                zone3.add(center.offset(dx, dy, dz));
            }
        }
        data.queueExplosion(zone3);
    }
}
