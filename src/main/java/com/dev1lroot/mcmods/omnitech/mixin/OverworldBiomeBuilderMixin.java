/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.worldgen.OmniTechBiomes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Adds {@code omnitech:volcano} to the Overworld's climate-based biome
 * distribution. Placed in a narrow, hot/dry/eroded/high-weirdness niche so it
 * shows up as a rare special biome, the same way vanilla carves out things
 * like Ice Spikes or Eroded Badlands, without disturbing any existing biome's
 * placement.
 */
@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderMixin {

    @Inject(method = "addBiomes", at = @At("TAIL"))
    private void omnitech$addVolcano(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, CallbackInfo ci) {
        Climate.Parameter temperature     = Climate.Parameter.span(0.55F, 1.0F);
        Climate.Parameter humidity        = Climate.Parameter.span(-1.0F, 0.3F);
        Climate.Parameter continentalness = Climate.Parameter.span(0.3F, 1.0F);
        Climate.Parameter erosion         = Climate.Parameter.span(-1.0F, -0.2F);
        Climate.Parameter weirdness       = Climate.Parameter.span(-1.0F, 1.0F);
        float offset = 0.0F;

        biomes.accept(Pair.of(
                Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(0.0F), weirdness, offset),
                OmniTechBiomes.VOLCANO));
        biomes.accept(Pair.of(
                Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.0F), weirdness, offset),
                OmniTechBiomes.VOLCANO));
    }
}
