/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Worldgen tuning aid: when the {@code OMNITECH_MAP} environment variable names an
 * output directory, generates a square of chunks around the origin of a dimension
 * on server start and writes top-down PNGs there:
 * <ul>
 *   <li>{@code <dim>_surface.png} – top surface (map colour, shaded by height)</li>
 *   <li>{@code <dim>_height.png} – top surface height, greyscale over y 40..200</li>
 *   <li>{@code <dim>_seafloor.png} – first non-water block under the topmost water</li>
 *   <li>{@code <dim>_biomes.png} – biome per column</li>
 *   <li>{@code <dim>_section.png} – vertical x/y cross-section through the centre row
 *       (2 px per block, air black), for checking underground layers</li>
 *   <li>{@code <dim>_slice.png} – horizontal slice at {@code OMNITECH_MAP_SLICE_Y} (air black),
 *       only when that variable is set — for caves and ore distribution</li>
 *   <li>{@code OMNITECH_MAP_COUNT} ("ns:block,ns:block,…"): logs how many of each block the
 *       generated volume contains, for tuning ore frequency</li>
 * </ul>
 * and logs per-biome coverage and height stats. Optional: {@code OMNITECH_MAP_DIM}
 * (default {@code omnitech:europa}), {@code OMNITECH_MAP_RADIUS} (chunks, default 20),
 * {@code OMNITECH_MAP_CENTER} ("x,z" in blocks), {@code OMNITECH_MAP_EXIT} (stop the
 * server once the maps are written). Does nothing when {@code OMNITECH_MAP} is unset.
 */
public final class DimensionMapDebug {
    private static final int[] BIOME_COLORS = {
            0xE6194B, 0x3CB44B, 0xFFE119, 0x4363D8, 0xF58231, 0x911EB4, 0x46F0F0, 0xF032E6};

    private DimensionMapDebug() {}

    public static void onServerStarted(ServerStartedEvent event) {
        String out = System.getenv("OMNITECH_MAP");
        if (out == null || out.isBlank()) return;
        Identifier dimId = Identifier.parse(Objects.requireNonNullElse(System.getenv("OMNITECH_MAP_DIM"), "omnitech:europa"));
        ServerLevel level = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            OmniTech.LOGGER.error("[DimensionMap] {} not loaded", dimId);
            return;
        }
        String prefix = dimId.getPath();
        int radius = Integer.parseInt(Objects.requireNonNullElse(System.getenv("OMNITECH_MAP_RADIUS"), "20"));
        String[] center = Objects.requireNonNullElse(System.getenv("OMNITECH_MAP_CENTER"), "0,0").split(",");
        int ccx = Integer.parseInt(center[0].trim()) >> 4, ccz = Integer.parseInt(center[1].trim()) >> 4;

        int size = (radius * 2) * 16;
        BufferedImage surface = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage floor = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage height = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        String sliceEnv = System.getenv("OMNITECH_MAP_SLICE_Y");
        Integer sliceY = sliceEnv == null || sliceEnv.isBlank() ? null : Integer.parseInt(sliceEnv.trim());
        BufferedImage slice = sliceY == null ? null : new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage biomes = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Map<String, Integer> biomeIndex = new LinkedHashMap<>();
        Map<String, Integer> biomeCount = new LinkedHashMap<>();
        // per biome: {floor min, floor max, floor sum, surface min, surface max, surface sum}
        Map<String, long[]> heights = new LinkedHashMap<>();
        int[] openings = {0};

        Map<String, long[]> blockCounts = new LinkedHashMap<>();
        String countEnv = System.getenv("OMNITECH_MAP_COUNT");
        if (countEnv != null) for (String id : countEnv.split(",")) if (!id.isBlank()) blockCounts.put(id.trim(), new long[1]);

