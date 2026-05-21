/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tracks active nuclear explosion visual effects on the client. */
//@OnlyIn(Dist.CLIENT)
public final class NuclearExplosionEffect {

    // Phase 1: grow 0→32 blocks over 5s, no fade
    public static final long  PHASE1_MS         = 5_000L;
    public static final float PHASE1_MAX_RADIUS = 32f;
    // Phase 2: hold at radius 32 for 1s, no fade
    public static final long  PHASE2_MS         = 1_000L;
    // Phase 3: grow 32→128 blocks over 5s, fade out
    public static final long  PHASE3_MS         = 5_000L;
    public static final float MAX_RADIUS        = 128f;

    /** Total duration — used to cull finished effects. */
    public static final long DURATION_MS = PHASE1_MS + PHASE2_MS + PHASE3_MS;

    public record ActiveEffect(Vec3 center, long startMs) {}

    /** Thread-safe list — added from game thread (packet handler), read from render thread. */
    public static final List<ActiveEffect> ACTIVE = new CopyOnWriteArrayList<>();

    public static void addEffect(double x, double y, double z) {
        ACTIVE.add(new ActiveEffect(new Vec3(x, y, z), System.currentTimeMillis()));
    }

    private NuclearExplosionEffect() {}
}
