/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechSounds {

    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(Registries.SOUND_EVENT, OmniTech.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> NUCLEAR_EXPLOSION =
            REGISTRY.register("nuclear_explosion", () -> SoundEvent.createFixedRangeEvent(
                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "nuclear_explosion"), 320.0f));

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
