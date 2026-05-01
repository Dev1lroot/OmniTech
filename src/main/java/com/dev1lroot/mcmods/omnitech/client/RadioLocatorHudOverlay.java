package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.items.RadioLocatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class RadioLocatorHudOverlay {

    private static volatile float lastSignal = 0f;

    private static final int PANEL_X  = 4;
    private static final int LINE_H   = 10;
    private static final int BAR_W    = 80;
    private static final int BAR_H    = 5;

    private RadioLocatorHudOverlay() {}

    public static void setLastSignal(float signal) {
        lastSignal = signal;
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        ItemStack held = getHeldLocator(player);
        if (held == null) return;

        int globalKey = held.getOrDefault(OmniTechDataComponents.RADIO_LOCATOR_FREQ.get(),
                FrequencyBand.VHF.globalKey(0));
        FrequencyBand band = FrequencyBand.fromGlobalKey(globalKey);
        int ch = FrequencyBand.channelOf(globalKey);
        float signal = lastSignal;

        Font font = mc.font;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Anchor above the hotbar — same region as SpaceSuitHudOverlay but on right side
        int panelY = screenH - 22 - LINE_H * 2 - BAR_H - 10;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int panelX = screenW - BAR_W - 6 - 2;

        String freqStr = band.displayName() + ": " + band.freqDisplay(ch);
        String sigStr    = String.format("Sig: %.1f", signal);
        int labelW = Math.max(font.width(freqStr), font.width(sigStr));
        int panelW = Math.max(labelW, BAR_W) + 4;

        // Background
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2,
                panelY + LINE_H * 2 + BAR_H + 4, 0x88010108);

        // Frequency line
        g.text(font, freqStr, panelX, panelY, 0xFF00AAFF);

        // Signal value
        int sigColor = signalColor(signal);
        g.text(font, sigStr, panelX, panelY + LINE_H, sigColor);

        // Signal bar
        int barY = panelY + LINE_H * 2;
        g.fill(panelX, barY, panelX + BAR_W, barY + BAR_H, 0xFF333333);
        int filled = (int) Math.round(BAR_W * Math.clamp(signal / 15f, 0f, 1f));
        if (filled > 0) {
            g.fill(panelX, barY, panelX + filled, barY + BAR_H, sigColor);
        }
    }

    private static ItemStack getHeldLocator(Player player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof RadioLocatorItem) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof RadioLocatorItem) return off;
        return null;
    }

    private static int signalColor(float sig) {
        if (sig >= 12) return 0xFFFFFFFF;
        if (sig >= 8)  return 0xFFFFCC00;
        if (sig >= 4)  return 0xFF44CC44;
        if (sig > 0)   return 0xFF44AA44;
        return 0xFF888888;
    }
}
