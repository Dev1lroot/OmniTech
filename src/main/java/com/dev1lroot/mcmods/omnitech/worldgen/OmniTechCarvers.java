/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.worldgen;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class OmniTechCarvers {

    public static final DeferredRegister<MapCodec<? extends WorldCarver>> REGISTRY =
            DeferredRegister.create(Registries.CARVER_TYPE, OmniTech.MODID);

    public static final Supplier<MapCodec<CraterCarver>> MOON_CRATER =
            REGISTRY.register("moon_crater", () -> CraterCarver.MAP_CODEC);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
