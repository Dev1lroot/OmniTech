/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.radiation;

import com.dev1lroot.mcmods.omnitech.OmniTechSounds;
import com.dev1lroot.mcmods.omnitech.network.NuclearExplosionFxPacket;
import com.dev1lroot.mcmods.omnitech.network.RadiationSyncPacket;
import com.dev1lroot.mcmods.omnitech.util.AdvancementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * Nuclear explosion: three-zone spherical block destruction.
 * Destruction is deferred to match the visual phase transitions:
 *   Zone 1 (0–32 blocks):   100% destruction + entity kills, fires when phase 1 ends (5s / 100 ticks).
 *   Zone 2 (32–64 blocks):  75% destruction, queued when phase 2 ends (6s / 120 ticks).
 *   Zone 3 (64–192 blocks): 96 primed TNT, spawned when phase 3 ends (11s / 220 ticks).
 *   Zone 4:                 320-block biome conversion to nuclear_wastelands, at the same moment as zone 3.
 */
public class NuclearExplosion {

    private static final int R1 = 32;
    private static final int R2 = 64;
    private static final int R3 = 192;

    /** Radius of the sky-clearing cylinder queued at zone-1 time (32-block diameter). */
    private static final int BLAST_COL_R = 16;

    private static final int ZONE3_TNT_COUNT = 96;

    /** Kill radius: all non-creative living entities within this many blocks die at zone 1 time. */
    private static final int KILL_RADIUS = 96;

    /** Biome conversion radius (XZ) applied at zone 3 time. */
    private static final int BIOME_RADIUS = 320;

    /** Leaf strip radius (3D sphere) applied at zone 2 time. */
    private static final int LEAF_RADIUS       = 300;
    /** Within this radius all leaves are removed (100%); beyond it 50% are removed. */
    private static final int LEAF_FULL_RADIUS  = 96;
    /** All flowers removed within this radius (100%). */
    private static final int FLOWER_RADIUS     = 192;

    // Destruction fires when each visual phase ends (20 ticks/s)
    private static final long ZONE1_DELAY_TICKS = 100L; // phase 1: 5s
    private static final long ZONE2_DELAY_TICKS = 120L; // phase 1 + phase 2: 6s
    private static final long ZONE3_DELAY_TICKS = 220L; // phase 1 + phase 2 + phase 3: 11s

    public static void trigger(ServerLevel level, BlockPos center) {
        RadiationSavedData data = RadiationSavedData.get(level);
        data.addCenter(center.immutable());

        RadiationSyncPacket syncPkt = new RadiationSyncPacket(new ArrayList<>(data.getCenters()));
        double witnessRadiusSq = (KILL_RADIUS * 2.0) * (KILL_RADIUS * 2.0);
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(sp, syncPkt);
            // "Now I Am Become Death" — anyone close enough to see the explosion FX below.
            if (sp.level() == level && sp.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= witnessRadiusSq) {
                AdvancementUtil.award(sp, "progression/nuclear_explosion_witnessed", "witnessed_explosion");
            }
        }

        PacketDistributor.sendToPlayersNear(level, null,
                center.getX(), center.getY(), center.getZ(),
                KILL_RADIUS * 2.0,
                new NuclearExplosionFxPacket(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5));

        level.playSound(null, center.getX(), center.getY(), center.getZ(),
                OmniTechSounds.NUCLEAR_EXPLOSION.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);

