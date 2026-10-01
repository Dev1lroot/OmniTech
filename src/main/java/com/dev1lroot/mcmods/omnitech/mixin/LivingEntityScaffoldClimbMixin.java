/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.blocks.structure.SteelScaffoldingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Steel scaffolding is a solid cube, so an entity can never stand inside it the
 * way vanilla ladders require. Count an entity pressed against any of its sides
 * as climbing; vanilla's own ladder physics (push forward to go up, sneak to
 * hold, slow descent, no fall damage) then applies on both client and server.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityScaffoldClimbMixin {

    @Shadow private Optional<BlockPos> lastClimbablePos;

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void omnitech$climbSteelScaffolding(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSpectator() || self.isFallFlying()) return;
        BlockPos pos = SteelScaffoldingBlock.isClimbableFrom(self);
        if (pos != null) {
            lastClimbablePos = Optional.of(pos);
            cir.setReturnValue(true);
        }
    }
}
