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

    /** Total animation duration — used to cull finished effects. */
    public static final long DURATION_MS = CAP_END_MS;

    public record ActiveEffect(Vec3 center, long startMs) {}

    /** Thread-safe — added from game thread (packet handler), read from render thread. */
    public static final List<ActiveEffect> ACTIVE = new CopyOnWriteArrayList<>();

    public static void addEffect(double x, double y, double z) {
        ACTIVE.add(new ActiveEffect(new Vec3(x, y, z), System.currentTimeMillis()));
    }

    private NuclearExplosionEffect() {}
}
