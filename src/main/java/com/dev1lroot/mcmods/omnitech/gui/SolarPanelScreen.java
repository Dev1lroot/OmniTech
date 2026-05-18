/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * GUI for the Solar Panel.
 *
 * <p>Displays:
 * <ul>
 *   <li>A yellow sun-intensity bar scaled to the current sky light level (0–15).</li>
 *   <li>A blue output bar scaled to the current EU/tick output (0–1 EU/t).</li>
 *   <li>Numeric labels for both values.</li>
 * </ul>
 *
 * <p>Layout (within the 176×166 GUI background):
 * <pre>
 *  y=6   Title: "Solar Panel"
 *  y=20  Label: "Sun Intensity:"
 *  y=28  [====yellow bar====] (background gray, fill yellow)
 *  y=45  Label: "Output:"
 *  y=53  [====blue bar====]   (background gray, fill blue)
 *  y=68  "Inventory"
 *  y=84  Player inventory slots
 * </pre>
 */
public class SolarPanelScreen extends AbstractContainerScreen<SolarPanelMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Bar geometry (relative to GUI origin)
    private static final int BAR_X  = 38;   // left edge of bars
    private static final int BAR_H  = 8;    // bar height (px)
    private static final int BAR1_Y = 28;   // sky-light bar top
    private static final int BAR2_Y = 53;   // output bar top

    // Colors
    private static final int COLOR_BAR_BG     = 0xFF333333;
    private static final int COLOR_SKYLIGHT    = 0xFFFFDD00; // yellow
    private static final int COLOR_OUTPUT      = 0xFF44AAFF; // blue

    public SolarPanelScreen(SolarPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 72;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;

        // Standard container background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // ── Sky-light bar ─────────────────────────────────────────────────────
        int barMaxW = SolarPanelMenu.BAR_MAX_W;
        // background
        graphics.fill(x + BAR_X, y + BAR1_Y,
                x + BAR_X + barMaxW, y + BAR1_Y + BAR_H,
                COLOR_BAR_BG);
        // fill
        int skyW = menu.getSkyLightBarWidth();
        if (skyW > 0) {
            graphics.fill(x + BAR_X, y + BAR1_Y,
                    x + BAR_X + skyW, y + BAR1_Y + BAR_H,
                    COLOR_SKYLIGHT);
        }

        // ── Output bar ────────────────────────────────────────────────────────
        // background
        graphics.fill(x + BAR_X, y + BAR2_Y,
                x + BAR_X + barMaxW, y + BAR2_Y + BAR_H,
                COLOR_BAR_BG);
        // fill
        int outW = menu.getOutputBarWidth();
        if (outW > 0) {
            graphics.fill(x + BAR_X, y + BAR2_Y,
                    x + BAR_X + outW, y + BAR2_Y + BAR_H,
                    COLOR_OUTPUT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // "Sun Intensity:" label + value
        graphics.text(this.font,
                Component.translatable("gui.omnitech.solar_panel.sun_intensity"),
                BAR_X, 20, 0xFFFFDD00, false);
        graphics.text(this.font,
                menu.getSkyLight() + " / 15",
                BAR_X + SolarPanelMenu.BAR_MAX_W + 4, 28, 0xFFFFDD00, false);

        // "Output:" label + value
        graphics.text(this.font,
                Component.translatable("gui.omnitech.solar_panel.output"),
                BAR_X, 45, 0xFF44AAFF, false);
        String outputStr = String.format("%.3f EU/t", menu.getCurrentOutput());
        graphics.text(this.font,
                outputStr,
                BAR_X + SolarPanelMenu.BAR_MAX_W + 4, 53, 0xFF44AAFF, false);
    }
}
