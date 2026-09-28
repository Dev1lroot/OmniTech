/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Space-suit helmet view modes (cycled with a keybind on the client).
 *
 * <p>The look of each mode is a client post effect ({@code HelmetVisionClient}); the
 * server only learns the mode so that NIGHT_VISION can grant the real Night Vision
 * effect, refreshed while the mode is on and the helmet is worn.
 */
public final class HelmetVision {

    public enum Mode {
        DEFAULT, NIGHT_VISION, SONAR, LIDAR;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : DEFAULT;
        }
    }

    /** Effect duration granted per refresh; refreshed well before vanilla's <200-tick flicker. */
    private static final int EFFECT_TICKS = 400;
    private static final int REFRESH_BELOW = 260;

    private static final Map<UUID, Mode> MODES = new HashMap<>();
    /** Players whose Night Vision we granted, so turning the mode off only removes ours. */
    private static final Set<UUID> GRANTED = new HashSet<>();

    private HelmetVision() {}

    public static void setMode(ServerPlayer player, Mode mode) {
        if (mode == Mode.DEFAULT) MODES.remove(player.getUUID());
        else MODES.put(player.getUUID(), mode);
        apply(player);
    }

    public static void clear(UUID player) {
        MODES.remove(player);
        GRANTED.remove(player);
    }

    /** Call about once a second. */
    public static void serverTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MODES.containsKey(player.getUUID()) || GRANTED.contains(player.getUUID())) apply(player);
        }
    }

    private static void apply(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wanted = MODES.get(id) == Mode.NIGHT_VISION && SpaceSuitItem.isWearingHelmet(player);
        if (wanted) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || (GRANTED.contains(id) && current.getDuration() < REFRESH_BELOW)) {
                // ambient, no particles: it's the visor, not a potion
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, true, false, true));
                GRANTED.add(id);
            }
        } else if (GRANTED.remove(id)) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current != null && current.isAmbient() && current.getDuration() <= EFFECT_TICKS) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }
}
