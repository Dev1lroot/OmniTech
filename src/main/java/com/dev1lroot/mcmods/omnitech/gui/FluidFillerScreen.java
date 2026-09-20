/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidFillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.network.FluidFillerInjectPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class FluidFillerScreen extends AbstractContainerScreen<FluidFillerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("fluid_filler");

    private static final int STEP = 10;

    private GuiDataContext dataCtx;

    private Button minusButton;
    private Button plusButton;
    private Button injectButton;
    private EditBox amountBox;
    private int pendingAmount = STEP;

    public FluidFillerScreen(FluidFillerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input_fluid",  menu::getInputFluid,
                        menu::getInputFluidAmount,  menu::getInputFluidCapacity)
                .fluid("output_fluid", menu::getOutputFluid,
                        menu::getOutputFluidAmount, menu::getOutputFluidCapacity)
                .value("cook_progress", menu::getProgressScaled);

        int row = this.topPos + 54;

        minusButton = addRenderableWidget(Button.builder(Component.literal("-"),
                b -> adjustAmount(-STEP)).bounds(this.leftPos + 6, row, 14, 14).build());

        amountBox = new EditBox(this.font, this.leftPos + 22, row + 1, 36, 12,
                Component.literal("Amount"));
        amountBox.setMaxLength(4);
        amountBox.setValue(Integer.toString(pendingAmount));
        amountBox.setResponder(this::onAmountTyped);
        addRenderableWidget(amountBox);

        plusButton = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> adjustAmount(STEP)).bounds(this.leftPos + 60, row, 14, 14).build());

        injectButton = addRenderableWidget(Button.builder(Component.literal("Inject"),
                b -> doInject()).bounds(this.leftPos + 78, row, 50, 14).build());

        updateFlaskWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateFlaskWidgets();
    }

    private void updateFlaskWidgets() {
        boolean flaskPresent =
                menu.getSlot(FluidFillerBlockEntity.SLOT_INPUT_CANISTER).getItem().getItem()
                        instanceof FlaskItem;
        minusButton.visible  = flaskPresent;
        plusButton.visible   = flaskPresent;
        injectButton.visible = flaskPresent;
        amountBox.setVisible(flaskPresent);
    }

    private void adjustAmount(int delta) {
        pendingAmount = Math.clamp(pendingAmount + delta, 1, FlaskItem.CAPACITY);
        amountBox.setValue(Integer.toString(pendingAmount));
    }

    private void onAmountTyped(String text) {
        try {
            pendingAmount = Math.clamp(Integer.parseInt(text.trim()), 1, FlaskItem.CAPACITY);
        } catch (NumberFormatException ignored) {
            // leave pendingAmount as-is while the field holds invalid/partial text
        }
    }

    private void doInject() {
        ItemStack flask = menu.getSlot(FluidFillerBlockEntity.SLOT_INPUT_CANISTER).getItem();
        if (!(flask.getItem() instanceof FlaskItem)) return;
        ClientPacketDistributor.sendToServer(
                new FluidFillerInjectPacket(menu.getBlockPos(), pendingAmount));
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
