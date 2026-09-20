/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Resource keys for OmniTech's Overworld biomes, shared by the worldgen mixins that inject them. */
public final class OmniTechBiomes {
    private OmniTechBiomes() {}

    /** Rare hot/eroded Overworld biome whose terrain is mostly basalt with scattered lava pools. */
    public static final ResourceKey<Biome> VOLCANO =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("omnitech", "volcano"));
}
