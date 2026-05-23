/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
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
 * Screen for the Gravitation Source.
 *
 * <p>Layout (panel-relative y):
 * <ul>
 *   <li>y=6  – title (by super)</li>
 *   <li>y=22 – "Radius:" label</li>
 *   <li>y=35 – four −5/−1/+1/+5 buttons with current radius displayed between them</li>
 *   <li>y=52 – "Inventory" label (by super)</li>
 * </ul>
 */
public class GravitationSourceScreen extends AbstractContainerScreen<GravitationSourceMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("gravitation_source");

    // Panel-relative button positions
    private static final int BTN_Y        = 35;
    private static final int BTN_H        = 12;
    private static final int BTN_W        = 20;
    private static final int BTN_MINUS5_X = 6;
    private static final int BTN_MINUS1_X = 28;
    private static final int DISP_X       = 52;
    private static final int DISP_W       = 72;
    private static final int BTN_PLUS1_X  = 126;
    private static final int BTN_PLUS5_X  = 148;

    private final GuiDataContext dataCtx = new GuiDataContext();

    public GravitationSourceScreen(GravitationSourceMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        int by = this.topPos + BTN_Y;

        addRenderableWidget(Button.builder(
                Component.literal("-5"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, GravitationSourceBlockEntity.BTN_MINUS_5))
                .bounds(this.leftPos + BTN_MINUS5_X, by, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("-1"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, GravitationSourceBlockEntity.BTN_MINUS_1))
                .bounds(this.leftPos + BTN_MINUS1_X, by, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("+1"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, GravitationSourceBlockEntity.BTN_PLUS_1))
                .bounds(this.leftPos + BTN_PLUS1_X, by, BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(
                Component.literal("+5"),
                b -> Minecraft.getInstance().gameMode
                        .handleInventoryButtonClick(menu.containerId, GravitationSourceBlockEntity.BTN_PLUS_5))
                .bounds(this.leftPos + BTN_PLUS5_X, by, BTN_W, BTN_H).build());
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

        graphics.text(this.font, "Radius:", 6, 22, 0xFFAAAAAA, false);

        String radiusStr = menu.getRadius() + " blocks";
        int rw = this.font.width(radiusStr);
        int rx = DISP_X + (DISP_W - rw) / 2;
        graphics.text(this.font, radiusStr, rx, 39, 0xFF44FFDD, false);
    }
}
