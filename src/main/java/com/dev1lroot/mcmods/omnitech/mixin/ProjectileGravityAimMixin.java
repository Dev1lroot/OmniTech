/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Arrows, snowballs, tridents and every other projectile a player throws or shoots are launched
 * along the player's raw yaw/pitch; turn that direction by the player's gravity frame so it flies
 * where the (tilted or rolled) player is actually looking.
 */
@Mixin(Projectile.class)
public abstract class ProjectileGravityAimMixin {

    @ModifyArgs(method = "shootFromRotation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"))
    private void omnitech$gravityAim(Args args, Entity source, float xRot, float yRot, float yOffset,
            float pow, float uncertainty) {
        if (!(source instanceof Player player)) return;
        Quaternionf frame = GravityUtil.frameOf(player);
        if (frame == null) return;
        Vec3 dir = GravityUtil.rotate(frame, new Vec3(args.<Double>get(0), args.<Double>get(1), args.<Double>get(2)));
        args.set(0, dir.x);
        args.set(1, dir.y);
        args.set(2, dir.z);
    }
}
