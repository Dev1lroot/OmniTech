/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PcbBurnerScreen extends AbstractContainerScreen<PcbBurnerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("pcb_burner");
    private GuiDataContext dataCtx;

    public PcbBurnerScreen(PcbBurnerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (LAYOUT.width - font.width(title)) / 2;
        inventoryLabelY = LAYOUT.inventory.label_y;
        dataCtx = new GuiDataContext()
                .value("progress",      menu::getProgressScaled)
                .value("energy_stored", menu::getEnergyStored)
                .value("energy_max",    menu::getMaxEu)
                .value("eu_per_cycle",  menu::getEuPerBoard)
                .value("voltage",       menu::getVoltage)
                .value("power_in",      menu::getInputWatts)
                .value("power_load",    menu::getLoadWatts)
                .value("rated_power",   menu::getRatedWatts);
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
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null) GuiLayoutRenderer.setElectricTooltip(g, font, LAYOUT, dataCtx, mouseX, mouseY, leftPos, topPos, LAYOUT.width);
    }
}
