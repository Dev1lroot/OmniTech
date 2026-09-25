/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

public final class GravityUtil {

    private GravityUtil() {}

    /** Dimensions where vanilla gravity is completely absent (no ambient pull between gravity fields). */
    private static final Set<ResourceKey<Level>> ZERO_GRAVITY_DIMS = Set.of(
        ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("omnitech", "kuiper_belt"))
    );

    /** True in a deep-space dimension such as the Kuiper Belt, where only gravity fields pull. */
    public static boolean isZeroGravityDimension(Level level) {
        return ZERO_GRAVITY_DIMS.contains(level.dimension());
    }

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
            BlockPos pos    = entry.getKey().pos();
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

    // ── Player frame (body/view orientation) ──────────────────────────────────

    /**
     * {@code player}'s gravity frame (see {@link PlayerFrames}), or {@code null} if upright. On a
     * client this is the local player's own frame or the one relayed for another player; on the
     * server it is the frame the player last reported.
     */
    public static @Nullable Quaternionf frameOf(Player player) {
        if (player.level().isClientSide()) {
            return player.getId() == PlayerFrames.localPlayerId()
                    ? PlayerFrames.getLocal() : PlayerFrames.getRemote(player.getId());
        }
        return PlayerFrames.getServer(player.getUUID());
    }

    /** Field strength α ∈ [0, 1] at {@code entity}'s centre (0 = no field). */
    public static float fieldStrength(Entity entity) {
        return (float) Math.min(1.0, computeGravityVec(entity).length());
    }

    /**
     * Height above the feet that a tilted body turns about: its centre inside a gravity field
     * (where the model has always pivoted, so the head stays at the surface it stands on), its eyes
     * while floating free (so rolling spins the view in place), blended by field strength α.
     */
    public static float pivotHeight(Entity entity, float eyeHeight, float alpha) {
        return eyeHeight + (entity.getBbHeight() * 0.5f - eyeHeight) * alpha;
    }

    /**
     * What to add to the vanilla eye position (feet + eyeHeight straight up) so the eye sits where
     * the turned body's head actually is: pivot + frame·(eye − pivot).
     */
    public static Vec3 eyeOffset(Quaternionf frame, float eyeHeight, float pivot) {
        Vector3f up = frame.transform(new Vector3f(0f, 1f, 0f));
        float arm = eyeHeight - pivot;
        return new Vec3(up.x * arm, up.y * arm - arm, up.z * arm);
    }

    /** {@code v} turned by {@code frame}. */
    public static Vec3 rotate(Quaternionf frame, Vec3 v) {
        Vector3f r = frame.transform(new Vector3f((float) v.x, (float) v.y, (float) v.z));
        return new Vec3(r.x, r.y, r.z);
    }
}
