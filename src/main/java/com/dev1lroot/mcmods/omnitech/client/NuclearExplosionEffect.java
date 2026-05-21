/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks active nuclear explosion visual effects on the client.
 *
 * <p>The full animation is 13 seconds and consists of five overlapping layers:
 * <pre>
 *  Layer        Start     End    Description
 *  ─────────────────────────────────────────────────────────────────────
 *  Flash          0 ms   800 ms  White sphere, 0→80 blocks, rapid fade
 *  Fireball       0 ms  6500 ms  Orange-red sphere rising 0→64 blocks
 *  Shockwave    150 ms  6000 ms  Horizontal ring expanding 0→300 blocks
 *  Stem column  800 ms 11000 ms  Vertical cylinder growing 0→140 blocks
 *  Mushroom cap 4000 ms 13000 ms Torus rising above the stem
 * </pre>
 */
public final class NuclearExplosionEffect {

    // ── Flash ─────────────────────────────────────────────────────────────────
    public static final long  FLASH_END_MS    =   800L;
    public static final float FLASH_MAX_R     =  80f;

    // ── Fireball ──────────────────────────────────────────────────────────────
    public static final long  FIRE_END_MS     = 6_500L;
    public static final float FIRE_MAX_R      =  48f;
    /** How many blocks the fireball rises over its lifetime. */
    public static final float FIRE_RISE       =  64f;

    // ── Shockwave ring ────────────────────────────────────────────────────────
    public static final long  SHOCK_START_MS  =   150L;
    public static final long  SHOCK_END_MS    = 6_000L;
    public static final float SHOCK_MAX_R     = 300f;
    public static final float SHOCK_MINOR_R   =   8f;

    // ── Stem column ───────────────────────────────────────────────────────────
    public static final long  STEM_START_MS   =   800L;
    public static final long  STEM_END_MS     = 11_000L;
    public static final float STEM_RADIUS     =  18f;
    public static final float STEM_MAX_HEIGHT = 140f;

    // ── Mushroom cap (torus) ──────────────────────────────────────────────────
    public static final long  CAP_START_MS    = 4_000L;
    public static final long  CAP_END_MS      = 13_000L;
    public static final float CAP_MAX_MAJOR_R =  90f;
    public static final float CAP_MAX_MINOR_R =  35f;

    /** Total animation duration — must cover the longest-running layer (biome-ring at 23 s). */
    public static final long DURATION_MS = 23_000L;

    // ── Blast column (first stage, sky-clearing) ──────────────────────────────
    /** Radius of the sky-clearing column (32-block diameter = 32×32 cross-section). */
    public static final float BLAST_COL_RADIUS = 16f;
    /**
     * Column height from the explosion Y to the sky.  512 blocks ensures it
     * reaches above the build limit in any standard dimension.
     */
    public static final float BLAST_COL_HEIGHT  = 512f;

    // ── Biome-change sweep ring ───────────────────────────────────────────────
    /** Ring is delayed 3 s to appear after the main blast dies down. */
    public static final long  BIOME_RING_START_MS =  9_000L;
    /** Ring spreads for 20 s after its start: 3 000 + 20 000 = 23 000 ms total. */
    public static final long  BIOME_RING_END_MS   = 29_000L;
    /** Maximum radius the sweep ring reaches before it fully fades out. */
    public static final float BIOME_RING_MAX_R   = 320f;
    /** Tube cross-section radius of the sweep torus. */
    public static final float BIOME_RING_MINOR_R =   6f;

    // ── Firework burst particles ──────────────────────────────────────────────
    /**
     * Number of FIREWORK spark particles spawned in a single spherical burst.
     * Fibonacci-sphere distribution gives even coverage.
     */
    public static final int   BURST_PARTICLE_COUNT = 600;
    /**
     * Radius (blocks) at which particles are spawned — coincides with the peak
     * of the white flash sphere so the burst appears to emerge from the flash.
     */
    public static final float BURST_START_RADIUS   =  96f;
    /**
     * Final radius (blocks) that burst particles reach before drag halts them.
     * Travel distance = BURST_RADIUS_BLOCKS − BURST_START_RADIUS = 96 blocks.
     * FIREWORK spark drag ≈ 0.91/tick → v₀ = travel × (1 − 0.91) = travel × 0.09.
     */
    public static final float BURST_RADIUS_BLOCKS  = 192f;

    /**
     * An active nuclear explosion tracked on the client.
     *
     * <p>Using a plain class (not a record) so {@code burstFired} can be
     * flipped by the game-thread tick without allocating a new object.
     */
    public static final class ActiveEffect {
        public final Vec3 center;
        public final long startMs;
        /** Game-thread only: true after the firework burst has been spawned. */
        volatile boolean burstFired;

        public ActiveEffect(Vec3 center, long startMs) {
            this.center   = center;
            this.startMs  = startMs;
            this.burstFired = false;
        }

        public Vec3 center()  { return center; }
        public long startMs() { return startMs; }
    }

    /** Thread-safe — added from game thread (packet handler), read from render thread. */
    public static final List<ActiveEffect> ACTIVE = new CopyOnWriteArrayList<>();

    public static void addEffect(double x, double y, double z) {
        ACTIVE.add(new ActiveEffect(new Vec3(x, y, z), System.currentTimeMillis()));
    }

    private NuclearExplosionEffect() {}
}
