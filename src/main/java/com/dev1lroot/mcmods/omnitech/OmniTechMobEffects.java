package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.radiation.RadiationEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechMobEffects {

    public static final DeferredRegister<MobEffect> REGISTRY =
            DeferredRegister.create(Registries.MOB_EFFECT, OmniTech.MODID);

    /** Amplifier 0 = Radiation I, 1 = Radiation II, 2 = Radiation III. */
    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION =
            REGISTRY.register("radiation", RadiationEffect::new);

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