        long start = System.currentTimeMillis();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = -radius; cx < radius; cx++) {
            for (int cz = -radius; cz < radius; cz++) {
                LevelChunk chunk = level.getChunk(ccx + cx, ccz + cz);
                if (!blockCounts.isEmpty()) {
                    for (var section : chunk.getSections()) {
                        if (section.hasOnlyAir()) continue;
                        for (int i = 0; i < 4096; i++) {
                            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(section.getBlockState(i & 15, (i >> 8) & 15, (i >> 4) & 15).getBlock()).toString();
                            long[] n = blockCounts.get(id);
                            if (n != null) n[0]++;
                        }
                    }
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
                        int px = (cx + radius) * 16 + lx, pz = (cz + radius) * 16 + lz;

                        // top surface: first non-air from the sky down
                        int y = level.getMaxY();
                        for (; y > 0; y--) if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) break;
                        BlockState top = chunk.getBlockState(pos.set(x, y, z));
                        if (top.is(Blocks.WATER)) openings[0]++;
                        surface.setRGB(px, pz, shade(top.getMapColor(level, pos).col, y, 40, 160));
                        int g = Math.max(0, Math.min(255, (y - 40) * 255 / 160));
                        height.setRGB(px, pz, (g << 16) | (g << 8) | g);

                        // seafloor: first non-water block below the shell's underside
                        int wy = y;
                        for (; wy > 0; wy--) if (chunk.getBlockState(pos.set(x, wy, z)).is(Blocks.WATER)) break;
                        int fy = wy;
                        for (; fy > 0; fy--) if (!chunk.getBlockState(pos.set(x, fy, z)).is(Blocks.WATER)) break;
                        BlockState f = chunk.getBlockState(pos.set(x, fy, z));
                        floor.setRGB(px, pz, shade(f.getMapColor(level, pos).col, fy, 10, 60));

                        if (slice != null) {
                            BlockState ss = chunk.getBlockState(pos.set(x, sliceY, z));
                            slice.setRGB(px, pz, ss.isAir() ? 0 : ss.getMapColor(level, pos).col);
                        }

                        String b = level.getBiome(pos.set(x, y, z)).unwrapKey()
                                .map(k -> k.identifier().getPath()).orElse("?");
                        int idx = biomeIndex.computeIfAbsent(b, k -> biomeIndex.size());
                        biomeCount.merge(b, 1, Integer::sum);
                        long[] h = heights.computeIfAbsent(b, k -> new long[]{999, -999, 0, 999, -999, 0});
                        h[0] = Math.min(h[0], fy); h[1] = Math.max(h[1], fy); h[2] += fy;
                        h[3] = Math.min(h[3], y); h[4] = Math.max(h[4], y); h[5] += y;
                        biomes.setRGB(px, pz, BIOME_COLORS[idx % BIOME_COLORS.length]);
                    }
                }
            }
        }
        // vertical cross-section along the centre row (z = middle of the map)
        int sz = (ccz << 4) + 8, minY = level.getMinY(), maxY = level.getMaxY();
        BufferedImage section = new BufferedImage(size, (maxY - minY + 1) * 2, BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < size; px++) {
            int x = ((ccx - radius) << 4) + px;
            LevelChunk chunk = level.getChunk(x >> 4, sz >> 4);
            for (int y = minY; y <= maxY; y++) {
                BlockState st = chunk.getBlockState(pos.set(x, y, sz));
                int rgb = st.isAir() ? 0 : st.getMapColor(level, pos).col;
                int row = (maxY - y) * 2;
                section.setRGB(px, row, rgb);
                section.setRGB(px, row + 1, rgb);
            }
        }
        try {
            File dir = new File(out);
            dir.mkdirs();
            ImageIO.write(surface, "png", new File(dir, prefix + "_surface.png"));
            ImageIO.write(height, "png", new File(dir, prefix + "_height.png"));
            ImageIO.write(floor, "png", new File(dir, prefix + "_seafloor.png"));
            ImageIO.write(biomes, "png", new File(dir, prefix + "_biomes.png"));
            ImageIO.write(section, "png", new File(dir, prefix + "_section.png"));
            if (slice != null) ImageIO.write(slice, "png", new File(dir, prefix + "_slice.png"));
        } catch (Exception e) {
            OmniTech.LOGGER.error("[DimensionMap] failed to write maps", e);
        }
        int total = size * size;
        StringBuilder sb = new StringBuilder();
        biomeCount.forEach((b, c) -> {
            long[] h = heights.get(b);
            sb.append(String.format("%n  %-22s %5.1f%% #%06X  floor %d..%d avg %.1f  surface %d..%d avg %.1f",
                    b, 100.0 * c / total, BIOME_COLORS[biomeIndex.get(b) % BIOME_COLORS.length],
                    h[0], h[1], h[2] / (double) c, h[3], h[4], h[5] / (double) c));
        });
        OmniTech.LOGGER.info("[DimensionMap] {} {}x{} in {}s; open-water columns {};{}", dimId, size, size,
                (System.currentTimeMillis() - start) / 1000, openings[0], sb);
        if (!blockCounts.isEmpty()) {
            StringBuilder counts = new StringBuilder();
            blockCounts.forEach((id, n) -> counts.append(String.format("%n  %-36s %8d  (%.1f per chunk)", id, n[0],
                    n[0] / (double) (4 * radius * radius))));
            OmniTech.LOGGER.info("[DimensionMap] block counts:{}", counts);
        }
        if (System.getenv("OMNITECH_MAP_EXIT") != null) event.getServer().halt(false);
    }

    /** Map colour, darkened/brightened by height within [lo, hi]. */
    private static int shade(int rgb, int y, int lo, int hi) {
        float t = Math.max(0f, Math.min(1f, (y - lo) / (float) (hi - lo)));
        float k = 0.45f + 0.75f * t;
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }
}
