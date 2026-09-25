/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Corrects block-selection raycasting for the local player inside a gravity field.
 *
 * <p>Entity.pick() casts a ray from {@code getEyePosition(partialTick)} along
 * {@code getViewVector(partialTick)}.  Both must be gravity-rotated so the selected
 * block matches the camera crosshair:
 *
 * <ul>
 *   <li><b>getViewVector</b> — vanilla direction is based on yaw/pitch only.
 *       Rotating by gravQ produces the same direction the camera shows.</li>
 *   <li><b>getEyePosition</b> — vanilla adds eyeHeight along world-Y.  When the player's
 *       body is turned, the ray must start where the turned head is — the same eye point the
 *       camera uses ({@link GravityUtil#pivotHeight}, {@link GravityUtil#eyeOffset}).</li>
 * </ul>
 */
@Mixin(Entity.class)
public class EntityViewVectorGravityMixin {

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void omnitech$applyGravityToViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (Minecraft.getInstance().player != (Object)self) return;
        Quaternionf gravQ = GravityFieldManager.getGravityQ();
        if (gravQ.w > 0.9999f) return;
        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f((float)vanilla.x, (float)vanilla.y, (float)vanilla.z);
        gravQ.transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void omnitech$fixEyePositionForRaycast(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (Minecraft.getInstance().player != (Object)self) return;
        Quaternionf gravQ = GravityFieldManager.getGravityQ();
        if (gravQ.w > 0.9999f) return;
        float eyeH = self.getEyeHeight();
        float pivot = GravityUtil.pivotHeight(self, eyeH, GravityUtil.fieldStrength(self));
        cir.setReturnValue(cir.getReturnValue().add(GravityUtil.eyeOffset(gravQ, eyeH, pivot)));
    }
}
