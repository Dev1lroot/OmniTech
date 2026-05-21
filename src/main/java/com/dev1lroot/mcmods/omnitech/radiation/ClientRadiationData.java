/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.radiation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side cache of nuclear explosion centers; computes radiation level and drives Geiger sounds. */
public final class ClientRadiationData {

    /** Radiation effect begins at this distance from any explosion center. */
    private static final double OUTER_RADIUS = 380.0;

    public static final List<BlockPos> CENTERS = new ArrayList<>();

    private static long nextCrackMs = 0;

    private ClientRadiationData() {}

    /**
     * Returns 0.0–1.0: distance-based radiation level for the given player.
     * 0.0 = safe (> 380 blocks), 1.0 = at explosion epicenter.
     */
    public static float getRadiationLevel(Player player) {
        if (CENTERS.isEmpty()) return 0f;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float maxLevel = 0f;
        for (BlockPos c : CENTERS) {
            double dx = x - c.getX();
            double dy = y - c.getY();
            double dz = z - c.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist >= OUTER_RADIUS) continue;
            float level = (float)((OUTER_RADIUS - dist) / OUTER_RADIUS);
            if (level > maxLevel) maxLevel = level;
        }
        return maxLevel;
    }

    /**
     * Converts a 0–1 radiation level to a dose-rate string in μSv/h, mSv/h, or Sv/h.
     * Uses a logarithmic scale: 10^(level×9) μSv/h, so level 0 ≈ 1 μSv/h and
     * level 1.0 ≈ 1 000 Sv/h (instantly lethal, consistent with nuclear epicenter).
     */
    public static String getDoseRate(float level) {
        if (level < 0.001f) return "0.00 μSv/h";
        double usvh = Math.pow(10.0, level * 9.0);
        if (usvh < 1_000.0)       return String.format("%.2f μSv/h", usvh);
        if (usvh < 1_000_000.0)   return String.format("%.2f mSv/h",      usvh / 1_000.0);
        return                            String.format("%.2f Sv/h",       usvh / 1_000_000.0);
    }

    public static int getLabelColor(float level) {
        if (level < 0.05f) return 0xFF88FF88;
        if (level < 0.25f) return 0xFFFFDD44;
        if (level < 0.50f) return 0xFFFF8833;
        if (level < 0.75f) return 0xFFEE4444;
        return 0xFFAA22CC;
    }

    /**
     * Call every rendered frame from the HUD overlay.
     * Simulates a Geiger counter using an exponential (Poisson-process) inter-click distribution.
     * Rate = level^1.5 * 10 clicks/sec, so clicks grow quickly with proximity.
     * Each click has a random pitch (0.4–2.0) and volume scaled by level.
     */
    public static void tickCrackSound(float level) {
        if (level < 0.05f) return;
        long now = System.currentTimeMillis();
        if (now < nextCrackMs) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Random pitch across the full range for maximum variety
        float pitch  = 0.4f + rng.nextFloat() * 1.6f;
        float volume = 0.2f + level * 0.6f;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.COMPARATOR_CLICK, pitch, volume));

        // Poisson process: next interval drawn from exponential distribution
        // mean = 1000 / rate ms, where rate = level^1.5 * 10 clicks/sec
        double rate      = Math.pow(level, 1.5) * 10.0;
        double meanMs    = 1000.0 / rate;
        // -ln(U) gives exponential distribution; clamp between 25 ms and 20 s
        double intervalMs = -Math.log(1.0 - rng.nextDouble()) * meanMs;
        nextCrackMs = now + (long) Math.min(20_000, Math.max(25, intervalMs));
    }
}
