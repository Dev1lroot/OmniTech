/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applied to LivingEntity on both sides.
 *
 * Gravity field mechanics (two injection points):
 *
 * 1. getEffectiveGravity → returns 0.0 while in a field, so travel() doesn't
 *    apply vanilla downward gravity.
 *
 * 2. travel() HEAD → forces onGround=true when touching any surface, so the
 *    entity gets proper block friction instead of air friction while in the field.
 *    Entity.move() resets onGround based on real collisions after movement runs.
 *
 * 3. travel() RETURN → adds a pull impulse in the direction of the source
 *    after movement is resolved for this tick.
 *
 * 4. jumpFromGround() HEAD (cancellable) → when inside a field the vanilla
 *    upward jump impulse is cancelled and replaced by an equivalent impulse
 *    directed away from the gravity source ("up" in the custom gravity frame).
 *
 * Uses SERVER_ACTIVE_SOURCES on the server and GravityFieldManager on the
 * client so both sides simulate identical physics, preventing rubber-banding
 * for local players while keeping mobs correctly attracted server-side.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGravityMixin {

    private static final double GRAVITY   = 0.08;
    private static final double MAX_SPEED = 1.0;

    @Shadow protected abstract float getJumpPower();
    @Shadow public abstract void jumpFromGround();
    @Shadow protected boolean jumping;
    @Shadow private int noJumpDelay;

    // ── 1. Cancel vanilla gravity ──────────────────────────────────────────────

    @Inject(method = "getEffectiveGravity", at = @At("RETURN"), cancellable = true)
    private void cancelVanillaGravity(CallbackInfoReturnable<Double> cir) {
        if (findSource((LivingEntity)(Object)this) != null) {
            cir.setReturnValue(0.0);
        }
    }

    // ── 2a. Grant surface friction when touching a gravity surface ─────────────
    //
    // When the entity is pressed against a wall/ceiling by custom gravity,
    // isOnGround() is false (no downward-Y block contact), so travel() applies
    // air-friction physics and the entity can barely move.  Forcing onGround=true
    // before travelInAir() runs gives it proper block friction and acceleration.
    // Entity.move() inside travelInAir() resets onGround via setOnGroundWithMovement
    // (based on actual verticalCollisionBelow), so no manual restore is needed.

    @Inject(method = "travel", at = @At("HEAD"))
    private void gravityForceGroundFriction(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.onGround()
                && findSource(self) != null
                && (self.horizontalCollision || self.verticalCollision)) {
            self.setOnGround(true);
        }
    }

    // ── 2b. Apply custom gravity pull ─────────────────────────────────────────

    @Inject(method = "travel", at = @At("RETURN"))
    private void applyCustomGravity(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        BlockPos source = findSource(self);
        if (source == null) return;

        double cy   = self.getY() + self.getBbHeight() * 0.5;
        double dx   = source.getX() + 0.5 - self.getX();
        double dy   = source.getY() + 0.5 - cy;
        double dz   = source.getZ() + 0.5 - self.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        Vec3 current = self.getDeltaMovement();
        Vec3 newVel  = current.add(dx / dist * GRAVITY, dy / dist * GRAVITY, dz / dist * GRAVITY);
        double speed = newVel.length();
        if (speed > MAX_SPEED) newVel = newVel.scale(MAX_SPEED / speed);

        self.setDeltaMovement(newVel);
        self.resetFallDistance();
    }

    // ── 3. Redirect jump away from gravity source ──────────────────────────────

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void redirectJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        BlockPos source = findSource(self);
        if (source == null) return;

        float jumpPower = getJumpPower();
        if (jumpPower <= 1e-5f) return;

        ci.cancel(); // replace vanilla jump

        // Custom "up" = direction away from the source
        double cy   = self.getY() + self.getBbHeight() * 0.5;
        double dx   = self.getX() - (source.getX() + 0.5);
        double dy   = cy           - (source.getY() + 0.5);
        double dz   = self.getZ() - (source.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        double ux = dx / dist;
        double uy = dy / dist;
        double uz = dz / dist;

        Vec3 current = self.getDeltaMovement();

        // Replicate vanilla logic: set the "up" component to max(jumpPower, currentUpSpeed)
        double currentUpSpeed = current.x * ux + current.y * uy + current.z * uz;
        double newUpSpeed = Math.max(jumpPower, currentUpSpeed);
        double delta = newUpSpeed - currentUpSpeed;

        self.setDeltaMovement(
                current.x + ux * delta,
                current.y + uy * delta,
                current.z + uz * delta
        );

        // Sprint boost: keep vanilla horizontal push (small, acceptable in any gravity direction)
        if (self.isSprinting()) {
            double yaw = Math.toRadians(self.getYRot());
            self.addDeltaMovement(new Vec3(-Math.sin(yaw) * 0.2, 0.0, Math.cos(yaw) * 0.2));
        }
    }

    // ── 4. Allow jumping when on a custom-gravity surface ─────────────────────
    //
    // LivingEntity.aiStep() gates jumpFromGround() with isOnGround(), which is
    // false when the entity is pressed against a wall by a sideways gravity source.
    // This injection fires after the vanilla jump block runs; if the vanilla block
    // didn't fire (onGround() was false) but the entity has any surface contact and
    // noJumpDelay is 0, we fire the jump ourselves.  Our redirectJump HEAD injection
    // will then cancel the vanilla impulse and apply the gravity-directed one.

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void customGravityAiStepJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.onGround()) return;           // vanilla already handled it
        if (!jumping) return;
        if (noJumpDelay != 0) return;
        if (findSource(self) == null) return;
        if (!self.horizontalCollision && !self.verticalCollision) return;

        jumpFromGround();
        noJumpDelay = 10;
    }

    // ── Helper: find which gravity source this entity is inside ───────────────

    @Nullable
    private static BlockPos findSource(LivingEntity entity) {
        if (entity.isSpectator() || (entity instanceof Player p && p.isCreative())) return null;

        double cx = entity.getX();
        double cy = entity.getY() + entity.getBbHeight() * 0.5;
        double cz = entity.getZ();

        if (entity.level().isClientSide()) {
            return GravityFieldManager.getGravitySource(cx, cy, cz);
        }

        for (var entry : GravitationSourceBlockEntity.SERVER_ACTIVE_SOURCES.entrySet()) {
            if (!entry.getValue().dimension().equals(entity.level().dimension())) continue;
            BlockPos pos = entry.getKey();
            double ddx = pos.getX() + 0.5 - cx;
            double ddy = pos.getY() + 0.5 - cy;
            double ddz = pos.getZ() + 0.5 - cz;
            double r   = entry.getValue().radius();
            if (ddx * ddx + ddy * ddy + ddz * ddz <= r * r) return pos;
        }
        return null;
    }
}
