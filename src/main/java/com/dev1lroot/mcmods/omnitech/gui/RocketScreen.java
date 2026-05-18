/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RocketScreen extends AbstractContainerScreen<RocketMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("rocket");
    private GuiDataContext dataCtx;

    // Slot positions from rocket.json: fuel_in at (62,35), bucket_out at (98,35)
    private static final int FUEL_IN_X = 62, FUEL_IN_Y = 35;
    private static final int BUCKET_OUT_X = 98, BUCKET_OUT_Y = 35;

    public RocketScreen(RocketMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        int maxFuel = RocketEntity.MAX_FUEL;
        this.dataCtx = new GuiDataContext()
                .value("fuel_progress", () -> maxFuel > 0 ? menu.getFuelAmount() * 100f / maxFuel : 0f);
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

        new HudWriter(graphics, this.font, FUEL_IN_X - 1, FUEL_IN_Y - 10, 10, false)
                .setColor(0xFF888888).write("Fuel In");

        new HudWriter(graphics, this.font, BUCKET_OUT_X - 1, BUCKET_OUT_Y - 10, 10, false)
                .setColor(0xFF888888).write("Bucket");

        int fuel    = menu.getFuelAmount();
        int maxFuel = RocketEntity.MAX_FUEL;
        new HudWriter(graphics, this.font, 8, 46, 10, false)
                .setColor(0xFF4488FF).write(fuel + "")
                .setColor(0xFF404040).write(" / " + maxFuel + " mB");
    }
}
