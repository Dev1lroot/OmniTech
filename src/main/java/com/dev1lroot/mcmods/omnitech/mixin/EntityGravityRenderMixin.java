/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates entity models so their "bottom" face points toward the combined gravity
 * attractor direction.  The rotation is weighted by the combined field strength α
 * so models gradually tilt as entities enter the outer zone.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EntityGravityRenderMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void applyGravityRotation(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci) {

        double entityCX = state.x;
        double entityCY = state.y + state.boundingBoxHeight * 0.5;
        double entityCZ = state.z;

        Vec3 gv   = GravityFieldManager.computeGravityVec(entityCX, entityCY, entityCZ);
        double gLen = gv.length();
        if (gLen < 0.01) return;

        // Full rotation: (0,−1,0) → toward attractor
        Quaternionf fullRot = new Quaternionf().rotationTo(
                new Vector3f(0f, -1f, 0f),
                new Vector3f((float)(gv.x / gLen), (float)(gv.y / gLen), (float)(gv.z / gLen)));

        // Blend between identity and fullRot by α so the model tilts gradually
        float alpha    = (float) Math.min(1.0, gLen);
        Quaternionf rotation = new Quaternionf().slerp(fullRot, alpha);

        float halfHeight = state.boundingBoxHeight * 0.5f;
        poseStack.translate(0.0, halfHeight, 0.0);
        poseStack.rotate(rotation);
        poseStack.translate(0.0, -halfHeight, 0.0);
    }
}
