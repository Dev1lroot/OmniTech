/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.BlockLoader;
import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Lays Europa's seafloor: per-biome surface blocks (sand, gravel, vanilla sulfur,
 * blackstone) and ~9 blocks of ocean sediment (carbonate with evaporite patches)
 * below them.
 *
 * <p>This can't be a material rule: the material system's "stone depth" only resets
 * at air, not at water, so under the ice shell it counts the shell + the ocean and
 * "N blocks below the seafloor" never matches. Here each column is scanned from the
 * ocean down to the actual floor instead.
 *
 * <p>Place once per chunk (no placement modifiers) in an early decoration step so
 * vegetation, chimneys and trenches see the final floor. Only the origin chunk is
 * written.
 */
public record EuropaSeafloorFeature() implements Feature {

    public static final MapCodec<EuropaSeafloorFeature> CODEC = MapCodec.unit(EuropaSeafloorFeature::new);

    private static final int SCAN_TOP = 99;       // just under the ice shell
    private static final int SURFACE_DEPTH = 3;   // biome surface blocks
    private static final int SEDIMENT_DEPTH = 12; // sediment below the surface blocks, down to this depth

    private static long noiseSeed = Long.MIN_VALUE;
    private static SimplexNoise patchNoise;

    @Override
    public MapCodec<EuropaSeafloorFeature> codec() {
        return CODEC;
    }

    private static synchronized SimplexNoise patches(long seed) {
        if (patchNoise == null || noiseSeed != seed) {
            patchNoise = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(seed ^ 0x5EAF100DL)));
            noiseSeed = seed;
        }
        return patchNoise;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        SimplexNoise noise = patches(level.getSeed());
        BlockState stone = OmniTechBlocks.EUROPA_STONE.get().defaultBlockState();
        BlockState carbonate = BlockLoader.getBlock("europa_carbonate").get().defaultBlockState();
        BlockState evaporite = BlockLoader.getBlock("europa_evaporite").get().defaultBlockState();
        BlockState sulfur = Blocks.SULFUR.defaultBlockState();

        int minX = origin.getX() & ~15, minZ = origin.getZ() & ~15;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean changed = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx, z = minZ + dz;

                // scan down: into the ocean, then to the first solid block below it
                int y = SCAN_TOP;
                while (y > level.getMinY() && !level.getBlockState(m.set(x, y, z)).is(Blocks.WATER)) y--;
                while (y > level.getMinY() && level.getBlockState(m.set(x, y, z)).is(Blocks.WATER)) y--;
                if (y <= level.getMinY() + 4) continue;
                int floorY = y;

                String biome = level.getBiome(m.set(x, floorY, z)).unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("");
                double patch = noise.get(x * 0.06, z * 0.06);

                for (int depth = 0; depth < SEDIMENT_DEPTH; depth++) {
                    m.set(x, floorY - depth, z);
                    if (!level.getBlockState(m).is(stone.getBlock())) break;
                    // evaporite forms flat lenses: 3-D noise, stretched horizontally
                    BlockState place = depth < SURFACE_DEPTH ? surface(biome, patch, sulfur, carbonate)
                            : sediment(biome, noise.get(x * 0.05, (floorY - depth) * 0.3, z * 0.05),
                                       carbonate, evaporite);
                    if (place != null) {
                        level.setBlock(m, place, 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static BlockState surface(String biome, double patch, BlockState sulfur, BlockState carbonate) {
        return switch (biome) {
            case "europa_sulfur_vents" -> patch > 0.15 ? sulfur
                    : patch < -0.35 ? Blocks.BLACKSTONE.defaultBlockState() : null;
            case "europa_plains" -> patch > 0.35 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
            case "europa_coral_reef" -> patch < -0.45 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
            case "europa_hills" -> patch > 0.3 ? Blocks.GRAVEL.defaultBlockState() : carbonate;
            default -> null;   // stone peaks: bare europa_stone
        };
    }

    private static BlockState sediment(String biome, double lens, BlockState carbonate, BlockState evaporite) {
        if (biome.equals("europa") || biome.isEmpty()) return null;   // stone peaks: no sediment
        return lens > 0.35 ? evaporite : carbonate;
    }
}
