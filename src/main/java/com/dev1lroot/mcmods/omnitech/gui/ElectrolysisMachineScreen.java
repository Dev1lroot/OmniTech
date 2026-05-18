/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ElectrolysisMachineScreen extends AbstractContainerScreen<ElectrolysisMachineMenu>
{
    /**
     * Loaded statically so dimensions are available before {@code super()} is
     * called.  Static fields initialise when the class is first loaded —
     * always before any constructor runs.
     */
    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("electrolysis_machine");

    private GuiDataContext dataCtx;

    public ElectrolysisMachineScreen(ElectrolysisMachineMenu menu, Inventory playerInventory, Component title)
    {
        // Pass JSON dimensions so leftPos/topPos are centred on the actual panel.
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();

        this.titleLabelX    = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input",
                        menu::getInputFluid,
                        menu::getInputFluidAmount,
                        menu::getInputFluidCapacity)
                .fluid("anode_output",
                        menu::getAnodeFluid,
                        menu::getAnodeFluidAmount,
                        menu::getAnodeFluidCapacity)
                .fluid("cathode_output",
                        menu::getCathodeFluid,
                        menu::getCathodeFluidAmount,
                        menu::getCathodeFluidCapacity)
                .fluid("solution_output",
                        menu::getSolutionFluid,
                        menu::getSolutionFluidAmount,
                        menu::getSolutionFluidCapacity)
                .value("cook_progress", menu::getCookProgressScaled)
                .value("energy_stored", menu::getEnergyStored)
                .value("energy_max",    menu::getMaxEu)
                .value("eu_per_cycle",  menu::getEuPerRecipe);
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
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Show fluid tooltip only when no item slot tooltip is already queued.
        if (hoveredSlot == null) {
            GuiLayoutRenderer.setFluidTooltip(graphics, this.font, LAYOUT, dataCtx,
                    mouseX, mouseY, this.leftPos, this.topPos);
        }
    }
}
