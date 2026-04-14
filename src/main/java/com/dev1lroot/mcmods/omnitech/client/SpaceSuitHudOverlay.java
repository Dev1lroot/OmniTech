package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.SpaceSuitItem;
import com.dev1lroot.mcmods.omnitech.space.DimensionEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Renders the Space Suit HUD panel in the bottom-left corner of the screen.
 *
 * <p>The overlay is shown whenever the player has a {@link SpaceSuitItem} helmet
 * equipped.  It displays three lines:
 * <ol>
 *   <li>Current planet / dimension name</li>
 *   <li>Atmospheric pressure in Pascals (colour-coded: white = breathable,
 *       yellow = dangerous, red = lethal)</li>
 *   <li>Ambient temperature in °C (colour-coded: cyan = cold, white = normal,
 *       orange = hot)</li>
 * </ol>
 *
 * <p>Register via {@link net.neoforged.common.NeoForge#EVENT_BUS}:
 * <pre>{@code NeoForge.EVENT_BUS.addListener(SpaceSuitHudOverlay::onRenderGui);}</pre>
 */
public final class SpaceSuitHudOverlay {

    // Colour constants
    private static final int C_LABEL    = 0xFFAABBCC;
    private static final int C_DIM      = 0xFF80FFCC;
    private static final int C_PRESS_OK = 0xFFCCDDEE;   // near-Earth pressure
    private static final int C_PRESS_LO = 0xFFFFDD44;   // low pressure warning
    private static final int C_PRESS_VAC= 0xFFEE4444;   // vacuum
    private static final int C_TEMP_COLD= 0xFF66DDFF;   // very cold
    private static final int C_TEMP_OK  = 0xFFCCDDEE;   // comfortable
    private static final int C_TEMP_HOT = 0xFFFF8833;   // hot

    private static final int PANEL_X    = 4;
    private static final int LINE_H     = 10;

    private SpaceSuitHudOverlay() {}

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (!SpaceSuitItem.isWearingHelmet(player)) return;

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        Font font = mc.font;

        double temperature = DimensionEnvironment.getTemperature(level, pos);
        double pressure    = DimensionEnvironment.getPressure(level, pos);
        String dimName     = DimensionEnvironment.getDimensionName(level);

        GuiGraphicsExtractor g    = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Panel anchored 4 px above the hotbar area (hotbar is ~22 px tall)
        int panelY = screenH - 22 - LINE_H * 3 - 6;

        // ── Background pill ───────────────────────────────────────────────────
        int labelW  = font.width("Pressure: ");
        int maxValW = Math.max(
                font.width(formatPressure(pressure)),
                Math.max(font.width(formatTemp(temperature)), font.width(dimName)));
        int panelW  = labelW + maxValW + 8;
        g.fill(PANEL_X - 2, panelY - 2, PANEL_X + panelW + 2, panelY + LINE_H * 3 + 2, 0x88010108);

        // ── Line 1: dimension name ─────────────────────────────────────────────
        g.text(font, "Location: ", PANEL_X, panelY, C_LABEL);
        g.text(font, dimName, PANEL_X + labelW, panelY, C_DIM);

        // ── Line 2: pressure ───────────────────────────────────────────────────
        int pressColor = pressure > 70_000 ? C_PRESS_OK
                       : pressure > 1_000  ? C_PRESS_LO
                       : C_PRESS_VAC;
        g.text(font, "Pressure: ", PANEL_X, panelY + LINE_H, C_LABEL);
        g.text(font, formatPressure(pressure), PANEL_X + labelW, panelY + LINE_H, pressColor);

        // ── Line 3: temperature ────────────────────────────────────────────────
        int tempColor = temperature < -50 ? C_TEMP_COLD
                      : temperature > 60  ? C_TEMP_HOT
                      : C_TEMP_OK;
        g.text(font, "Temp:     ", PANEL_X, panelY + LINE_H * 2, C_LABEL);
        g.text(font, formatTemp(temperature), PANEL_X + labelW, panelY + LINE_H * 2, tempColor);
    }

    // ── Formatters ─────────────────────────────────────────────────────────────

    private static String formatPressure(double pa) {
        if (pa == 0) return "VACUUM";
        if (pa < 10_000)  return String.format("%.0f Pa", pa);
        if (pa < 1_000_000) return String.format("%.1f kPa", pa / 1_000.0);
        return String.format("%.2f MPa", pa / 1_000_000.0);
    }

    private static String formatTemp(double celsius) {
        return String.format("%+.1f \u00B0C", celsius);  // e.g. "+15.0 °C"
    }
}
