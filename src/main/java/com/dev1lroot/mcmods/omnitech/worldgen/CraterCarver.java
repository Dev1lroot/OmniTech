package com.dev1lroot.mcmods.omnitech.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.function.Function;

/**
 * Carves perfectly circular parabolic craters into the moon surface.
 *
 * Works like vanilla cave/canyon carvers: called once per (sourceChunkPos, chunk) pair
 * within getRange() radius. Each source chunk independently decides whether it has a
 * crater, using a seeded random so all chunks agree on the same craters. Only blocks
 * within the target chunk are written — no cross-chunk write violations.
 */
public class CraterCarver extends WorldCarver<CarverConfiguration> {

    private static final int MIN_RADIUS = 5;
    private static final int MAX_RADIUS = 50;
    private static final int MIN_DEPTH  = 5;
    private static final int MAX_DEPTH  = 10;

    public CraterCarver() {
        super(CarverConfiguration.CODEC.codec());
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
    public boolean isStartChunk(CarverConfiguration config, RandomSource random) {
        return random.nextFloat() < config.probability;
    }

    @Override
    public boolean carve(
            CarvingContext context,
            CarverConfiguration config,
            ChunkAccess chunk,
            Function<BlockPos, Holder<Biome>> biomeGetter,
            RandomSource random,
            Aquifer aquifer,
            ChunkPos sourceChunkPos,
            CarvingMask mask
    ) {
        // Pick crater centre randomly within the source chunk.
        // random is already seeded consistently for (worldSeed, sourceChunkPos).
        int craterCenterX = sourceChunkPos.getMinBlockX() + random.nextInt(16);
        int craterCenterZ = sourceChunkPos.getMinBlockZ() + random.nextInt(16);
        int radius   = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int maxDepth = MIN_DEPTH  + random.nextInt(MAX_DEPTH  - MIN_DEPTH  + 1);

        ChunkPos currentChunk = chunk.getPos();
        int chunkMinX = currentChunk.getMinBlockX();
        int chunkMinZ = currentChunk.getMinBlockZ();
        int chunkMaxX = currentChunk.getMaxBlockX();
        int chunkMaxZ = currentChunk.getMaxBlockZ();

        // Quick bounding-box rejection — skip if the crater circle cannot touch this chunk.
        if (craterCenterX + radius < chunkMinX || craterCenterX - radius > chunkMaxX) return false;
        if (craterCenterZ + radius < chunkMinZ || craterCenterZ - radius > chunkMaxZ) return false;

        double radiusSq = (double) radius * radius;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        boolean carved = false;

        // Iterate only over the columns of THIS chunk that fall inside the crater radius.
        int iterMinX = Math.max(craterCenterX - radius, chunkMinX);
        int iterMaxX = Math.min(craterCenterX + radius, chunkMaxX);
        int iterMinZ = Math.max(craterCenterZ - radius, chunkMinZ);
        int iterMaxZ = Math.min(craterCenterZ + radius, chunkMaxZ);

        for (int worldX = iterMinX; worldX <= iterMaxX; worldX++) {
            int dx = worldX - craterCenterX;
            for (int worldZ = iterMinZ; worldZ <= iterMaxZ; worldZ++) {
                int dz = worldZ - craterCenterZ;
                double distSq = (double)(dx * dx + dz * dz);
                if (distSq > radiusSq) continue; // strict circle

                double dist = Math.sqrt(distSq);
                // Parabolic bowl: full depth at centre, zero at rim.
                int depth = (int) Math.ceil(maxDepth * (1.0 - (dist / radius) * (dist / radius)));
                if (depth <= 0) continue;

                // ChunkAccess.getHeight accepts world XZ; it masks to local internally.
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ);

                for (int y = surfaceY; y > surfaceY - depth && y >= context.getMinGenY(); y--) {
                    mutable.set(worldX, y, worldZ);
                    BlockState existing = chunk.getBlockState(mutable);
                    if (!existing.is(Blocks.BEDROCK) && !existing.isAir()) {
                        chunk.setBlockState(mutable, AIR);
                        carved = true;
                    }
                }
            }
        }

        return carved;
    }
}
