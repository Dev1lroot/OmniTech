/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater.ElectricHeaterBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Electric Heater.
 *
 * <p>Layout (panel-relative y):
 * <ul>
 *   <li>y=6  – title "Electric Heater" (by super)</li>
 *   <li>y=22 – "Target:" label</li>
 *   <li>y=35 – six +/− buttons with target temperature display between them</li>
 *   <li>y=51 – "Current: X °C"</li>
 *   <li>y=61 – "EU/tick: X EU/t    Buffer: Y/Z EU"</li>
 *   <li>y=72 – "Inventory" label (by super)</li>
 * </ul>
 */
public class ElectricHeaterScreen extends AbstractContainerScreen<ElectricHeaterMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("electric_heater");

    // Button positions (panel-relative x, reused in init() with leftPos offset)
    private static final int BTN_Y       = 35;
    private static final int BTN_H       = 12;
    private static final int BTN_MINUS_100_X = 6;
    private static final int BTN_MINUS_10_X  = 24;
    private static final int BTN_MINUS_1_X   = 42;
    private static final int DISP_X          = 60;   // center display start
    private static final int DISP_W          = 58;   // center display width
    private static final int BTN_PLUS_1_X    = 120;
    private static final int BTN_PLUS_10_X   = 138;
    private static final int BTN_PLUS_100_X  = 156;
    private static final int BTN_W           = 16;

    private GuiDataContext dataCtx;

    public ElectricHeaterScreen(ElectricHeaterMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .value("energy_stored", menu::getEnergyStored)
                .value("energy_max",    menu::getMaxEu);

        int by = this.topPos + BTN_Y;

        this.addRenderableWidget(Button.builder(
                Component.literal("-100"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_MINUS_100))
                .bounds(this.leftPos + BTN_MINUS_100_X, by, BTN_W, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("-10"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_MINUS_10))
                .bounds(this.leftPos + BTN_MINUS_10_X, by, BTN_W, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("-1"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_MINUS_1))
                .bounds(this.leftPos + BTN_MINUS_1_X, by, BTN_W, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("+1"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_PLUS_1))
                .bounds(this.leftPos + BTN_PLUS_1_X, by, BTN_W, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("+10"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_PLUS_10))
                .bounds(this.leftPos + BTN_PLUS_10_X, by, BTN_W, BTN_H).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("+100"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, ElectricHeaterBlockEntity.BTN_PLUS_100))
                .bounds(this.leftPos + BTN_PLUS_100_X, by, BTN_W, BTN_H).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        GuiLayoutRenderer.renderLabels(graphics, this.font, LAYOUT, dataCtx, LAYOUT.width);

        // "Target:" header
        graphics.text(this.font, "Target:", 6, 22, 0xFFAAAAAA, false);

        // Target temperature — centered in the display area between the buttons
        String targetStr = formatTemp(menu.getTargetTemp());
        int tw = this.font.width(targetStr);
        int tx = DISP_X + (DISP_W - tw) / 2;
        graphics.text(this.font, targetStr, tx, 39, heatColor(menu.getTargetTemp()), false);

        // Current temperature
        String curStr = "Current: " + formatTemp(menu.getCurrentTemp());
        graphics.text(this.font, curStr, 6, 51, heatColor(menu.getCurrentTemp()), false);

        // EU/tick + buffer
        String euStr = formatEuPerTick(menu.getEuPerTick());
        graphics.text(this.font, euStr, 6, 62, 0xFF44AAFF, false);
        String bufStr = String.format("%.0f/%.0f EU", menu.getEnergyStored(), menu.getMaxEu());
        int bx = LAYOUT.width - 6 - this.font.width(bufStr);
        graphics.text(this.font, bufStr, bx, 62, 0xFF888888, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatTemp(float temp) {
        int t = Math.round(temp);
        return formatTemp(t);
    }

    private static String formatTemp(int temp) {
        if (temp < 1_000)       return temp + " °C";
        if (temp < 1_000_000)   return String.format("%.1fk °C", temp / 1_000f);
        return String.format("%.2fM °C", temp / 1_000_000f);
    }

    private static String formatEuPerTick(float eu) {
        if (eu < 1_000f)       return String.format("%.2f EU/t", eu);
        if (eu < 1_000_000f)   return String.format("%.1fk EU/t", eu / 1_000f);
        return String.format("%.2fM EU/t", eu / 1_000_000f);
    }

    private static int heatColor(float heat) {
        if (heat >= 5_000)  return 0xFFFFFFFF;
        if (heat >= 1_000)  return 0xFFFF4400;
        if (heat >= 500)    return 0xFFFF8800;
        if (heat >= 100)    return 0xFFFFCC00;
        if (heat > 20)      return 0xFFFFEE88;
        return 0xFFAAAAAA;
    }
}
