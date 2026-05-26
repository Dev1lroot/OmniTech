/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class GravityUtil {

    private GravityUtil() {}

    /**
     * Computes the combined gravity pull vector for {@code entity}.
     *
     * <p>The returned {@link Vec3} points from the entity toward the combined gravity
     * attractor.  Its magnitude encodes the combined field strength (α = 1.0 inside
     * any inner radius, 0.0 outside all outer radii, linearly interpolated between
     * them; multiple sources add).  Returns {@link Vec3#ZERO} when outside all fields.
     *
     * <p>Creative players and spectators always return {@link Vec3#ZERO}.
     */
    public static Vec3 computeGravityVec(Entity entity) {
        if (entity.isSpectator()) return Vec3.ZERO;
        if (entity instanceof Player p && p.isCreative()) return Vec3.ZERO;

        double cx = entity.getX();
        double cy = entity.getY() + entity.getBbHeight() * 0.5;
        double cz = entity.getZ();

        if (entity.level().isClientSide()) {
            return GravityFieldManager.computeGravityVec(cx, cy, cz);
        }

        double gx = 0, gy = 0, gz = 0;
        for (var entry : GravitationSourceBlockEntity.SERVER_ACTIVE_SOURCES.entrySet()) {
            if (!entry.getValue().dimension().equals(entity.level().dimension())) continue;
            BlockPos pos    = entry.getKey();
            int      outerR = entry.getValue().outerRadius();
            int      innerR = entry.getValue().innerRadius();
            double   dx     = pos.getX() + 0.5 - cx;
            double   dy     = pos.getY() + 0.5 - cy;
            double   dz     = pos.getZ() + 0.5 - cz;
            double   dist   = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist >= outerR || dist < 0.01) continue;
            double alpha = (dist <= innerR) ? 1.0
                    : 1.0 - (dist - innerR) / (double)(outerR - innerR);
            gx += dx / dist * alpha;
            gy += dy / dist * alpha;
            gz += dz / dist * alpha;
        }
        return new Vec3(gx, gy, gz);
    }
}
