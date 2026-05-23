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
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates entity models so their "bottom" face points toward the nearest active
 * {@link com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlock}.
 *
 * In MC 26.1 the rendering pipeline changed from render(Entity,...) to
 * submit(RenderState, PoseStack, ...). We inject at HEAD of the LivingEntityRenderState
 * overload, which fires inside EntityRenderDispatcher's own push/pop scope and is
 * therefore properly undone after each entity's submit returns.
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

        BlockPos source = GravityFieldManager.getGravitySource(entityCX, entityCY, entityCZ);
        if (source == null) return;

        double cx  = source.getX() + 0.5 - entityCX;
        double cy  = source.getY() + 0.5 - entityCY;
        double cz  = source.getZ() + 0.5 - entityCZ;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 0.01) return;

        Quaternionf rotation = new Quaternionf().rotationTo(
                new Vector3f(0f, -1f, 0f),
                new Vector3f((float)(cx / len), (float)(cy / len), (float)(cz / len)));

        float halfHeight = state.boundingBoxHeight * 0.5f;
        poseStack.translate(0.0, halfHeight, 0.0);
        poseStack.mulPose(rotation);
        poseStack.translate(0.0, -halfHeight, 0.0);
    }
}
