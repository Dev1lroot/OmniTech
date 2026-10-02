/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.DisplayUnits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PcbWasherScreen extends AbstractContainerScreen<PcbWasherMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("pcb_washer");
    private GuiDataContext dataCtx;

    public PcbWasherScreen(PcbWasherMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        FlushButton.addAll(LAYOUT, leftPos, topPos, menu.containerId, this::addRenderableWidget);
        titleLabelX = (LAYOUT.width - font.width(title)) / 2;
        inventoryLabelY = LAYOUT.inventory.label_y;
        dataCtx = new GuiDataContext()
                .fluid("acid", menu::getAcid, menu::getAcidAmount, menu::getAcidCapacity)
                .value("progress", menu::getProgressScaled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);
        GuiLayoutRenderer.renderBackground(g, LAYOUT, dataCtx, leftPos, topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        GuiLayoutRenderer.renderLabels(g, font, LAYOUT, dataCtx, LAYOUT.width);
        // acid per board depends on the board's area
        if (menu.getAcidPerBoard() > 0) {
            Component cost = Component.translatable("gui.omnitech.pcb.fab_cost", DisplayUnits.volume(menu.getAcidPerBoard()));
            g.text(font, cost, (LAYOUT.width - font.width(cost)) / 2, 66, 0xFF606060, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null) GuiLayoutRenderer.setFluidTooltip(g, font, LAYOUT, dataCtx, mouseX, mouseY, leftPos, topPos);
    }
}
