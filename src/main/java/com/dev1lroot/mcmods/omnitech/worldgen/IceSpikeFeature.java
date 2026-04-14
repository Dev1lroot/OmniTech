package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Places downward-pointing icicle spikes of {@code europa_ice} that hang from
 * the underside of Europa's ice crust into the sub-surface ocean.
 *
 * Placement is expected to be at Y≈103 (the ice–water interface).  The feature
 * scans upward from that point to find the first ice block, then extends a
 * tapered column downward through water blocks.
 */
public class IceSpikeFeature extends Feature<NoneFeatureConfiguration> {

    public IceSpikeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level  = ctx.level();
        BlockPos       origin = ctx.origin();
        RandomSource   random = ctx.random();

        BlockPos.MutableBlockPos mutable = origin.mutable();

        // Scan upward from the placement position to find the bottom of the ice crust.
        int iceBottomY = -1;
        for (int dy = 0; dy <= 25; dy++) {
            mutable.set(origin.getX(), origin.getY() + dy, origin.getZ());
            if (isEuropaIce(level.getBlockState(mutable))) {
                iceBottomY = origin.getY() + dy;
                break;
            }
        }
        if (iceBottomY < 0) return false;

        // The spike starts one block below the ice crust — that block should be water.
        int spikeTopY = iceBottomY - 1;
        mutable.set(origin.getX(), spikeTopY, origin.getZ());
        if (!level.getBlockState(mutable).is(Blocks.WATER)) return false;

        // Randomise spike shape.
        int length    = 8  + random.nextInt(13);   // 8–20 blocks long
        int maxRadius = random.nextBoolean() ? 1 : 2; // 1 or 2 block radius at base

        BlockState iceState = OmniTechBlocks.EUROPA_ICE.get().defaultBlockState();
        boolean placed = false;

        for (int dy = 0; dy < length; dy++) {
            int y = spikeTopY - dy;
            if (y < 4) break; // stop above the stone/bedrock base

            // Radius tapers linearly from maxRadius at top to 0 at tip.
            float progress = (float) dy / Math.max(1, length - 1);
            int   radius   = Math.round(maxRadius * (1.0f - progress));

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    mutable.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.getBlockState(mutable).is(Blocks.WATER)) {
                        level.setBlock(mutable, iceState, 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean isEuropaIce(BlockState state) {
        return state.is(OmniTechBlocks.EUROPA_ICE.get())
            || state.is(OmniTechBlocks.CRACKED_ICE.get());
    }
}
