/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards a single criterion of an OmniTech advancement whose criterion uses
 * {@code "trigger": "minecraft:impossible"} — the standard vanilla pattern for an advancement that
 * can only be granted by code (a GUI action, an explosion, anything with no natural vanilla
 * trigger) rather than by an automatic in-game event. Used by
 * {@code items/GuidebookItem}, {@code blocks/fluid/FlammableLiquidBlock},
 * {@code radiation/NuclearExplosion} and {@code network/AssembleTruthTablePacket}.
 */
public final class AdvancementUtil {

    private AdvancementUtil() {}

    public static void award(ServerPlayer player, String advancementPath, String criterion) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(OmniTech.MODID, advancementPath));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }
}
