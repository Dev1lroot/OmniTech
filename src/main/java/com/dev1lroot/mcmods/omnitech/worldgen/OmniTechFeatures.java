package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechFeatures {

    public static final DeferredRegister<Feature<?>> REGISTRY =
            DeferredRegister.create(Registries.FEATURE, OmniTech.MODID);

    /** Downward icicle spikes hanging from the Europa ice crust into the sub-surface ocean. */
    public static final java.util.function.Supplier<IceSpikeFeature> ICE_SPIKE =
            REGISTRY.register("ice_spike", IceSpikeFeature::new);

    /**
     * Rare hydrothermal trench cut into Europa's ocean floor, filled with water
     * and decorated with magma blocks at the bottom.
     */
    public static final java.util.function.Supplier<EuropaTrenchFeature> EUROPA_TRENCH =
            REGISTRY.register("europa_trench", EuropaTrenchFeature::new);

    /**
     * Upward-pointing europa_stone spires rising from the ocean floor into the
     * subsurface water column.  Each invocation places a cluster of 4–9 spires.
     */
    public static final java.util.function.Supplier<EuropaStoneSpireFeature> EUROPA_STONE_SPIRE =
            REGISTRY.register("europa_stone_spire", EuropaStoneSpireFeature::new);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
