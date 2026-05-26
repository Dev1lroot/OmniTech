/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only mixin that redirects WASD movement to align with the gravity-adjusted
 * camera orientation when the local player is inside a gravity field.
 *
 * <p>Vanilla {@code moveRelative} rotates input by {@code yRot} in the world-XZ plane.
 * When the player stands on a wall or ceiling relative to a gravity source this produces
 * wrong-direction or inverted movement.
 *
 * <p>Strategy:
 * <ol>
 *   <li>{@code travel()} HEAD – save {@code xxa}/{@code zza}, zero them so vanilla
 *       {@code moveRelative} contributes no velocity this tick.</li>
 *   <li>{@code travel()} RETURN – rotate the saved input by the smoothed gravity
 *       quaternion, project onto the gravity-horizontal plane, and add the equivalent
 *       impulse to {@code deltaMovement}.</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementClientMixin {

    @Shadow public float xxa;
    @Shadow public float zza;

    /** Saved at HEAD so RETURN can apply the input in the correct direction. */
    private transient float omnitech$savedXxa;
    private transient float omnitech$savedZza;

    /** Matches MC's on-ground moveRelative speed formula: 0.16277136 / friction³, friction=0.6. */
    private static final float GROUND_SPEED_FACTOR = 0.16277136f / (0.6f * 0.6f * 0.6f);

    // ── travel() HEAD: intercept input ────────────────────────────────────────

    @Inject(method = "travel", at = @At("HEAD"))
    private void gravityMovementHead(Vec3 travelVec, CallbackInfo ci) {
        omnitech$savedXxa = 0f;
        omnitech$savedZza = 0f;

        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (Minecraft.getInstance().player != (Object)self) return;
        if (self.isSpectator()) return;
        if (self instanceof Player p && p.isCreative()) return;

        BlockPos src = GravityFieldManager.getGravitySource(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        if (src == null) return;

        Quaternionf gQ = GravityFieldManager.getGravityQ();
        if (gQ.w > 0.9999f) return; // essentially identity — vanilla movement is correct

        omnitech$savedXxa = xxa;
        omnitech$savedZza = zza;
        xxa = 0f;
        zza = 0f;
    }

    // ── travel() RETURN: apply gravity-frame movement ─────────────────────────

    @Inject(method = "travel", at = @At("RETURN"))
    private void gravityMovementReturn(Vec3 travelVec, CallbackInfo ci) {
        float savedXxa = omnitech$savedXxa;
        float savedZza = omnitech$savedZza;
        if (savedXxa == 0f && savedZza == 0f) return;

        LivingEntity self = (LivingEntity)(Object)this;

        BlockPos src = GravityFieldManager.getGravitySource(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        if (src == null) return;

        // ── Gravity-down unit vector (toward source) ──────────────────────────
        double cx   = self.getX();
        double cy   = self.getY() + self.getBbHeight() * 0.5;
        double cz   = self.getZ();
        double dx   = src.getX() + 0.5 - cx;
        double dy   = src.getY() + 0.5 - cy;
        double dz   = src.getZ() + 0.5 - cz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;
        Vector3f gravDown = new Vector3f((float)(dx / dist), (float)(dy / dist), (float)(dz / dist));

        // ── Vanilla input → world direction (mirrors Entity.getInputVector) ───
        // forward = (-sin(yaw), 0, cos(yaw))   right = (cos(yaw), 0, sin(yaw))
        float yawRad = (float) Math.toRadians(self.getYRot());
        float sy     = (float) Math.sin(yawRad);
        float cy2    = (float) Math.cos(yawRad);
        Vector3f worldInput = new Vector3f(
                -sy * savedZza + cy2 * savedXxa,
                0f,
                cy2 * savedZza + sy * savedXxa);

        float inputLen = worldInput.length();
        if (inputLen < 0.001f) return;
        worldInput.div(inputLen);

        // ── Rotate by gravity Q → 3-D gravity-adjusted direction ─────────────
        Quaternionf gQ = GravityFieldManager.getGravityQ();
        gQ.transform(worldInput);

        // ── Project onto gravity-horizontal plane ─────────────────────────────
        float dotGD = worldInput.dot(gravDown);
        worldInput.sub(new Vector3f(gravDown).mul(dotGD));
        float projLen = worldInput.length();
        if (projLen < 0.001f) return;
        worldInput.div(projLen);

        // ── Speed: mirrors vanilla moveRelative scale ─────────────────────────
        float normalizedInput = Math.min(inputLen, 1.0f);
        boolean onSurface = self.onGround() || self.horizontalCollision || self.verticalCollision;
        float moveSpeed = normalizedInput
                * (onSurface ? self.getSpeed() * GROUND_SPEED_FACTOR : 0.02f);

        Vec3 vel = self.getDeltaMovement();
        self.setDeltaMovement(
                vel.x + worldInput.x * moveSpeed,
                vel.y + worldInput.y * moveSpeed,
                vel.z + worldInput.z * moveSpeed);
    }
}