        long now = level.getGameTime();
        data.scheduleZone(1, center.immutable(), now + ZONE1_DELAY_TICKS);
        data.scheduleZone(2, center.immutable(), now + ZONE2_DELAY_TICKS);
        data.scheduleZone(3, center.immutable(), now + ZONE3_DELAY_TICKS);
        data.scheduleZone(4, center.immutable(), now + ZONE3_DELAY_TICKS);
        data.scheduleZone(5, center.immutable(), now + ZONE2_DELAY_TICKS);
    }

    // ── Zone executors (called by RadiationSavedData.tick) ───────────────────

    static void executeZone1(ServerLevel level, BlockPos center, RadiationSavedData data) {
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

        // Sky-clearing column: 32-block diameter cylinder from explosion height to world ceiling.
        // Queued nearest-first so blocks clear from the ground upward.
        int colRsq = BLAST_COL_R * BLAST_COL_R;
        int maxY   = level.getMaxY();
        List<BlockPos> column = new ArrayList<>();
        for (int dx = -BLAST_COL_R; dx <= BLAST_COL_R; dx++) {
            for (int dz = -BLAST_COL_R; dz <= BLAST_COL_R; dz++) {
                if (dx * dx + dz * dz > colRsq) continue;
                for (int y = center.getY(); y < maxY; y++) {
                    column.add(new BlockPos(center.getX() + dx, y, center.getZ() + dz));
                }
            }
        }
        data.queueExplosion(column, center);
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
        data.queueExplosion(zone2, center);
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

    static void executeLeafStrip(ServerLevel level, BlockPos center, RadiationSavedData data) {
        double radiusSq = (double) LEAF_RADIUS * LEAF_RADIUS;
        int chunkMinX = (center.getX() - LEAF_RADIUS) >> 4;
        int chunkMaxX = (center.getX() + LEAF_RADIUS) >> 4;
        int chunkMinZ = (center.getZ() - LEAF_RADIUS) >> 4;
        int chunkMaxZ = (center.getZ() + LEAF_RADIUS) >> 4;

        double flowerRadiusSq = (double) FLOWER_RADIUS * FLOWER_RADIUS;
        double leafFullRadiusSq = (double) LEAF_FULL_RADIUS * LEAF_FULL_RADIUS;

        List<BlockPos> toRemove = new ArrayList<>();
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection section = sections[si];
                    if (section.hasOnlyAir()) continue;
                    int sectionMinY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                var state = section.getBlockState(lx, ly, lz);
                                boolean isLeaf   = state.is(BlockTags.LEAVES);
                                boolean isFlower = state.is(BlockTags.FLOWERS);
                                if (!isLeaf && !isFlower) continue;
                                double dx  = (cx << 4) + lx - center.getX();
                                double dy  = sectionMinY + ly - center.getY();
                                double dz  = (cz << 4) + lz - center.getZ();
                                double dsq = dx*dx + dy*dy + dz*dz;
                                if (isLeaf) {
                                    if (dsq > radiusSq) continue;
                                    if (dsq <= leafFullRadiusSq || level.getRandom().nextFloat() < 0.5f)
                                        toRemove.add(new BlockPos((cx << 4) + lx, sectionMinY + ly, (cz << 4) + lz));
                                } else { // flower
                                    if (dsq <= flowerRadiusSq)
                                        toRemove.add(new BlockPos((cx << 4) + lx, sectionMinY + ly, (cz << 4) + lz));
                                }
                            }
                        }
                    }
                }
            }
        }
        data.queueExplosion(toRemove, center);
    }

    static void applyBiomeChange(ServerLevel level, BlockPos center) {
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath("omnitech", "nuclear_wastelands"));
        Holder<Biome> biomeHolder = level.registryAccess()
                .lookup(Registries.BIOME)
                .flatMap(reg -> reg.get(biomeKey))
                .orElse(null);
        if (biomeHolder == null) return;

        double radiusSq = (double) BIOME_RADIUS * BIOME_RADIUS;
        int chunkMinX = (center.getX() - BIOME_RADIUS) >> 4;
        int chunkMaxX = (center.getX() + BIOME_RADIUS) >> 4;
        int chunkMinZ = (center.getZ() - BIOME_RADIUS) >> 4;
        int chunkMaxZ = (center.getZ() + BIOME_RADIUS) >> 4;

        List<ChunkAccess> modified = new ArrayList<>();
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                boolean changed = false;
                for (LevelChunkSection section : chunk.getSections()) {
                    for (int bx = 0; bx < 4; bx++) {
                        for (int bz = 0; bz < 4; bz++) {
                            double dx = (cx << 4) + (bx << 2) + 2 - center.getX();
                            double dz = (cz << 4) + (bz << 2) + 2 - center.getZ();
                            if (dx * dx + dz * dz > radiusSq) continue;
                            for (int by = 0; by < 4; by++) {
                                if (section.getBiomes() instanceof PalettedContainer<Holder<Biome>> container) {
                                    container.set(bx, by, bz, biomeHolder);
                                    changed = true;
                                }
                            }
                        }
                    }
                }
                if (changed) {
                    chunk.markUnsaved();
                    modified.add(chunk);
                }
            }
        }
        if (!modified.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(modified);
        }
    }
}
