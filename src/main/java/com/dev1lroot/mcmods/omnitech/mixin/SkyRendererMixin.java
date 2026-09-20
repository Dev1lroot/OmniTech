/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.SpaceMapSkyboxRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    /**
     * Suppress the vanilla sun disc when {@link SpaceMapSkyboxRenderer} is active.
     * The renderer replaces it with a distance-scaled version using the star's own texture.
     */
    @Redirect(
            method = "renderSunMoonAndStars",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(Lcom/mojang/renderpearl/api/commands/RenderPass;FLcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void redirectRenderSun(SkyRenderer instance, RenderPass renderPass, float rainBrightness, PoseStack poseStack) {
        if (!SpaceMapSkyboxRenderer.isSuppressingVanillaSun()) {
            SpaceMapSkyboxRenderer.invokeSunRender(instance, renderPass, rainBrightness, poseStack);
        }
        // else: suppressed — SpaceMapSkyboxRenderer renders its own scaled star
    }

    /**
     * Suppress the vanilla moon disc when {@link SpaceMapSkyboxRenderer} is active.
     * Parent bodies (planets) are rendered explicitly by the custom sky renderer instead.
     */
    @Redirect(
            method = "renderSunMoonAndStars",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void redirectRenderMoon(SkyRenderer instance, RenderPass renderPass, MoonPhase moonPhase,
                                    float rainBrightness, PoseStack poseStack) {
        if (!SpaceMapSkyboxRenderer.isSuppressingVanillaMoon()) {
            SpaceMapSkyboxRenderer.invokeMoonRender(instance, renderPass, moonPhase, rainBrightness, poseStack);
        }
        // else: suppressed — SpaceMapSkyboxRenderer renders the parent planet instead
    }
}
