/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Resource-key factory for all OmniTech ore worldgen objects.
 * Keys are derived from the ore's registry name so adding a new ore
 * requires no changes here.
 */
public final class OmniTechWorldGen {
    private OmniTechWorldGen() {}

    // ── ConfiguredFeature ──────────────────────────────────────────────────

    /** Key for the default (fallback) configured feature of an ore. */
    public static ResourceKey<Feature> cfKey(String ore) {
        return ResourceKey.create(Registries.FEATURE, id(ore));
    }

    /** Key for a biome-override configured feature (created only when vein size differs). */
    public static ResourceKey<Feature> cfOverrideKey(String ore, int index) {
        return ResourceKey.create(Registries.FEATURE, id(ore + "_biome_" + index));
    }

    // ── PlacedFeature ──────────────────────────────────────────────────────

    public static ResourceKey<PlacedFeature> pfKey(String ore) {
        return ResourceKey.create(Registries.PLACED_FEATURE, id(ore));
    }

    public static ResourceKey<PlacedFeature> pfOverrideKey(String ore, int index) {
        return ResourceKey.create(Registries.PLACED_FEATURE, id(ore + "_biome_" + index));
    }

    // ── BiomeModifier ──────────────────────────────────────────────────────

    public static ResourceKey<BiomeModifier> bmKey(String ore) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id(ore));
    }

    public static ResourceKey<BiomeModifier> bmOverrideKey(String ore, int index) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id(ore + "_biome_" + index));
    }

    // ──────────────────────────────────────────────────────────────────────

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OmniTech.MODID, path);
    }
}
