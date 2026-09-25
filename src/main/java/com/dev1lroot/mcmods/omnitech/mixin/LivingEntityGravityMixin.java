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
 * 2. {@code travel()} HEAD — forces {@code onGround=true} so vanilla computes
 *    ground-level move speed, and applies gravity-frame friction to the carry-over
 *    velocity for wall/ceiling surfaces (vanilla only dampens world-XZ for onGround).
 *
 * 3. {@code travel()} RETURN — applies the gravity pull impulse.  When touching a
 *    surface only the component pressing INTO the surface is applied; the tangential
 *    component is dropped so the player does not slide toward the 6 cardinal poles.
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
            return;
        }
        // In zero-gravity dimensions, suppress vanilla gravity even between asteroid fields
        LivingEntity self = (LivingEntity)(Object)this;
        if (GravityUtil.isZeroGravityDimension(self.level())) {
            cir.setReturnValue(0.0);
        }
    }

    // ── 2. Grant surface friction when touching a gravity surface ─────────────

    @Inject(method = "travel", at = @At("HEAD"))
    private void gravityFrameSetup(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 gv = omnitech$grav();
        if (gv.lengthSqr() <= FIELD_THRESHOLD_SQ) return;

        boolean actuallyOnGround  = self.onGround();
        boolean onWallOrCeiling   = self.horizontalCollision || self.verticalCollision;

        // Let vanilla think the player is on the ground so it uses ground movement speed.
        if (!actuallyOnGround && onWallOrCeiling) {
            self.setOnGround(true);
        }

        // For wall/ceiling surfaces vanilla's friction (world-XZ, onGround only) does not
        // apply.  Damp the carry-over tangential velocity here (before any impulses are
        // added this tick) so the player stops when releasing movement keys.
        if (onWallOrCeiling && !actuallyOnGround) {
            double gLen = gv.length();
            if (gLen < 0.001) return;
            double gdx = gv.x / gLen, gdy = gv.y / gLen, gdz = gv.z / gLen;
            Vec3   cur = self.getDeltaMovement();
            // Component along gravity axis (preserved) vs tangential (friction'd)
            double n  = cur.x * gdx + cur.y * gdy + cur.z * gdz;
            double tx = cur.x - n * gdx;
            double ty = cur.y - n * gdy;
            double tz = cur.z - n * gdz;
            double slip = 0.6; // same as typical Minecraft block friction
            self.setDeltaMovement(n * gdx + tx * slip,
                                  n * gdy + ty * slip,
                                  n * gdz + tz * slip);
        }
    }

    // ── 3. Apply combined gravity pull ────────────────────────────────────────

    @Inject(method = "travel", at = @At("RETURN"))
    private void applyCustomGravity(Vec3 travelVec, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        Vec3 gv = omnitech$grav();
        if (gv.lengthSqr() < FIELD_THRESHOLD_SQ) return;

        double gLen  = gv.length();
        double alpha = Math.min(1.0, gLen);
        double gdx   = gv.x / gLen, gdy = gv.y / gLen, gdz = gv.z / gLen;

        // Full gravity impulse points toward the attractor (gravDown direction).
        Vec3 fullImpulse = new Vec3(gdx * GRAVITY * alpha,
                                    gdy * GRAVITY * alpha,
                                    gdz * GRAVITY * alpha);
        Vec3 impulse;

        boolean onSurface = self.onGround() || self.horizontalCollision || self.verticalCollision;
        if (onSurface) {
            // When touching a surface, apply only the component that presses the player
            // INTO the surface.  The tangential component is intentionally discarded —
            // it is what causes the player to slide toward the 6 cardinal poles.
            // The pressing-in component is canceled by collision anyway; we keep it so
            // the player stays grounded the instant they step back onto the surface.
            Vec3   normal = omnitech$surfaceNormal(self, gdx, gdz);
            double dot    = fullImpulse.dot(normal); // negative = pressing in
            impulse = dot < 0 ? normal.scale(dot) : fullImpulse;
        } else {
            // Free flight: full pull toward attractor.
            impulse = fullImpulse;
        }

        Vec3   current = self.getDeltaMovement();
        Vec3   newVel  = current.add(impulse);
        double speed   = newVel.length();
        if (speed > MAX_SPEED) newVel = newVel.scale(MAX_SPEED / speed);

        self.setDeltaMovement(newVel);
        self.resetFallDistance();
    }

    /**
     * Infers the outward surface normal from which collision flag is active.
     * Used to separate the pressing-in from the tangential gravity component.
     */
    @Unique
    private static Vec3 omnitech$surfaceNormal(LivingEntity self, double gdx, double gdz) {
        if (self.onGround()) return new Vec3(0, 1, 0);
        if (self.verticalCollision) return new Vec3(0, -1, 0);
        // horizontalCollision: wall.  The horizontal gravity direction tells us which
        // wall — the normal points opposite to the horizontal gravity.
        double hLen = Math.sqrt(gdx * gdx + gdz * gdz);
        return hLen > 0.001 ? new Vec3(-gdx / hLen, 0, -gdz / hLen)
                            : new Vec3(0, 1, 0);
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
