/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gravity-field physics for non-living entities (dropped items, etc.).
 *
 * 1. {@code applyGravity()} HEAD — suppresses vanilla gravity for any non-
 *    {@link LivingEntity} inside a gravity field.  LivingEntity vanilla gravity
 *    is handled by {@link LivingEntityGravityMixin}.
 *
 * 2. {@code Entity.tick()} RETURN — adds the pull impulse toward the combined
 *    attractor.  For {@link net.minecraft.world.entity.item.ItemEntity} this fires
 *    inside the {@code super.tick()} call, before {@code applyGravity()} and
 *    {@code move()} — correct ordering.
 *    {@link net.minecraft.world.entity.item.FallingBlockEntity} skips super.tick(),
 *    so its pull is handled by {@link FallingBlockEntityGravityMixin}.
 */
@Mixin(Entity.class)
public abstract class EntityGravityPhysicsMixin {

    private static final double GRAVITY   = 0.04;
    private static final double MAX_SPEED = 1.0;

    @Inject(method = "applyGravity", at = @At("HEAD"), cancellable = true)
    private void cancelGravityInField(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self instanceof LivingEntity) return;
        if (GravityUtil.computeGravityVec(self).lengthSqr() > 1e-8) ci.cancel();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void applyPullForItems(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self instanceof LivingEntity) return;

        Vec3 gv   = GravityUtil.computeGravityVec(self);
        if (gv.lengthSqr() < 1e-8) return;

        Vec3   vel = self.getDeltaMovement().add(gv.x * GRAVITY, gv.y * GRAVITY, gv.z * GRAVITY);
        double sp  = vel.length();
        if (sp > MAX_SPEED) vel = vel.scale(MAX_SPEED / sp);
        self.setDeltaMovement(vel);
    }
}
