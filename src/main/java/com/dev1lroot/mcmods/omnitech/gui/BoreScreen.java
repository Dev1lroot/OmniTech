/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.items.BatteryItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class BoreScreen extends AbstractContainerScreen<BoreMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load(BoreMenu.LAYOUT_ID);
    private static final int LABEL_COLOR = 0xFF404040;

    private GuiDataContext dataCtx;

    public BoreScreen(BoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .value("energy_stored",   () -> (float) batteryEnergy())
                .value("energy_max",      () -> (float) batteryCapacity())
                .value("battery_charge",  () -> batteryCapacity() > 0
                        ? batteryEnergy() * 100f / batteryCapacity() : 0f)
                .value("head_durability", () -> {
                    ItemStack head = menu.getHead();
                    return head.isEmpty() || head.getMaxDamage() <= 0 ? 0f
                            : (head.getMaxDamage() - head.getDamageValue()) * 100f / head.getMaxDamage();
                });
    }

    private int batteryEnergy() {
        ItemStack bat = menu.getBattery();
        return bat.getItem() instanceof BatteryItem b ? b.getEnergy(bat) : 0;
    }

    private int batteryCapacity() {
        ItemStack bat = menu.getBattery();
        return bat.getItem() instanceof BatteryItem b ? b.getCapacity(bat) : 0;
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
        Component head = Component.translatable("gui.omnitech.bore.head");
        Component battery = Component.translatable("gui.omnitech.bore.battery");
        graphics.text(this.font, head, 40 - this.font.width(head), 30, LABEL_COLOR, false);
        graphics.text(this.font, battery, 136, 30, LABEL_COLOR, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null) {
            GuiLayoutRenderer.setElectricTooltip(graphics, this.font, LAYOUT, dataCtx,
                    mouseX, mouseY, this.leftPos, this.topPos, LAYOUT.width);
        }
    }
}
