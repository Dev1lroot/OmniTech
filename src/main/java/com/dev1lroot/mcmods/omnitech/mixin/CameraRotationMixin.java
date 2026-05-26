/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
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
 * offsets the camera by eyeHeight along world-Y. When gravity tilts the player, the
 * eye must instead be offset along the gravity-up direction so the camera sits at the
 * player's actual head, not at their feet.
 * correctedPos = entityFeetPos + gravUp_smoothed × eyeHeight
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

        // smoothed gravity-up = gravQ applied to world-up (0,1,0)
        // this is in sync with the rotation smoothing from OmniTechClient
        Vector3f gravUp = new Vector3f(0f, 1f, 0f);
        gravQ.transform(gravUp);

        float eyeH = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);

        // Vanilla added (0, eyeH, 0); we replace that with gravUp * eyeH.
        // delta = (gravUp - world_up) * eyeH
        setPosition(this.position.add(
            gravUp.x * eyeH,
            (gravUp.y - 1.0) * eyeH,
            gravUp.z * eyeH
        ));
    }
}
