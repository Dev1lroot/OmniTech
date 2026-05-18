/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ExpansionSlotScreen extends AbstractContainerScreen<ExpansionSlotMenu> {

    private static final int W = 176;
    private static final int H = 166;

    public ExpansionSlotScreen(ExpansionSlotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        // Draw slot frames for 8 RAM card slots (4 × 2 grid)
        for (int i = 0; i < ExpansionSlotBlockEntity.SLOT_COUNT; i++) {
            int sx = this.leftPos + 8  + (i % 4) * 18;
            int sy = this.topPos  + 18 + (i / 4) * 18;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF000000);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }

        // Player inventory area
        g.fill(this.leftPos + 4, this.topPos + 80, this.leftPos + 172, this.topPos + 162, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
        g.text(this.font, Component.literal("RAM Cards (max 8):"), 8, 8, 0xFF404040, false);
        g.text(this.font, Component.translatable("container.inventory"), 8, 72, 0xFF404040, false);
    }
}
