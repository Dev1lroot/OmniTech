/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import java.util.List;

/**
 * Complete world-generation configuration for an ore block.
 *
 * <p>Pass this to the {@code OmniTechOreBlock} constructor. Running
 * {@code ./gradlew runData} regenerates all worldgen JSON from these values.
 *
 * <p><b>Biome selector format</b> (used in both configs):
 * <ul>
 *   <li>{@code "#minecraft:is_overworld"} — all overworld biomes</li>
 *   <li>{@code "#minecraft:is_mountain"} — mountain biomes</li>
 *   <li>{@code "#minecraft:is_jungle"} — jungle / tropical biomes</li>
 *   <li>{@code "minecraft:plains"} — a single specific biome</li>
 * </ul>
 *
 * @param defaultConfig  fallback generation that applies to all matching biomes
 * @param biomeOverrides optional per-biome configurations; each adds additional
 *                       clusters on top of the default for its target biomes
 */
public record OreSpawnConfig(
        GenerationConfig defaultConfig,
        List<BiomeOverride> biomeOverrides
) {
    /** Parameters shared by the default config and every biome override. */
    public interface SpawnParams {
        /** Biome tag or biome ID this config selects. */
        String biomes();
        int minY();
        int maxY();
        /** Maximum blocks per ore cluster. */
        int veinSize();
        int minCount();
        int maxCount();
    }

    /**
     * Fallback generation applied to all biomes matching {@code biomes}.
     *
     * @param biomes    biome selector (tag or ID)
     * @param minY      minimum Y level (inclusive)
     * @param maxY      maximum Y level (inclusive)
     * @param veinSize  maximum blocks per cluster
     * @param minCount  minimum cluster attempts per chunk
     * @param maxCount  maximum cluster attempts per chunk
     */
    public record GenerationConfig(
            String biomes,
            int minY, int maxY,
            int veinSize,
            int minCount, int maxCount
    ) implements SpawnParams {}

    /**
     * Biome-specific override that adds extra clusters in addition to the default.
     * Uses independent height, cluster size, and count from the default config.
     *
     * @param biomes    biome selector (tag or ID) — matched biomes also receive the default config
     * @param minY      minimum Y level for this override
     * @param maxY      maximum Y level for this override
     * @param veinSize  maximum cluster size for this override
     * @param minCount  minimum extra clusters per chunk
     * @param maxCount  maximum extra clusters per chunk
     */
    public record BiomeOverride(
            String biomes,
            int minY, int maxY,
            int veinSize,
            int minCount, int maxCount
    ) implements SpawnParams {}
}
