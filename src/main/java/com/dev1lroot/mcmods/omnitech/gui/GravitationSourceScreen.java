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
 *   <li>y=6   – title</li>
 *   <li>y=22  – "Outer Radius:" label + value at y=39</li>
 *   <li>y=35  – outer radius −5/−1/+1/+5 buttons</li>
 *   <li>y=52  – "Inner Radius:" label + value at y=69</li>
 *   <li>y=65  – inner radius −5/−1/+1/+5 buttons</li>
 *   <li>y=82  – "Inventory" label (by super)</li>
 * </ul>
 */
public class GravitationSourceScreen extends AbstractContainerScreen<GravitationSourceMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("gravitation_source");

    // Shared button geometry
    private static final int BTN_H        = 12;
    private static final int BTN_W        = 20;
    private static final int BTN_MINUS5_X = 6;
    private static final int BTN_MINUS1_X = 28;
    private static final int DISP_X       = 52;
    private static final int DISP_W       = 72;
    private static final int BTN_PLUS1_X  = 126;
    private static final int BTN_PLUS5_X  = 148;

    // Row Y positions (panel-relative)
    private static final int OUTER_BTN_Y = 35;
    private static final int INNER_BTN_Y = 65;

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

        // Outer radius row
        int oy = this.topPos + OUTER_BTN_Y;
        addRenderableWidget(Button.builder(Component.literal("-5"),
                b -> click(GravitationSourceBlockEntity.BTN_MINUS_5))
                .bounds(this.leftPos + BTN_MINUS5_X, oy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
                b -> click(GravitationSourceBlockEntity.BTN_MINUS_1))
                .bounds(this.leftPos + BTN_MINUS1_X, oy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
                b -> click(GravitationSourceBlockEntity.BTN_PLUS_1))
                .bounds(this.leftPos + BTN_PLUS1_X, oy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+5"),
                b -> click(GravitationSourceBlockEntity.BTN_PLUS_5))
                .bounds(this.leftPos + BTN_PLUS5_X, oy, BTN_W, BTN_H).build());

        // Inner radius row
        int iy = this.topPos + INNER_BTN_Y;
        addRenderableWidget(Button.builder(Component.literal("-5"),
                b -> click(GravitationSourceBlockEntity.BTN_INNER_MINUS_5))
                .bounds(this.leftPos + BTN_MINUS5_X, iy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
                b -> click(GravitationSourceBlockEntity.BTN_INNER_MINUS_1))
                .bounds(this.leftPos + BTN_MINUS1_X, iy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
                b -> click(GravitationSourceBlockEntity.BTN_INNER_PLUS_1))
                .bounds(this.leftPos + BTN_PLUS1_X, iy, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+5"),
                b -> click(GravitationSourceBlockEntity.BTN_INNER_PLUS_5))
                .bounds(this.leftPos + BTN_PLUS5_X, iy, BTN_W, BTN_H).build());
    }

    private void click(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
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

        // Outer radius
        graphics.text(this.font, "Outer Radius:", 6, 22, 0xFFAAAAAA, false);
        String outerStr = menu.getOuterRadius() + " blocks";
        graphics.text(this.font, outerStr, DISP_X + (DISP_W - this.font.width(outerStr)) / 2, 39,
                0xFF44FFDD, false);

        // Inner radius
        graphics.text(this.font, "Inner Radius:", 6, 52, 0xFFAAAAAA, false);
        String innerStr = menu.getInnerRadius() + " blocks";
        graphics.text(this.font, innerStr, DISP_X + (DISP_W - this.font.width(innerStr)) / 2, 69,
                0xFF44FFDD, false);
    }
}
