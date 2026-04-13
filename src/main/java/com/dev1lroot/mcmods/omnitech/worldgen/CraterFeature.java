package com.dev1lroot.mcmods.omnitech.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Carves a circular parabolic crater bowl into the moon surface.
 *
 * Radius: 5–40 blocks. Depth: 5–20 blocks (at centre).
 * Depth falls off as depth * (1 - (r/radius)^2) — parabolic profile.
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 5;
    private static final int MAX_RADIUS = 40;
    private static final int MIN_DEPTH  = 5;
    private static final int MAX_DEPTH  = 20;

    public CraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level  = context.level();
        RandomSource  random = context.random();
        BlockPos      origin = context.origin();

        int radius   = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int maxDepth = MIN_DEPTH  + random.nextInt(MAX_DEPTH  - MIN_DEPTH  + 1);

        // Centre of the crater — snap to the actual surface height at origin
        int cx = origin.getX();
        int cz = origin.getZ();
        int cy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);

        double radiusSq = (double) radius * radius;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distSq = (double) (dx * dx + dz * dz);
                if (distSq > radiusSq) continue; // strictly circular

                double dist = Math.sqrt(distSq);
                // Parabolic depth: full at centre, zero at the rim
                int craterDepth = (int) Math.ceil(maxDepth * (1.0 - (dist / radius) * (dist / radius)));
                if (craterDepth <= 0) continue;

                int worldX = cx + dx;
                int worldZ = cz + dz;
                // Get surface at this xz
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ);

                // Carve from surface downward by craterDepth blocks
                for (int y = surfaceY - 1; y >= surfaceY - craterDepth; y--) {
                    mutable.set(worldX, y, worldZ);
                    if (!level.isOutsideBuildHeight(mutable) && level.ensureCanWrite(mutable)) {
                        level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        return true;
    }
}
