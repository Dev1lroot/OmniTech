/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Directional gravity pull for falling blocks.
 *
 * {@link FallingBlockEntity#tick()} does not call {@code super.tick()}, so the
 * pull injection in {@link EntityGravityPhysicsMixin} never fires for it.
 * Injecting at HEAD here adds the impulse before {@code applyGravity()} and
 * {@code move()} run.  The vanilla gravity cancellation is handled by
 * {@link EntityGravityPhysicsMixin#cancelGravityInField}.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityGravityMixin {

    private static final double GRAVITY   = 0.04;
    private static final double MAX_SPEED = 1.0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void applyPullForFallingBlock(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity)(Object)this;

        Vec3 gv = GravityUtil.computeGravityVec(self);
        if (gv.lengthSqr() < 1e-8) return;

        Vec3   vel = self.getDeltaMovement().add(gv.x * GRAVITY, gv.y * GRAVITY, gv.z * GRAVITY);
        double sp  = vel.length();
        if (sp > MAX_SPEED) vel = vel.scale(MAX_SPEED / sp);
        self.setDeltaMovement(vel);
    }
}
