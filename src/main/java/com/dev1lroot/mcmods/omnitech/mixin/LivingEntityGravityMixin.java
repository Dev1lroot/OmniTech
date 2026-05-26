/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gravity-field physics for living entities.
 *
 * All injection points use the combined gravity vector from
 * {@link GravityUtil#computeGravityVec}, which sums contributions from every
 * active source and encodes field strength as its magnitude (α ∈ [0,1] per source,
 * linearly interpolated between inner and outer radii).
 *
 * 1. {@code getEffectiveGravity} RETURN — scales vanilla gravity by (1 − α) so it
 *    fades out smoothly as the entity enters the outer zone.
 *
 * 2. {@code travel()} HEAD — forces {@code onGround=true} when the entity is pressed
 *    against any surface inside a gravity field, giving it proper block friction.
 *
 * 3. {@code travel()} RETURN — adds the custom pull impulse in the direction of the
 *    combined gravity attractor, scaled by the field strength.
 *
 * 4. {@code jumpFromGround()} HEAD — replaces the vanilla upward jump with an
 *    equivalent impulse directed away from the gravity attractor.
 *
 * 5. {@code aiStep()} RETURN — fires the redirected jump for entities touching a
 *    custom-gravity surface that vanilla would not consider "on ground".
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGravityMixin {

    private static final double GRAVITY   = 0.08;
    private static final double MAX_SPEED = 1.0;

    /** Threshold for "entity is meaningfully inside a gravity field". */
    private static final double FIELD_THRESHOLD_SQ = 1e-8;

    @Shadow protected abstract float getJumpPower();
    @Shadow public abstract void jumpFromGround();
    @Shadow protected boolean jumping;
    @Shadow private int noJumpDelay;

    // ── Per-tick gravity vector cache ─────────────────────────────────────────

    @Unique private Vec3 omnitech$gravVec  = Vec3.ZERO;
    @Unique private int  omnitech$gravTick = -1;

    @Unique
    private Vec3 omnitech$grav() {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.tickCount != omnitech$gravTick) {
            omnitech$gravVec  = GravityUtil.computeGravityVec(self);
            omnitech$gravTick = self.tickCount;
        }
        return omnitech$gravVec;
    }

    // ── 1. Fade vanilla gravity by (1 − α) ───────────────────────────────────

    @Inject(method = "getEffectiveGravity", at = @At("RETURN"), cancellable = true)
    private void fadeVanillaGravity(CallbackInfoReturnable<Double> cir) {
        double alpha = Math.min(1.0, omnitech$grav().length());
        if (alpha > 0) {
            cir.setReturnValue(cir.getReturnValue() * (1.0 - alpha));
        }
    }

    // ── 2. Grant surface friction when touching a gravity surface ─────────────

    @Inject(method = "travel", at = @At("HEAD"))
    private void gravityForceGroundFriction(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.onGround()
                && omnitech$grav().lengthSqr() > FIELD_THRESHOLD_SQ
                && (self.horizontalCollision || self.verticalCollision)) {
            self.setOnGround(true);
        }
    }

    // ── 3. Apply combined gravity pull ────────────────────────────────────────

    @Inject(method = "travel", at = @At("RETURN"))
    private void applyCustomGravity(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 gv = omnitech$grav();
        if (gv.lengthSqr() < FIELD_THRESHOLD_SQ) return;

        Vec3   current = self.getDeltaMovement();
        Vec3   newVel  = current.add(gv.x * GRAVITY, gv.y * GRAVITY, gv.z * GRAVITY);
        double speed   = newVel.length();
        if (speed > MAX_SPEED) newVel = newVel.scale(MAX_SPEED / speed);

        self.setDeltaMovement(newVel);
        self.resetFallDistance();
    }

    // ── 4. Redirect jump away from gravity attractor ──────────────────────────

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void redirectJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 gv = omnitech$grav();
        if (gv.lengthSqr() < FIELD_THRESHOLD_SQ) return;

        float jumpPower = getJumpPower();
        if (jumpPower <= 1e-5f) return;

        ci.cancel();

        // Custom "up" = direction away from combined attractor
        double gLen = gv.length();
        double ux   = -gv.x / gLen;
        double uy   = -gv.y / gLen;
        double uz   = -gv.z / gLen;

        Vec3   current      = self.getDeltaMovement();
        double currentUp    = current.x * ux + current.y * uy + current.z * uz;
        double newUp        = Math.max(jumpPower, currentUp);
        double delta        = newUp - currentUp;

        self.setDeltaMovement(
                current.x + ux * delta,
                current.y + uy * delta,
                current.z + uz * delta);

        if (self.isSprinting()) {
            double yaw = Math.toRadians(self.getYRot());
            self.addDeltaMovement(new Vec3(-Math.sin(yaw) * 0.2, 0.0, Math.cos(yaw) * 0.2));
        }
    }

    // ── 5. Allow jumping when on a custom-gravity surface ────────────────────

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void customGravityAiStepJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.onGround()) return;
        if (!jumping) return;
        if (noJumpDelay != 0) return;
        if (omnitech$grav().lengthSqr() < FIELD_THRESHOLD_SQ) return;
        if (!self.horizontalCollision && !self.verticalCollision) return;

        jumpFromGround();
        noJumpDelay = 10;
    }
}
