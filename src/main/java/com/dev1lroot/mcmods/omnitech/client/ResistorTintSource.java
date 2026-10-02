/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.ResistorItem;
import com.dev1lroot.mcmods.omnitech.pcb.ResistorCode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Tints one layer of the resistor model: {@code layer} 0 is the body (beige carbon film,
 * blue metal film), 1–6 are the colour bands. A band the resistor doesn't have takes
 * the body colour, so a 4-band part shows four bands.
 */
public record ResistorTintSource(int layer) implements ItemTintSource {

    public static final MapCodec<ResistorTintSource> MAP_CODEC =
            Codec.INT.fieldOf("layer").xmap(ResistorTintSource::new, ResistorTintSource::layer);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        List<ResistorCode.Band> bands = ResistorCode.fromCodes(ResistorItem.getBands(stack));
        int body = ResistorCode.bodyColor(bands);
        if (layer >= 1 && layer <= bands.size()) return 0xFF000000 | bands.get(layer - 1).rgb;
        return 0xFF000000 | body;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
