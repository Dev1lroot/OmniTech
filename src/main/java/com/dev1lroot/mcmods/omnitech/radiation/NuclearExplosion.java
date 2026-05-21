/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.radiation;

import com.dev1lroot.mcmods.omnitech.network.NuclearExplosionFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Nuclear explosion: three-zone spherical block destruction.
 * Destruction is deferred to match the visual phase transitions:
 *   Zone 1 (0–32 blocks):   100% destruction, fires when phase 1 ends (5s / 100 ticks).
 *   Zone 2 (32–64 blocks):  75% destruction, queued when phase 2 ends (6s / 120 ticks).
 *   Zone 3 (64–128 blocks): 32 primed TNT, spawned when phase 3 ends (11s / 220 ticks).
 */
public class NuclearExplosion {

    private static final int R1 = 32;
    private static final int R2 = 64;
    private static final int R3 = 128;

    private static final int ZONE3_TNT_COUNT = 32;

    /** Kill radius: all non-creative living entities within this many blocks die at zone 1 time. */
    private static final int KILL_RADIUS = 96;

    // Destruction fires when each visual phase ends (20 ticks/s)
    private static final long ZONE1_DELAY_TICKS = 100L; // phase 1: 5s
    private static final long ZONE2_DELAY_TICKS = 120L; // phase 1 + phase 2: 6s
    private static final long ZONE3_DELAY_TICKS = 220L; // phase 1 + phase 2 + phase 3: 11s

    public static void trigger(ServerLevel level, BlockPos center) {
        RadiationSavedData data = RadiationSavedData.get(level);
        data.addCenter(center.immutable());

        PacketDistributor.sendToPlayersNear(level, null,
                center.getX(), center.getY(), center.getZ(),
                KILL_RADIUS * 2.0,
                new NuclearExplosionFxPacket(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5));

        long now = level.getGameTime();
        data.scheduleZone(1, center.immutable(), now + ZONE1_DELAY_TICKS);
        data.scheduleZone(2, center.immutable(), now + ZONE2_DELAY_TICKS);
        data.scheduleZone(3, center.immutable(), now + ZONE3_DELAY_TICKS);
    }

    // ── Zone executors (called by RadiationSavedData.tick) ───────────────────

    static void executeZone1(ServerLevel level, BlockPos center) {
        int kr = KILL_RADIUS;
        AABB killBox = new AABB(
                center.getX() - kr, center.getY() - kr, center.getZ() - kr,
                center.getX() + kr, center.getY() + kr, center.getZ() + kr);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, killBox, e -> {
            if (e instanceof Player p && p.isCreative()) return false;
            double dx = e.getX() - center.getX();
            double dy = e.getY() - center.getY();
            double dz = e.getZ() - center.getZ();
            return dx*dx + dy*dy + dz*dz <= (double)(kr * kr);
        })) entity.kill(level);

        int r1sq = R1 * R1;
        for (int dx = -R1; dx <= R1; dx++)
            for (int dy = -R1; dy <= R1; dy++)
                for (int dz = -R1; dz <= R1; dz++) {
                    if (dx*dx + dy*dy + dz*dz > r1sq) continue;
                    BlockPos target = center.offset(dx, dy, dz);
                    if (!level.getBlockState(target).isAir()
                            && level.getBlockState(target).getDestroySpeed(level, target) >= 0)
                        level.setBlock(target, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
    }

    static void executeZone2(ServerLevel level, BlockPos center, RadiationSavedData data) {
        List<BlockPos> zone2 = new ArrayList<>();
        int r1sq = R1 * R1;
        int r2sq = R2 * R2;
        for (int dx = -R2; dx <= R2; dx++)
            for (int dy = -R2; dy <= R2; dy++)
                for (int dz = -R2; dz <= R2; dz++) {
                    int dsq = dx*dx + dy*dy + dz*dz;
                    if (dsq <= r1sq || dsq > r2sq) continue;
                    if (level.getRandom().nextFloat() < 0.75f)
                        zone2.add(center.offset(dx, dy, dz));
                }
        data.queueExplosion(zone2);
    }

    static void spawnZone3Tnt(ServerLevel level, BlockPos center) {
        for (int i = 0; i < ZONE3_TNT_COUNT; i++) {
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0;
            double phi   = Math.acos(1.0 - 2.0 * level.getRandom().nextDouble());
            double r     = R2 + level.getRandom().nextDouble() * (R3 - R2);
            double x = center.getX() + Math.sin(phi) * Math.cos(theta) * r;
            double y = center.getY() + Math.cos(phi) * r;
            double z = center.getZ() + Math.sin(phi) * Math.sin(theta) * r;
            PrimedTnt tnt = new PrimedTnt(level, x, y, z, null);
            tnt.setFuse(40);
            level.addFreshEntity(tnt);
        }
    }
}
