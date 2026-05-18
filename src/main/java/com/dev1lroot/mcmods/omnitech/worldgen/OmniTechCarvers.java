/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class OmniTechCarvers {

    public static final DeferredRegister<WorldCarver<?>> REGISTRY =
            DeferredRegister.create(Registries.CARVER, OmniTech.MODID);

    public static final Supplier<CraterCarver> MOON_CRATER =
            REGISTRY.register("moon_crater", CraterCarver::new);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
