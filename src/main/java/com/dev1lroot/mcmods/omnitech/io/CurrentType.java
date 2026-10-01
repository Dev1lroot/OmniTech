/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Kind of current a source pushes into the electric network.
 *
 * <p>Generators (alternators) and transformers carry AC; solar panels and
 * capacitors carry DC. Most machines have their own power supply and take
 * either, but a transformer only works on AC — DC goes through an
 * {@code inverter} first, and AC becomes DC in a {@code rectifier}.
 */
public enum CurrentType {
    AC(ChatFormatting.GOLD),
    DC(ChatFormatting.AQUA);

    private final ChatFormatting color;

    CurrentType(ChatFormatting color) {
        this.color = color;
    }

    public Component label() {
        return Component.translatable("electric.omnitech.current." + name().toLowerCase())
                .withStyle(color);
    }
}
