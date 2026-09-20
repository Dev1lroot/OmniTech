/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechFeatures {

    public static final DeferredRegister<MapCodec<? extends Feature>> REGISTRY =
            DeferredRegister.create(Registries.FEATURE_TYPE, OmniTech.MODID);

    /** Downward icicle spikes hanging from the Europa ice crust into the sub-surface ocean. */
    public static final java.util.function.Supplier<MapCodec<IceSpikeFeature>> ICE_SPIKE =
            REGISTRY.register("ice_spike", () -> IceSpikeFeature.CODEC);

    /**
     * Rare hydrothermal trench cut into Europa's ocean floor, filled with water
     * and decorated with magma blocks at the bottom.
     */
    public static final java.util.function.Supplier<MapCodec<EuropaTrenchFeature>> EUROPA_TRENCH =
            REGISTRY.register("europa_trench", () -> EuropaTrenchFeature.CODEC);

    /**
     * Upward-pointing europa_stone spires rising from the ocean floor into the
     * subsurface water column.  Each invocation places a cluster of 4–9 spires.
     */
    public static final java.util.function.Supplier<MapCodec<EuropaStoneSpireFeature>> EUROPA_STONE_SPIRE =
            REGISTRY.register("europa_stone_spire", () -> EuropaStoneSpireFeature.CODEC);

    /**
     * Distorted asteroid sphere (radius 6–24 blocks) filled with asteroid_block,
     * with a calibrated GravitationSource at the centre.  Scattered sparsely
     * through the void of the Kuiper Belt dimension.
     */
    public static final java.util.function.Supplier<MapCodec<AsteroidFeature>> ASTEROID =
            REGISTRY.register("asteroid", () -> AsteroidFeature.CODEC);

    /**
     * Underground crude-oil pocket (radius 5–12 blocks) that replaces solid
     * terrain with {@code omnitech:crude_oil} source blocks. Placed mostly in
     * desert biomes, occasionally in badlands.
     */
    public static final java.util.function.Supplier<MapCodec<CrudeOilPocketFeature>> CRUDE_OIL_POCKET =
            REGISTRY.register("crude_oil_pocket", () -> CrudeOilPocketFeature.CODEC);

    /**
     * Procedural volcanic cone with a summit crater and lava pool, placed on
     * mountain terrain (see {@code neoforge/biome_modifier/volcano.json}).
     */
    public static final java.util.function.Supplier<MapCodec<VolcanoFeature>> VOLCANO =
            REGISTRY.register("volcano", () -> VolcanoFeature.CODEC);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
