/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.network.GravityFieldSyncPacket;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side store of active gravity field sources received via
 * {@link GravityFieldSyncPacket}.
 *
 * <p>Queried by {@link com.dev1lroot.mcmods.omnitech.mixin.EntityGravityRenderMixin}
 * to determine which entity models need to be rotated so their bottom face points
 * toward the nearest gravity source.
 */
public final class GravityFieldManager {

    private static final List<BlockPos>          POSITIONS  = new ArrayList<>();
    private static final List<Integer>           RADII      = new ArrayList<>();
    private static final org.joml.Quaternionf    GRAVITY_Q  = new org.joml.Quaternionf();

    private GravityFieldManager() {}

    /** Replace the active source list with data from the latest sync packet. */
    public static synchronized void updateFromPacket(GravityFieldSyncPacket pkt) {
        POSITIONS.clear();
        POSITIONS.addAll(pkt.positions());
        RADII.clear();
        RADII.addAll(pkt.radii());
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
        RADII.clear();
        GRAVITY_Q.identity();
    }

    /**
     * Returns the {@link BlockPos} of the first gravity source whose sphere contains
     * the given point, or {@code null} if no source is active at that location.
     *
     * @param entityX entity center X (world coords)
     * @param entityY entity center Y (world coords)
     * @param entityZ entity center Z (world coords)
     */
    @Nullable
    public static synchronized BlockPos getGravitySource(double entityX, double entityY, double entityZ) {
        for (int i = 0; i < POSITIONS.size(); i++) {
            BlockPos pos    = POSITIONS.get(i);
            double   radius = RADII.get(i);
            double   cx     = pos.getX() + 0.5;
            double   cy     = pos.getY() + 0.5;
            double   cz     = pos.getZ() + 0.5;
            double   dx     = entityX - cx;
            double   dy     = entityY - cy;
            double   dz     = entityZ - cz;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                return pos;
            }
        }
        return null;
    }
}
