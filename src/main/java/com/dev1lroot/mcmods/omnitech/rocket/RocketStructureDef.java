/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One rocket multiblock template, parsed from {@code data/omnitech/rocket_structure/<id>.json}.
 *
 * <p>The pattern is an arbitrary 3D shape — not just a rectangular box of one
 * block type. Each layer is a rectangular grid of symbols; a blank cell means
 * "don't care" (any block, including air), which is what lets a template
 * describe fins, a tapering nose cone, or any other non-cuboid silhouette
 * instead of a plain box. Layers stack bottom-to-top; rows within a layer run
 * along world Z, columns along world X.
 */
public class RocketStructureDef {
    private final String id;
    private final Identifier controllerBlockId;
    private final Map<Character, Identifier> key;
    private final List<List<String>> layers; // [y] -> rows (z) of a string (x)
    private final int width;  // x
    private final int depth;  // z
    private final int height; // y
    private final int anchorX, anchorY, anchorZ;

    public RocketStructureDef(String id, Identifier controllerBlockId, Map<Character, Identifier> key,
                               List<List<String>> layers, int width, int depth, int height,
                               int anchorX, int anchorY, int anchorZ) {
        this.id = id;
        this.controllerBlockId = controllerBlockId;
        this.key = key;
        this.layers = layers;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    public String getId()     { return id; }
    public int getWidth()     { return width; }
    public int getDepth()     { return depth; }
    public int getHeight()    { return height; }

    /** True if this template's controller symbol resolves to the given block. */
    public boolean hasController(Block block) {
        return BuiltInRegistries.BLOCK.getValue(controllerBlockId) == block;
    }

    /**
     * Attempts to match this template against the world, anchored so that the
     * template's controller cell lands on {@code controllerPos}.
     *
     * @return the matched block positions (including the controller's own
     *         position) and the world-space spawn point, or empty if any
     *         required cell doesn't match.
     */
    public Optional<Match> match(Level level, BlockPos controllerPos) {
        BlockPos origin = controllerPos.offset(-anchorX, -anchorY, -anchorZ);
        List<BlockPos> positions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            List<String> layer = layers.get(y);
            for (int z = 0; z < depth; z++) {
                String row = layer.get(z);
                for (int x = 0; x < width; x++) {
                    char c = row.charAt(x);
                    if (c == ' ') continue; // don't-care cell

                    Identifier blockId = key.get(c);
                    if (blockId == null) return Optional.empty();

                    Block expected = BuiltInRegistries.BLOCK.getValue(blockId);
                    BlockPos pos = origin.offset(x, y, z);
                    if (expected == null || !level.getBlockState(pos).is(expected)) {
                        return Optional.empty();
                    }
                    positions.add(pos.immutable());
                }
            }
        }

        Vec3 spawnPos = new Vec3(
                origin.getX() + width / 2.0,
                origin.getY(),
                origin.getZ() + depth / 2.0);

        return Optional.of(new Match(origin, List.copyOf(positions), spawnPos));
    }

    /** Result of a successful {@link #match}. */
    public record Match(BlockPos origin, List<BlockPos> positions, Vec3 spawnPos) {}
}
