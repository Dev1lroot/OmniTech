/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * A procedurally-generated volcanic cone: a wide basalt/blackstone/tuff mound
 * rising from the placement origin (snapped to the surface heightmap by the
 * {@code minecraft:heightmap} placement modifier — see
 * {@code data/omnitech/worldgen/placed_feature/volcano.json}), tapering to a
 * summit crater with a small lava pool at its centre.
 *
 * <p>Replaces the mod's earlier attempt at a dedicated {@code omnitech:volcano}
 * Overworld biome (see the deleted {@code OverworldBiomeBuilderMixin} /
 * {@code OverworldMaterialRulesMixin} / {@code BiomeDataMixin}), which never
 * actually appeared in generated terrain: its climate-parameter box was broad
 * enough to overlap the existing Badlands/Desert niches, and {@link net.minecraft.world.level.biome.Climate}'s
 * nearest-point search only replaces the current best on a strict distance
 * improvement, so the pre-existing vanilla points won essentially every tie.
 * A placed feature sidesteps all of that — it just needs a target biome tag
 * (here {@code #minecraft:is_mountain}, wired via a
 * {@code neoforge:add_features} biome modifier) instead of carving out new
 * climate territory.
 */
public record VolcanoFeature() implements Feature {

    public static final MapCodec<VolcanoFeature> CODEC = MapCodec.unit(VolcanoFeature::new);

    @Override
    public MapCodec<VolcanoFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        // The heightmap placement modifier hands us a position one block above
        // the actual surface; step back down onto solid ground.
        int baseY = findSurface(level, origin.getX(), origin.getY(), origin.getZ());
        if (baseY < 0) return false;

        int baseRadius = 12 + random.nextInt(9);     // 12–20 blocks at the foot
        int height     = 16 + random.nextInt(15);     // 16–30 blocks tall
        int craterDepth = 4 + random.nextInt(4);       // 4–7 blocks deep summit crater
        int craterRadius = Math.max(2, baseRadius / 5);

        // Single-octave Perlin so the cone's silhouette and shell aren't a
        // perfect circle/cone — real volcanic cones are lumpy and irregular.
        PerlinNoise noise = new PerlinNoise(random);
        double noiseScale = 0.12;
        double noiseAmplitude = baseRadius * 0.25;

        int checkR = baseRadius + (int) Math.ceil(noiseAmplitude) + 1;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        boolean placedAny = false;

        BlockState shellA = Blocks.BASALT.defaultBlockState();
        BlockState shellB = Blocks.BLACKSTONE.defaultBlockState();
        BlockState core   = Blocks.TUFF.defaultBlockState();

        for (int dy = 0; dy < height; dy++) {
            int y = baseY + dy;
            float progress = (float) dy / Math.max(1, height - 1);
            // Slightly convex taper — wide base, narrowing faster near the summit.
            double layerRadius = baseRadius * (1.0 - Math.pow(progress, 0.7));

            for (int dx = -checkR; dx <= checkR; dx++) {
                for (int dz = -checkR; dz <= checkR; dz++) {
                    double distSq = (double) dx * dx + (double) dz * dz;
                    if (distSq > (double) checkR * checkR) continue;

                    double nv = noise.get(dx * noiseScale, dy * noiseScale, dz * noiseScale);
                    double effectiveRadius = layerRadius + nv * noiseAmplitude * (1.0 - progress * 0.5);
                    if (effectiveRadius <= 0 || Math.sqrt(distSq) >= effectiveRadius) continue;

                    // Hollow out the summit crater: within craterRadius of the
                    // centre and within craterDepth of the top, leave it open
                    // (or fill with lava at the very bottom) instead of solid rock.
                    boolean inCraterColumn = distSq <= (double) craterRadius * craterRadius;
                    boolean nearSummit = dy >= height - craterDepth - 1;
                    if (inCraterColumn && nearSummit) {
                        boolean craterFloor = dy == height - craterDepth - 1;
                        mut.set(origin.getX() + dx, y, origin.getZ() + dz);
                        BlockState existing = level.getBlockState(mut);
                        if (craterFloor && (isNaturalTerrain(existing) || existing.isAir())) {
                            level.setBlock(mut, Blocks.LAVA.defaultBlockState(), 2);
                            placedAny = true;
                        }
                        // Above the crater floor: leave air so the crater reads
                        // as a bowl, not a solid bump.
                        continue;
                    }

                    mut.set(origin.getX() + dx, y, origin.getZ() + dz);
                    BlockState existing = level.getBlockState(mut);
                    if (!isNaturalTerrain(existing) && !existing.isAir()) continue;

                    BlockState toPlace = random.nextFloat() < 0.2f ? shellB
                            : random.nextFloat() < 0.5f ? shellA : core;
                    level.setBlock(mut, toPlace, 2);
                    placedAny = true;
                }
            }
        }

        // A scatter of cooled obsidian at the foot, as if lava once ran down
        // and hardened against the surrounding terrain.
        for (int i = 0; i < 6 + random.nextInt(6); i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int r = baseRadius + random.nextInt(4);
            int ox = origin.getX() + (int) Math.round(Math.cos(angle) * r);
            int oz = origin.getZ() + (int) Math.round(Math.sin(angle) * r);
            int fy = findSurface(level, ox, baseY + 2, oz);
            if (fy < 0) continue;
            mut.set(ox, fy, oz);
            if (isNaturalTerrain(level.getBlockState(mut))) {
                level.setBlock(mut, Blocks.OBSIDIAN.defaultBlockState(), 2);
                placedAny = true;
            }
        }

        return placedAny;
    }

    /** Scans down (then a little up, in case the placement point landed inside terrain) for the surface. */
    private static int findSurface(WorldGenLevel level, int x, int startY, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 4; dy >= -24; dy--) {
            m.set(x, startY + dy, z);
            BlockState st = level.getBlockState(m);
            if (!st.isAir() && !st.is(Blocks.WATER) && !st.is(Blocks.LAVA)) {
                return startY + dy + 1;
            }
        }
        return -1;
    }

    /**
     * Allowlist of blocks the cone may bury/replace — never structures, never
     * anything already-placed that isn't ordinary terrain.
     */
    private static boolean isNaturalTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES);
    }
}
