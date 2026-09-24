/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.fluid;

import com.dev1lroot.mcmods.omnitech.util.AdvancementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link LiquidBlock} for a fluid marked {@code "flammable": true} in its
 * {@code data/omnitech/fluid/<name>.json}.
 *
 * <p>Every random tick, checks a 3-block radius for a fire source: a block in
 * {@link BlockTags#FIRE} (fire/soul fire), a lit {@link CampfireBlock}, a torch, lava,
 * or a nearby {@link MagmaCube}. If one is found, every connected block of this same
 * fluid (source or flowing, flood-filled from the ignition point) disappears and is
 * replaced with fire — the whole spill burns, not just the block that was touched.
 *
 * <p>Registered in place of a plain {@link LiquidBlock} by
 * {@link com.dev1lroot.mcmods.omnitech.OmniTechFluids.FluidObject}; needs
 * {@code .randomTicks()} on its {@code BlockBehaviour.Properties} to actually tick.
 */
public class FlammableLiquidBlock extends LiquidBlock {

    private static final int SEARCH_RADIUS = 3;
    /** Safety cap so an enormous spill can't stall the server on one tick. */
    private static final int MAX_IGNITE_BLOCKS = 4096;

    public FlammableLiquidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        // Always tick, regardless of the underlying fluid's own randomTick semantics —
        // ignition scanning must run even for fluids (e.g. most non-water/lava liquids)
        // whose FlowingFluid#isRandomlyTicking() would otherwise say no.
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (hasNearbyIgnitionSource(level, pos)) {
            // Source and flowing variants are two distinct registry entries for "the
            // same" fluid (e.g. omnitech:naphtha vs omnitech:flowing_naphtha) — match
            // both, or the flood-fill stops dead at the first non-source block.
            igniteConnected(level, pos, this.fluid.getSource(), this.fluid.getFlowing());
            awardNearbyPlayers(level, pos);
        }
    }

    /** "I Love the Smell of Napalm in the Morning" — awarded to whoever's around to see the burn. */
    private static void awardNearbyPlayers(ServerLevel level, BlockPos pos) {
        AABB witnessBox = new AABB(pos).inflate(16);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, witnessBox)) {
            AdvancementUtil.award(player, "progression/napalm", "burned_flammable");
        }
    }

    // ── Ignition-source detection ───────────────────────────────────────────────

    private static boolean hasNearbyIgnitionSource(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    mpos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isIgnitionSourceBlock(level, mpos)) return true;
                }
            }
        }

        AABB scanBox = new AABB(center).inflate(SEARCH_RADIUS);
        return !level.getEntitiesOfClass(MagmaCube.class, scanBox).isEmpty();
    }

    private static boolean isIgnitionSourceBlock(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE)) return true;
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return true;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return true;
        return !level.getFluidState(pos).isEmpty() && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA);
    }

    // ── Flood-fill ignition ─────────────────────────────────────────────────────

    /**
     * Replaces every connected block of {@code source}/{@code flowing} with fire, all
     * at once. This is deliberately two passes — discover the whole connected set
     * first, purely by reading fluid state, then replace every position in a second
     * pass — because interleaving discovery with {@code setBlock} lets vanilla's fluid
     * tick immediately re-flow neighbouring fluid into a spot this method just cleared,
     * "healing" the fire back into liquid before the flood-fill finishes. The
     * replacement pass also uses {@link Block#UPDATE_CLIENTS} only (no neighbour
     * notification) so it can't trigger that re-flow either.
     */
    private static void igniteConnected(ServerLevel level, BlockPos start, Fluid source, Fluid flowing) {
        Set<BlockPos> connected = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        connected.add(start.immutable());

        while (!queue.isEmpty() && connected.size() <= MAX_IGNITE_BLOCKS) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (connected.contains(next)) continue;
                var fluidState = level.getFluidState(next);
                if (!fluidState.is(source) && !fluidState.is(flowing)) continue;
                connected.add(next);
                queue.add(next);
            }
        }

        for (BlockPos pos : connected) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
