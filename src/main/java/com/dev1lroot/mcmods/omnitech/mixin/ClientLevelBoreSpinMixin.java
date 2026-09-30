/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.BoreSpin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records who is breaking blocks, so their bore head can spin (see {@link BoreSpin}). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBoreSpinMixin {

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void omnitech$trackBreaker(int id, BlockPos pos, int progress, CallbackInfo ci) {
        BoreSpin.onDestroyProgress(id, progress);
    }
}
