/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item raycasts (buckets, boats, bottles, lily pads, ...) build their ray from the player's raw
 * yaw/pitch, which on the server knows nothing of gravity: a player standing on an asteroid's side
 * or rolled in zero-g would fill or place at a block they aren't looking at. Here the ray starts at
 * the turned body's real eye and runs along the turned view, the same as the player's crosshair.
 */
@Mixin(Item.class)
public abstract class ItemGravityAimMixin {

    @Inject(method = "getPlayerPOVHitResult", at = @At("HEAD"), cancellable = true)
    private static void omnitech$gravityAim(Level level, Player player, ClipContext.Fluid fluid,
            CallbackInfoReturnable<BlockHitResult> cir) {
        Quaternionf frame = GravityUtil.frameOf(player);
        if (frame == null) return;

        float eyeHeight = player.getEyeHeight();
        float pivot = GravityUtil.pivotHeight(player, eyeHeight, GravityUtil.fieldStrength(player));
        Vec3 from = player.getEyePosition().add(GravityUtil.eyeOffset(frame, eyeHeight, pivot));
        Vec3 dir = GravityUtil.rotate(frame, Player.calculateViewVector(player.getXRot(), player.getYRot()));
        Vec3 to = from.add(dir.scale(player.blockInteractionRange()));
        cir.setReturnValue(level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, fluid, player)));
    }
}
