/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * Places a single Kuiper-Belt asteroid in the void:
 *
 * <ul>
 *   <li>Sphere radius randomly 6–24 blocks (diameter ~13–49), distorted by
 *       two-octave Perlin noise (±≈22% of radius) for an organic, rocky shape.</li>
 *   <li>Interior filled with {@code omnitech:asteroid_block}.</li>
 *   <li>{@link GravitationSourceBlockEntity} at the exact centre, calibrated so
 *       inner radius = sphere radius (full gravity at the surface) and
 *       outer radius = 2× sphere radius (gravity approach zone).</li>
 * </ul>
 *
 * Placement is governed by the {@code omnitech:asteroid} placed-feature JSON, which
 * scatters asteroids sparsely across the full height range of the Kuiper Belt dimension.
 */
public class AsteroidFeature extends Feature<NoneFeatureConfiguration> {

    public AsteroidFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level  = ctx.level();
        BlockPos       origin = ctx.origin();
        RandomSource   random = ctx.random();

        // Body radius: 6–24 blocks → diameter 13–49 blocks
        int radius = 6 + random.nextInt(19);

        // Two-octave Perlin for smooth surface distortion
        ImprovedNoise noise = new ImprovedNoise(random);
        double noiseScale     = 0.13;              // bump wavelength
        double noiseAmplitude = radius * 0.22;     // ±22% radius variation at surface

        int checkR = radius + (int) Math.ceil(noiseAmplitude) + 2;

        BlockPos.MutableBlockPos mut = origin.mutable();

        for (int dx = -checkR; dx <= checkR; dx++) {
            for (int dy = -checkR; dy <= checkR; dy++) {
                for (int dz = -checkR; dz <= checkR; dz++) {
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq > (double) checkR * checkR) continue;

                    double nx = dx * noiseScale;
                    double ny = dy * noiseScale;
                    double nz = dz * noiseScale;

                    // Two octaves: base + half-wavelength detail
                    double nv = noise.noise(nx, ny, nz) * 0.65
                              + noise.noise(nx * 2.1, ny * 2.1, nz * 2.1) * 0.35;

                    double effectiveRadius = radius + nv * noiseAmplitude;

                    if (Math.sqrt(distSq) < effectiveRadius) {
                        mut.set(origin.getX() + dx,
                                origin.getY() + dy,
                                origin.getZ() + dz);
                        if (level.getBlockState(mut).is(Blocks.AIR)) {
                            level.setBlock(mut, OmniTechBlocks.ASTEROID_BLOCK.get().defaultBlockState(), 2);
                        }
                    }
                }
            }
        }

        // Gravitation source at the exact centre — overrides whatever block was placed there
        level.setBlock(origin, OmniTechBlocks.GRAVITATION_SOURCE.get().defaultBlockState(), 3);

        // Configure radii: outer must be set before inner (inner is clamped to outer)
        int outerR = Math.min(GravitationSourceBlockEntity.MAX_RADIUS, radius * 2);
        int innerR = Math.max(GravitationSourceBlockEntity.MIN_RADIUS, radius - 3);

        if (level.getBlockEntity(origin) instanceof GravitationSourceBlockEntity be) {
            be.getContainerData().set(0, outerR);
            be.getContainerData().set(1, innerR);
            be.setChanged();
        }

        return true;
    }
}
