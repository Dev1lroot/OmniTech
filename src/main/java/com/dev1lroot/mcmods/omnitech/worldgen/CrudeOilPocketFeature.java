/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
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
 * Places a crude-oil pocket: an organically-distorted sphere (radius 5–12
 * blocks) that replaces natural terrain with {@code omnitech:crude_oil}
 * source blocks. A pocket never places oil above sea level — cells above
 * that Y are skipped outright — so an occasional shallow pocket (see the
 * separate, much rarer "lake" placed features) settles into a surface basin
 * that never rises above ocean height, instead of floating or capping at an
 * arbitrary height.
 *
 * <p>Only recognized natural terrain ({@link #isNaturalTerrain}) is replaced
 * — never air (a pocket that intersects an existing cave or the open sky
 * won't spill oil outward past its own footprint), never an existing fluid,
 * and never anything else (structure blocks like Trial Chambers' tuff
 * bricks/copper/vaults, villages, dungeons, etc. are left untouched since
 * they never match the natural-terrain allowlist).
 *
 * <p>Placement (desert-biased, occasional badlands; a separate, much rarer
 * pair of "lake" placed features handles near-surface pockets) is governed
 * by {@code data/omnitech/worldgen/placed_feature/crude_oil_pocket*.json}
 * and their biome modifiers.
 */
public record CrudeOilPocketFeature() implements Feature {

    public static final MapCodec<CrudeOilPocketFeature> CODEC = MapCodec.unit(CrudeOilPocketFeature::new);

    @Override
    public MapCodec<CrudeOilPocketFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        OmniTechFluids.FluidObject crudeOil = OmniTechFluids.get("crude_oil");
        if (crudeOil == null || crudeOil.block == null) return false;
        BlockState oilState = crudeOil.block.get().defaultBlockState();

        // Body radius: 5–12 blocks
        int radius = 5 + random.nextInt(8);

        // Single-octave Perlin for an organic, non-perfectly-spherical pocket shape
        PerlinNoise noise = new PerlinNoise(random);
        double noiseScale     = 0.15;
        double noiseAmplitude = radius * 0.2;

        int checkR = radius + (int) Math.ceil(noiseAmplitude) + 1;
        int seaLevel = chunkGenerator.getSeaLevel();

        BlockPos.MutableBlockPos mut = origin.mutable();
        boolean placedAny = false;

        for (int dx = -checkR; dx <= checkR; dx++) {
            for (int dy = -checkR; dy <= checkR; dy++) {
                int y = origin.getY() + dy;
                if (y > seaLevel) continue; // never let oil sit above ocean height

                for (int dz = -checkR; dz <= checkR; dz++) {
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq > (double) checkR * checkR) continue;

                    double nv = noise.get(dx * noiseScale, dy * noiseScale, dz * noiseScale);
                    double effectiveRadius = radius + nv * noiseAmplitude;
                    if (Math.sqrt(distSq) >= effectiveRadius) continue;

                    mut.set(origin.getX() + dx, y, origin.getZ() + dz);
                    BlockState existing = level.getBlockState(mut);
                    if (!isNaturalTerrain(existing)) continue;

                    level.setBlock(mut, oilState, 2);
                    placedAny = true;
                }
            }
        }

        return placedAny;
    }

    /**
     * True for the natural terrain blocks a pocket is allowed to replace.
     * Deliberately an allowlist (not "anything that isn't air/fluid") so that
     * generated structures — which are placed before this feature runs and
     * are built from blocks never in this list (tuff bricks, copper, vaults,
     * logs, cobblestone bricks, etc.) — are never carved into or destroyed.
     */
    private static boolean isNaturalTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL);
    }
}
