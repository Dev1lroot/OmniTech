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
     * When {@link SpaceMapSkyboxRenderer#isSuppressingVanillaMoon()} is true,
     * redirect the {@code renderMoon} call inside {@code renderSunMoonAndStars}
     * to a no-op so the vanilla moon disc is not drawn in OmniTech space dimensions.
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
        // else: vanilla moon suppressed while OmniTech space sky is rendering
    }
}
