package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.client.MoonSkyboxRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    /**
     * When {@link MoonSkyboxRenderer#isSkippingMoon()} is true (i.e. we are rendering
     * the moon dimension sky), redirect the {@code renderMoon} call inside
     * {@code renderSunMoonAndStars} to a no-op so the vanilla moon disc is not drawn.
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
        if (!MoonSkyboxRenderer.isSkippingMoon()) {
            // Not in moon-sky mode — invoke the original private method via the helper
            MoonSkyboxRenderer.invokeMoonRender(instance, moonPhase, rainBrightness, poseStack);
        }
        // else: moon rendering is suppressed for the moon dimension
    }
}
