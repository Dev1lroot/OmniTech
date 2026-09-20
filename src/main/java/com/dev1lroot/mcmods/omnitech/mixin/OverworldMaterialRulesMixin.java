/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.worldgen.OmniTechBiomes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.material.OverworldMaterialRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes {@code omnitech:volcano}'s terrain roughly 90% basalt with 10% lava
 * pools scattered across the surface, by prepending a biome-gated branch onto
 * the Overworld's surface material rule. Every other biome falls through to
 * the original (untouched) vanilla rule.
 */
@Mixin(OverworldMaterialRules.class)
public class OverworldMaterialRulesMixin {

    @ModifyReturnValue(method = "registerSurface", at = @At("RETURN"))
    private static MaterialRule omnitech$addVolcanoSurface(MaterialRule original, BootstrapContext<MaterialRule> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        MaterialCondition isVolcano = MaterialRules.isBiome(biomes, OmniTechBiomes.VOLCANO);
        // ~10% of the surface: a noise threshold band sized to cover roughly a tenth of the range.
        MaterialCondition isLavaPatch = MaterialRules.noiseCondition2d(Noises.PATCH, 0.8, 1.0);

        MaterialRule volcanoTerrain = MaterialRules.sequence(
                MaterialRules.ifTrue(isLavaPatch, MaterialRules.state(Blocks.LAVA.defaultBlockState())),
                MaterialRules.state(Blocks.BASALT.defaultBlockState())
        );

        return MaterialRules.sequence(
                MaterialRules.ifTrue(isVolcano, volcanoTerrain),
                original
        );
    }
}
