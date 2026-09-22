/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.labware.ChemicalMixerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Chemical Mixer GUI: tank A, tank B and the output. Each input has one button showing how many mB
 * of it go into every tick's blend; scroll the mouse wheel over it to change the value (±1, or ±5
 * while holding Shift).
 */
public class ChemicalMixerScreen extends AbstractContainerScreen<ChemicalMixerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("chemical_mixer");

    private static final int BTN_X = 50;    // panel-relative
    private static final int BTN_W = 96;
    private static final int BTN_H = 14;

    // Indices into ChemicalMixerBlockEntity.STEPS, i.e. {-5, -1, +1, +5}
    private static final int STEP_MINUS_5 = 0, STEP_MINUS_1 = 1, STEP_PLUS_1 = 2, STEP_PLUS_5 = 3;

    private static final int ROW_A_Y = 26;
    private static final int ROW_B_Y = 48;

    private GuiDataContext dataCtx;
    private Button ratioAButton;
    private Button ratioBButton;

    public ChemicalMixerScreen(ChemicalMixerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input_a", menu::getFluidA, menu::getFluidAAmount, menu::getFluidACapacity)
                .fluid("input_b", menu::getFluidB, menu::getFluidBAmount, menu::getFluidBCapacity)
                .fluid("output",  menu::getOutput, menu::getOutputAmount, menu::getOutputCapacity);

        ratioAButton = addRatioButton(ROW_A_Y);
        ratioBButton = addRatioButton(ROW_B_Y);
        updateButtonLabels();
    }

    /** The button only displays the value; it is changed by scrolling over it. */
    private Button addRatioButton(int y) {
        Button button = Button.builder(Component.empty(), b -> {})
                .bounds(this.leftPos + BTN_X, this.topPos + y, BTN_W, BTN_H).build();
        button.setTooltip(Tooltip.create(Component.literal("Scroll to change (hold Shift for ±5)")));
        return addRenderableWidget(button);
    }

    private void updateButtonLabels() {
        ratioAButton.setMessage(Component.literal(String.valueOf(menu.getRatioA())));
        ratioBButton.setMessage(Component.literal(String.valueOf(menu.getRatioB())));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonLabels();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int firstId = -1;
            if (ratioAButton.isMouseOver(mouseX, mouseY))      firstId = 0;
            else if (ratioBButton.isMouseOver(mouseX, mouseY)) firstId = ChemicalMixerBlockEntity.STEPS.length;
            if (firstId >= 0) {
                boolean big = Minecraft.getInstance().hasShiftDown();
                int step = scrollY > 0 ? (big ? STEP_PLUS_5 : STEP_PLUS_1) : (big ? STEP_MINUS_5 : STEP_MINUS_1);
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, firstId + step);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

        graphics.text(this.font, "Input A (left)",  BTN_X, ROW_A_Y - 10, 0xFFAAAAAA, false);
        graphics.text(this.font, "Input B (right)", BTN_X, ROW_B_Y - 10, 0xFFAAAAAA, false);

        int total = menu.getRatioA() + menu.getRatioB();
        graphics.text(this.font, menu.getRatioA() + " : " + menu.getRatioB() + "  =  +" + total + " mB/tick",
                BTN_X, ROW_B_Y + 16, 0xFF44FFDD, false);
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
