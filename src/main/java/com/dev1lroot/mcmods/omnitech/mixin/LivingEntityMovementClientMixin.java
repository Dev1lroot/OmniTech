/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only mixin that redirects WASD movement to align with the actual camera
 * look/left vectors when the local player is inside a gravity field.
 *
 * <p>Strategy:
 * <ol>
 *   <li>{@code travel()} HEAD — save xxa/zza and zero them so vanilla
 *       {@code moveRelative} contributes no velocity this tick.</li>
 *   <li>{@code travel()} RETURN — build worldInput from camera forward/left,
 *       project onto the gravity-horizontal plane, then add the impulse.</li>
 * </ol>
 *
 * <p>While free-floating in zero-g (no field at all, see
 * {@link GravityFieldManager#isFreeFloating}) there is no horizontal plane: the input is applied
 * straight along the camera's forward/left, so a rolled or upside-down player still moves where
 * they look.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementClientMixin {

    @Shadow public float xxa;
    @Shadow public float zza;

    private transient float omnitech$savedXxa;
    private transient float omnitech$savedZza;
    @Unique private transient boolean omnitech$freeFloat;

    /** Vanilla's airborne input speed (LivingEntity#getFlyingSpeed for a player). */
    @Unique private static final float AIR_SPEED = 0.02f;
    @Unique private static final float AIR_SPEED_SPRINTING = 0.026f;

    // ── travel() HEAD: intercept input ────────────────────────────────────────

    @Inject(method = "travel", at = @At("HEAD"))
    private void gravityMovementHead(Vec3 travelVec, CallbackInfo ci) {
        omnitech$savedXxa = 0f;
        omnitech$savedZza = 0f;
        omnitech$freeFloat = false;

        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (Minecraft.getInstance().player != (Object)self) return;
        if (self.isSpectator()) return;
        if (self instanceof Player p && p.isCreative()) return;

        Vec3 gv = GravityFieldManager.computeGravityVec(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        if (gv.lengthSqr() < 1e-8) {
            if (!GravityFieldManager.isFreeFloating()) return;
            omnitech$freeFloat = true;
        }

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

        if (omnitech$freeFloat) {
            omnitech$floatMove(self, savedXxa, savedZza);
            return;
        }

        Vec3 gv = GravityFieldManager.computeGravityVec(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        double gLen = gv.length();
        if (gLen < 0.01) return;

        // Gravity-down unit vector (toward attractor)
        Vector3f gravDown = new Vector3f(
                (float)(gv.x / gLen), (float)(gv.y / gLen), (float)(gv.z / gLen));

        // The player's own turned forward/left (not the camera's: in third-person front view
        // the camera faces the player, which would invert the controls).
        // Sign: xxa > 0 = A key = strafe LEFT = add left (not subtract).
        Vector3f worldInput = omnitech$forward(self).mul(savedZza)
                .add(omnitech$left(self).mul(savedXxa));

        float inputLen = worldInput.length();
        if (inputLen < 0.001f) return;
        worldInput.div(inputLen);

        // Project onto gravity-horizontal plane (perpendicular to gravDown)
        float dotGD = worldInput.dot(gravDown);
        worldInput.sub(new Vector3f(gravDown).mul(dotGD));
        float projLen = worldInput.length();
        if (projLen < 0.001f) return;
        worldInput.div(projLen);

        // Speed mirrors vanilla moveRelative
        float normalizedInput = Math.min(inputLen, 1.0f);
        boolean onSurface = self.onGround() || self.horizontalCollision || self.verticalCollision;
        // Vanilla (26.3) walks at plain getSpeed() on normal-friction ground
        float moveSpeed = normalizedInput
                * (onSurface ? self.getSpeed() : (self.isSprinting() ? AIR_SPEED_SPRINTING : AIR_SPEED));

        Vec3 vel = self.getDeltaMovement();
        self.setDeltaMovement(
                vel.x + worldInput.x * moveSpeed,
                vel.y + worldInput.y * moveSpeed,
                vel.z + worldInput.z * moveSpeed);
    }

    /** Zero-g: push along the player's own turned forward/left at vanilla's airborne speed. */
    @Unique
    private static void omnitech$floatMove(LivingEntity self, float xxa, float zza) {
        Vector3f input = omnitech$forward(self).mul(zza).add(omnitech$left(self).mul(xxa));
        float inputLen = input.length();
        if (inputLen < 0.001f) return;
        input.div(inputLen);

        float moveSpeed = Math.min(inputLen, 1.0f) * (self.isSprinting() ? AIR_SPEED_SPRINTING : AIR_SPEED);
        self.setDeltaMovement(self.getDeltaMovement().add(
                input.x * moveSpeed, input.y * moveSpeed, input.z * moveSpeed));
    }

    /** Look direction turned by the gravity frame (what the crosshair points along). */
    @Unique
    private static Vector3f omnitech$forward(LivingEntity self) {
        Vec3 look = net.minecraft.world.entity.Entity.calculateViewVector(self.getXRot(), self.getYRot());
        return GravityFieldManager.getGravityQ().transform(new Vector3f((float) look.x, (float) look.y, (float) look.z));
    }

    /** The player's left (vanilla: level, perpendicular to yaw) turned by the gravity frame. */
    @Unique
    private static Vector3f omnitech$left(LivingEntity self) {
        float yaw = (float) Math.toRadians(self.getYRot());
        return GravityFieldManager.getGravityQ().transform(new Vector3f((float) Math.cos(yaw), 0f, (float) Math.sin(yaw)));
    }
}
