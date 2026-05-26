/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.network.GravityFieldSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side store of active gravity field sources received via
 * {@link GravityFieldSyncPacket}.
 *
 * <p>Each source has an outer radius (gravity = 0) and an inner radius (gravity = 1).
 * Between those radii the pull factor α is linearly interpolated.
 * Multiple sources are combined by vector addition — an entity between two opposing
 * sources experiences a blended gravity direction and weaker net pull.
 */
public final class GravityFieldManager {

    private static final List<BlockPos>       POSITIONS    = new ArrayList<>();
    private static final List<Integer>        OUTER_RADII  = new ArrayList<>();
    private static final List<Integer>        INNER_RADII  = new ArrayList<>();
    private static final org.joml.Quaternionf GRAVITY_Q    = new org.joml.Quaternionf();

    private GravityFieldManager() {}

    /** Replace the active source list with data from the latest sync packet. */
    public static synchronized void updateFromPacket(GravityFieldSyncPacket pkt) {
        POSITIONS.clear();   POSITIONS.addAll(pkt.positions());
        OUTER_RADII.clear(); OUTER_RADII.addAll(pkt.outerRadii());
        INNER_RADII.clear(); INNER_RADII.addAll(pkt.innerRadii());
    }

    /** Store the current camera gravity quaternion (called every render frame). */
    public static synchronized void setGravityQ(org.joml.Quaternionf q) {
        GRAVITY_Q.set(q);
    }

    /** Returns a defensive copy of the current camera gravity quaternion. */
    public static synchronized org.joml.Quaternionf getGravityQ() {
        return new org.joml.Quaternionf(GRAVITY_Q);
    }

    /** Clear all active sources (call on world unload). */
    public static synchronized void clear() {
        POSITIONS.clear();
        OUTER_RADII.clear();
        INNER_RADII.clear();
        GRAVITY_Q.identity();
    }

    /**
     * Computes the combined gravity pull vector at the given world position.
     *
     * <p>Returns the vector sum of each source's contribution: direction-to-source
     * weighted by the falloff factor α ∈ [0, 1].  α = 1 inside the inner radius,
     * α = 0 at/beyond the outer radius, linearly interpolated between.
     *
     * <p>The returned vector's magnitude encodes the combined field strength:
     * 1.0 = full pull from one source, 0.0 = no field.  A magnitude > 1 is possible
     * when multiple sources overlap.
     */
    public static synchronized Vec3 computeGravityVec(double x, double y, double z) {
        double gx = 0, gy = 0, gz = 0;
        for (int i = 0; i < POSITIONS.size(); i++) {
            BlockPos pos    = POSITIONS.get(i);
            double   outerR = OUTER_RADII.get(i);
            double   innerR = INNER_RADII.get(i);
            double   dx     = pos.getX() + 0.5 - x;
            double   dy     = pos.getY() + 0.5 - y;
            double   dz     = pos.getZ() + 0.5 - z;
            double   dist   = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist >= outerR || dist < 0.01) continue;
            double alpha = (dist <= innerR) ? 1.0
                    : 1.0 - (dist - innerR) / (outerR - innerR);
            gx += dx / dist * alpha;
            gy += dy / dist * alpha;
            gz += dz / dist * alpha;
        }
        return new Vec3(gx, gy, gz);
    }

    /**
     * Legacy single-source lookup; returns the first source whose outer sphere
     * contains the given point, or {@code null}.
     * Prefer {@link #computeGravityVec} for new code.
     */
    @Nullable
    public static synchronized BlockPos getGravitySource(double x, double y, double z) {
        for (int i = 0; i < POSITIONS.size(); i++) {
            BlockPos pos    = POSITIONS.get(i);
            double   outerR = OUTER_RADII.get(i);
            double   dx     = pos.getX() + 0.5 - x;
            double   dy     = pos.getY() + 0.5 - y;
            double   dz     = pos.getZ() + 0.5 - z;
            if (dx * dx + dy * dy + dz * dz <= outerR * outerR) return pos;
        }
        return null;
    }
}
