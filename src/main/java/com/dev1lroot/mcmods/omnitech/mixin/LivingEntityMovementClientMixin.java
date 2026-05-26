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
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementClientMixin {

    @Shadow public float xxa;
    @Shadow public float zza;

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

        Vec3 gv = GravityFieldManager.computeGravityVec(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        if (gv.lengthSqr() < 1e-8) return;

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

        Vec3 gv = GravityFieldManager.computeGravityVec(
                self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ());
        double gLen = gv.length();
        if (gLen < 0.01) return;

        // Gravity-down unit vector (toward attractor)
        Vector3f gravDown = new Vector3f(
                (float)(gv.x / gLen), (float)(gv.y / gLen), (float)(gv.z / gLen));

        // Camera vectors are gravity-rotated by CameraRotationMixin: gravQ × vanillaVector.
        // Use them directly — they represent the actual world-space directions the player
        // sees as forward/left on screen.
        // Sign: xxa > 0 = A key = strafe LEFT = add camLeft (not subtract).
        net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f camFwd  = new Vector3f(cam.forwardVector());
        Vector3f camLeft = new Vector3f(cam.leftVector());

        Vector3f worldInput = new Vector3f(camFwd).mul(savedZza)
                .add(new Vector3f(camLeft).mul(savedXxa));

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
        float moveSpeed = normalizedInput
                * (onSurface ? self.getSpeed() * GROUND_SPEED_FACTOR : 0.02f);

        Vec3 vel = self.getDeltaMovement();
        self.setDeltaMovement(
                vel.x + worldInput.x * moveSpeed,
                vel.y + worldInput.y * moveSpeed,
                vel.z + worldInput.z * moveSpeed);
    }
}
