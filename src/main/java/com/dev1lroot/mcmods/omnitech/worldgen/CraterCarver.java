/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

/**
 * Carves perfectly circular parabolic craters into the moon surface.
 *
 * Works like vanilla cave/canyon carvers: called once per (sourceChunkPos, chunk) pair
 * within getRange() radius. Each source chunk independently decides whether it has a
 * crater, using a seeded random so all chunks agree on the same craters. Only positions
 * within the target chunk are marked via {@link CarverOutput#carve(int, int, int)} — no
 * cross-chunk write violations.
 *
 * <p>Unlike the pre-26.3 API, carvers no longer have {@code ChunkAccess} to query the
 * live surface heightmap, so the crater centre height is sampled from the configured
 * {@link #y} height provider rather than the actual generated terrain height.
 */
public record CraterCarver(float probability, HeightProvider y) implements WorldCarver {

    public static final MapCodec<CraterCarver> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(CraterCarver::probability),
                HeightProvider.CODEC.fieldOf("y").forGetter(CraterCarver::y)
            )
            .apply(i, CraterCarver::new)
    );

    private static final int MIN_RADIUS = 5;
    private static final int MAX_RADIUS = 50;
    private static final int MIN_DEPTH  = 5;
    private static final int MAX_DEPTH  = 10;

    @Override
    public MapCodec<CraterCarver> codec() {
        return MAP_CODEC;
    }

    /**
     * 3-chunk look-ahead: covers craters up to radius 40 (2.5 chunks) that are centred
     * in a neighbouring chunk but still overlap the chunk being generated.
     */
    @Override
    public int getRange() {
        return 3;
    }

    /**
     * ~15 % of chunks act as a crater source.  With range=3 that gives roughly
     * 7×7×0.15 ≈ 7 craters visible per chunk, comparable to a heavily cratered surface.
     */
    @Override
    public boolean isStartChunk(RandomSource random) {
        return random.nextFloat() < this.probability;
    }

    @Override
    public boolean carve(
            WorldGenerationContext context,
            RandomSource random,
            ChunkPos chunkPos,
            ChunkPos sourceChunkPos,
            CarverOutput output
    ) {
        // Pick crater centre randomly within the source chunk.
        // random is already seeded consistently for (worldSeed, sourceChunkPos).
        int craterCenterX = sourceChunkPos.getMinBlockX() + random.nextInt(16);
        int craterCenterZ = sourceChunkPos.getMinBlockZ() + random.nextInt(16);
        int radius   = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int maxDepth = MIN_DEPTH  + random.nextInt(MAX_DEPTH  - MIN_DEPTH  + 1);
        int surfaceY = this.y.sample(random, context);

        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        // Quick bounding-box rejection — skip if the crater circle cannot touch this chunk.
        if (craterCenterX + radius < chunkMinX || craterCenterX - radius > chunkMaxX) return false;
        if (craterCenterZ + radius < chunkMinZ || craterCenterZ - radius > chunkMaxZ) return false;

        double radiusSq = (double) radius * radius;
        boolean carved = false;

        // Iterate only over the columns of THIS chunk that fall inside the crater radius.
        int iterMinX = Math.max(craterCenterX - radius, chunkMinX);
        int iterMaxX = Math.min(craterCenterX + radius, chunkMaxX);
        int iterMinZ = Math.max(craterCenterZ - radius, chunkMinZ);
        int iterMaxZ = Math.min(craterCenterZ + radius, chunkMaxZ);

        for (int worldX = iterMinX; worldX <= iterMaxX; worldX++) {
            int dx = worldX - craterCenterX;
            int localX = worldX - chunkMinX;
            for (int worldZ = iterMinZ; worldZ <= iterMaxZ; worldZ++) {
                int dz = worldZ - craterCenterZ;
                double distSq = (double)(dx * dx + dz * dz);
                if (distSq > radiusSq) continue; // strict circle

                double dist = Math.sqrt(distSq);
                // Parabolic bowl: full depth at centre, zero at rim.
                int depth = (int) Math.ceil(maxDepth * (1.0 - (dist / radius) * (dist / radius)));
                if (depth <= 0) continue;

                int localZ = worldZ - chunkMinZ;
                for (int y = surfaceY; y > surfaceY - depth && y >= context.getMinGenY(); y--) {
                    output.carve(localX, y, localZ);
                    carved = true;
                }
            }
        }

        return carved;
    }
}
