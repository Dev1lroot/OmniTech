/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.recipe.PipetteExtractRecipe;
import com.dev1lroot.mcmods.omnitech.recipe.PipetteInjectRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Crafting-table recipe serializers — dynamic, item-state-driven recipes (see {@code recipe} package). */
public class OmniTechRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, OmniTech.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PipetteExtractRecipe>> PIPETTE_EXTRACT =
            REGISTRY.register("pipette_extract", () -> PipetteExtractRecipe.SERIALIZER);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PipetteInjectRecipe>> PIPETTE_INJECT =
            REGISTRY.register("pipette_inject", () -> PipetteInjectRecipe.SERIALIZER);

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
