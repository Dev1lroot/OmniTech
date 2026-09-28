/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.HelmetVisionClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code GameRenderer.update} rebuilds the frame's post-effect request list (vanilla
 * fills it from the server-driven player effects); append the helmet view mode's
 * effect after that, so nothing server-side can clear it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHelmetVisionMixin {

    @Inject(method = "update", at = @At("TAIL"))
    private void omnitech$helmetVision(DeltaTracker deltaTracker, CallbackInfo ci) {
        HelmetVisionClient.addPostEffect(((GameRenderer) (Object) this).getRequestedPostEffects());
    }
}
