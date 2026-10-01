/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.io.CurrentType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side record of what flows through each conductor (wire, relay,
 * suspension insulator) of the electric network, for the multimeter.
 *
 * <p>Wires have no block entity, so {@link ElectricNetworkUtil} reports every
 * delivery here, per conductor on the path: the energy carried, the I²R energy
 * dissipated in that conductor, the line voltage at it and — at the last
 * conductor before a machine — the energy handed over. Values are averaged
 * over a {@value #WINDOW}-tick window like {@link PowerMeter} and go stale
 * after two windows without traffic. Nothing is saved.
 */
public final class ElectricLossTracker {
    private ElectricLossTracker() {}

    public static final int WINDOW = PowerMeter.WINDOW;
    private static final int PURGE_INTERVAL = 200;

    /** Published averages for one conductor. Energies are kJ/tick. */
    public record Reading(float flow, float loss, float delivered, float volts, CurrentType type) {
        public static final Reading NONE = new Reading(0f, 0f, 0f, 0f, CurrentType.AC);
        public boolean active() { return flow > 0f || volts > 0f; }
    }

    private static final class Entry {
        long windowStart;
        long lastUpdate;
        float flowAcc, lossAcc, deliveredAcc;
        float minVolts = Float.MAX_VALUE;
        CurrentType type = CurrentType.AC;
        Reading published = Reading.NONE;

        void roll(long now) {
            long elapsed = now - windowStart;
            if (elapsed < WINDOW) return;
            published = new Reading(flowAcc / elapsed, lossAcc / elapsed, deliveredAcc / elapsed,
                    minVolts == Float.MAX_VALUE ? 0f : minVolts, type);
            flowAcc = lossAcc = deliveredAcc = 0f;
            minVolts = Float.MAX_VALUE;
            windowStart = now;
        }
    }

    private static final class LevelData {
        final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
        long lastPurge;
    }

    private static final Map<Level, LevelData> LEVELS = new WeakHashMap<>();

    /**
     * Records traffic through the conductor at {@code pos}.
     *
     * @param flow      energy carried this clock, kJ
     * @param loss      energy dissipated in this conductor, kJ
     * @param delivered energy handed to a machine from this conductor, kJ
     * @param volts     line voltage at this conductor
     */
    public static void record(Level level, BlockPos pos, float flow, float loss, float delivered,
            float volts, CurrentType type) {
        LevelData data = LEVELS.computeIfAbsent(level, l -> new LevelData());
        long now = level.getGameTime();
        Entry e = data.entries.computeIfAbsent(pos.asLong(), k -> {
            Entry n = new Entry();
            n.windowStart = now;
            return n;
        });
        e.roll(now);
        e.flowAcc      += flow;
        e.lossAcc      += loss;
        e.deliveredAcc += delivered;
        if (volts > 0f) e.minVolts = Math.min(e.minVolts, volts);
        e.type       = type;
        e.lastUpdate = now;

        if (now - data.lastPurge > PURGE_INTERVAL) {
            data.lastPurge = now;
            data.entries.values().removeIf(x -> now - x.lastUpdate > PURGE_INTERVAL);
        }
    }

    /** Last published averages at {@code pos}, or {@link Reading#NONE} if idle. */
    public static Reading read(Level level, BlockPos pos) {
        LevelData data = LEVELS.get(level);
        if (data == null) return Reading.NONE;
        Entry e = data.entries.get(pos.asLong());
        if (e == null) return Reading.NONE;
        long now = level.getGameTime();
        if (now - e.lastUpdate > 2L * WINDOW) return Reading.NONE;
        e.roll(now);
        return e.published;
    }
}
