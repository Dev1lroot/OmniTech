/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two injections into Camera for gravity-field support:
 *
 * <p><b>1. setRotation</b> — left-multiplies the camera quaternion by gravQ after
 * vanilla builds it via rotationYXZ(), so camera-up = gravQ × world_up = gravUp.
 * No Euler decomposition, no gimbal lock.
 * finalRotation = gravQ × vanillaRotation
 *
 * <p><b>2. alignWithEntity</b> — corrects the eye-position offset. Vanilla always
 * offsets the camera by eyeHeight along world-Y. When gravity turns the player, the eye
 * must follow the turned body: the body turns about a pivot (its centre in a gravity field —
 * the same point the model turns about, so the camera sits in the drawn head — and its eyes
 * while floating free, so a zero-g roll spins the view in place), see
 * {@link GravityUtil#pivotHeight}.
 * correctedPos = feet + pivot·worldUp + gravQ·((eyeHeight − pivot)·worldUp)
 */
@Mixin(Camera.class)
public abstract class CameraRotationMixin {

    @Shadow @Final private Quaternionf rotation;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private Vec3 position;

    @Shadow protected abstract void setPosition(Vec3 pos);

    // ── 1. Rotation ───────────────────────────────────────────────────────────

    @Inject(
        method = "setRotation(FFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void omnitech$applyGravityRotation(float yRot, float xRot, float roll, CallbackInfo ci) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        Quaternionf gravQ = GravityFieldManager.getGravityQ();
        if (gravQ.w > 0.9999f) return;
        // rotation = gravQ × vanillaRotation
        // vanilla immediately re-derives forwards/up/left from this.rotation
        this.rotation.premul(gravQ);
    }

    // ── 2. Eye-position correction ────────────────────────────────────────────

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void omnitech$fixEyePosition(float partialTicks, CallbackInfo ci) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        Quaternionf gravQ = GravityFieldManager.getGravityQ();
        if (gravQ.w > 0.9999f) return;
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity == null) return;

        float eyeH = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
        float pivot = GravityUtil.pivotHeight(entity, eyeH, GravityUtil.fieldStrength(entity));
        setPosition(this.position.add(GravityUtil.eyeOffset(gravQ, eyeH, pivot)));
    }
}
