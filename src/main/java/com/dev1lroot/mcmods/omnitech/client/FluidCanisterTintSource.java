/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.mojang.serialization.MapCodec;
import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * {@link ItemTintSource} for {@link FluidCanisterItem}.
 *
 * <p>Provides the fluid's ARGB tint for {@code tintindex 0} (the fluid overlay layer).
 * Returns {@code 0} (fully transparent) when the canister is empty so the texture
 * is invisible.
 *
 * <p>Register this codec under {@code omnitech:fluid_canister_tint} via
 * {@link net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources},
 * then reference it in the item definition JSON:
 * <pre>{@code
 * "tints": [{"type": "omnitech:fluid_canister_tint"}]
 * }</pre>
 */
public final class FluidCanisterTintSource implements ItemTintSource {

    public static final FluidCanisterTintSource INSTANCE = new FluidCanisterTintSource();
    public static final MapCodec<FluidCanisterTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private FluidCanisterTintSource() {}

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        FluidStack fluid = FluidCanisterItem.getFluid(stack);
        if (fluid.isEmpty()) return 0; // transparent — hides fluid layer when empty
        try {
            var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
            var model = modelSet.get(fluid.getFluid().defaultFluidState());
            if (model.fluidTintSource() != null) {
                return model.fluidTintSource().colorAsStack(fluid);
            }
        } catch (Exception ignored) {}
        return 0xFF4488FF; // fallback blue tint
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
