package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechFeatures {

    public static final DeferredRegister<Feature<?>> REGISTRY =
            DeferredRegister.create(Registries.FEATURE, OmniTech.MODID);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
