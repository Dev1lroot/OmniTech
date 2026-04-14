package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.SpaceMapSkyboxRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
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
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(FLcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void redirectRenderSun(SkyRenderer instance, float rainBrightness, PoseStack poseStack) {
        if (!SpaceMapSkyboxRenderer.isSuppressingVanillaSun()) {
            SpaceMapSkyboxRenderer.invokeSunRender(instance, rainBrightness, poseStack);
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
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void redirectRenderMoon(SkyRenderer instance, MoonPhase moonPhase,
                                    float rainBrightness, PoseStack poseStack) {
        if (!SpaceMapSkyboxRenderer.isSuppressingVanillaMoon()) {
            SpaceMapSkyboxRenderer.invokeMoonRender(instance, moonPhase, rainBrightness, poseStack);
        }
        // else: suppressed — SpaceMapSkyboxRenderer renders the parent planet instead
    }
}
