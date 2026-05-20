/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HeatExchangerScreen extends AbstractContainerScreen<HeatExchangerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("heat_exchanger");
    private static final int AMBIENT = 20;

    private GuiDataContext dataCtx;

    public HeatExchangerScreen(HeatExchangerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input",  menu::getInputFluid,  menu::getInputFluidAmount,  menu::getInputFluidCapacity)
                .fluid("output", menu::getOutputFluid, menu::getOutputFluidAmount, menu::getOutputFluidCapacity)
                .value("process_progress", menu::getProcessProgressScaled);
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

        int machineTemp = menu.getMachineTemp();

        int tempColor;
        if (machineTemp == AMBIENT)      tempColor = 0xFF888888;
        else if (machineTemp > AMBIENT)  tempColor = machineTemp > 500 ? 0xFFFF2200 : 0xFFFF8800;
        else                             tempColor = machineTemp < -200 ? 0xFF00CCFF : 0xFF44AAFF;

        String tempStr = machineTemp + " °C";
        int textW = this.font.width(tempStr);
        new HudWriter(graphics, this.font, (LAYOUT.width - textW) / 2, 22, 10, false)
                .setColor(tempColor).write(tempStr);

        HudWriter procWriter = new HudWriter(graphics, this.font, 52, 47, 10, false);
        if (menu.getProcessTimer() > 0) {
            procWriter.setColor(0xFF44AA44)
                    .write(String.format("%.0f%%", menu.getProcessProgressScaled()))
                    .setColor(0xFF606060).write(" processing");
        } else {
            procWriter.setColor(0xFF888888).write("idle");
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null) {
            GuiLayoutRenderer.setFluidTooltip(graphics, this.font, LAYOUT, dataCtx,
                    mouseX, mouseY, this.leftPos, this.topPos);
        }
    }
}
