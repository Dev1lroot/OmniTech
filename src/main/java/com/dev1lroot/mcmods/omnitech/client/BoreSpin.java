/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.util.Util;

/**
 * Which other entities are breaking a block right now, from the block-crack progress packets
 * the server sends to everyone except the breaker. The bore cancels arm swings, so this is how
 * other players' bore heads know to spin. Fed by {@code ClientLevelBoreSpinMixin}.
 */
public final class BoreSpin {

    /** A crack stage can last this long on slow blocks before the next update arrives. */
    private static final long STALE_MS = 3000;

    private static final Int2LongMap LAST_PROGRESS = new Int2LongOpenHashMap();

    private BoreSpin() {}

    /** @param progress crack stage 0–9 while breaking; anything else = stopped */
    public static void onDestroyProgress(int entityId, int progress) {
        if (progress >= 0 && progress < 10) LAST_PROGRESS.put(entityId, Util.getMillis());
        else LAST_PROGRESS.remove(entityId);
    }

    public static boolean isMining(int entityId) {
        long t = LAST_PROGRESS.getOrDefault(entityId, -1L);
        return t >= 0 && Util.getMillis() - t < STALE_MS;
    }
}
