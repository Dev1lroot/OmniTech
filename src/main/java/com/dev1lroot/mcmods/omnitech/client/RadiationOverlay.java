package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechMobEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Full-screen noisy static overlay for Radiation III (amplifier 2).
 * Rendered as a fluctuating semi-transparent green-tinted vignette.
 */
public class RadiationOverlay implements GuiLayer {

    private static final int[] DOTS_PER_TIER = { 30, 100, 250 };

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        MobEffectInstance effect = mc.player.getEffect(OmniTechMobEffects.RADIATION);
        if (effect == null) return;

        int amplifier = Math.min(effect.getAmplifier(), 2);
        int dotCount  = DOTS_PER_TIER[amplifier];

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        long seed = System.currentTimeMillis() / 50; // refreshes at ~20fps
        for (int i = 0; i < dotCount; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int nx = (int) ((seed >>> 48) % w);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int ny = (int) ((seed >>> 48) % h);
            int na = 80 + (int) ((seed >>> 40) & 0x7F); // 80..207 alpha
            g.fill(nx, ny, nx + 2, ny + 2, (na << 24) | 0xFFFFFF);
        }
    }
}
