/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Optional;

public class ElectricEngineScreen extends AbstractContainerScreen<ElectricEngineMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("electric_engine");
    private static final GuiDataContext DATA_CTX = new GuiDataContext();

    private static final int BTN_W = 90;
    private static final int BTN_H = 14;

    private Button modeButton;

    /** Electrical readings for the tooltip — generator (forward) and motor (reverse). */
    private GuiDataContext forwardCtx;
    private GuiDataContext reverseCtx;

    public ElectricEngineScreen(ElectricEngineMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.forwardCtx = new GuiDataContext()
                .value("voltage",   menu::getVoltage)
                .value("power_out", menu::getTerminalWatts)
                .value("power_peak", () -> (float) ElectricUnits.toWatts(
                        ElectricEngineBlockEntity.EU_OUTPUT));
        this.reverseCtx = new GuiDataContext()
                .value("energy_stored", menu::getEuBuffer)
                .value("energy_max",    () -> ElectricEngineBlockEntity.MAX_EU_BUFFER)
                .value("voltage",       menu::getVoltage)
                .value("power_in",      menu::getTerminalWatts)
                .value("rated_power",   () -> (float) ElectricUnits.toWatts(
                        ElectricEngineBlockEntity.EU_CONSUME));

        int bx = this.leftPos + (LAYOUT.width - BTN_W) / 2;
        int by = this.topPos + 17;

        modeButton = this.addRenderableWidget(
                Button.builder(modeLabel(), b ->
                        Minecraft.getInstance().gameMode
                                .handleInventoryButtonClick(menu.containerId, 0))
                        .bounds(bx, by, BTN_W, BTN_H)
                        .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, DATA_CTX,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        boolean powered = menu.isPowered();
        boolean reverse = menu.isReverse();

        if (reverse) {
            float kfOut = menu.getKfOutput();

            // Motor: electrical input
            String inputLabel = GuiLayoutRenderer.powerLineText("gui.omnitech.electric.input",
                    menu.getVoltage(), menu.getTerminalWatts());
            graphics.text(this.font, inputLabel, 8, 36,
                    menu.getTerminalWatts() > 0f ? 0xFF44AAFF : 0xFF888888, false);

            String outputLabel = powered
                    ? String.format("KF Output: %.2f KF/t", kfOut)
                    : "KF Output: 0 KF/t";
            graphics.text(this.font, outputLabel, 8, 48, powered ? 0xFF55FF55 : 0xFF888888, false);

        } else {
            String inputLabel = powered
                    ? String.format("KF Input:  %.2f KF/t", menu.getKfReceived())
                    : "KF Input:  Idle";
            graphics.text(this.font, inputLabel, 8, 36, powered ? 0xFF55FF55 : 0xFF888888, false);

            // Generator: electrical output
            String outputLabel = GuiLayoutRenderer.powerLineText("gui.omnitech.electric.output",
                    menu.getVoltage(), menu.getTerminalWatts());
            graphics.text(this.font, outputLabel, 8, 48, powered ? 0xFF44AAFF : 0xFF888888, false);
        }

        String status = powered ? "[ RUNNING ]" : "[  IDLE  ]";
        int stColor   = powered ? 0xFFFFFF44 : 0xFF666666;
        graphics.text(this.font, status,
                (LAYOUT.width - this.font.width(status)) / 2, 60, stColor, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int rx = mouseX - this.leftPos;
        int ry = mouseY - this.topPos;
        // Electrical line: y=36 in reverse mode, y=48 in forward mode
        int lineY = menu.isReverse() ? 36 : 48;
        if (hoveredSlot == null && ry >= lineY - 1 && ry < lineY + 9
                && rx >= 0 && rx < LAYOUT.width) {
            GuiDataContext ctx = menu.isReverse() ? reverseCtx : forwardCtx;
            graphics.setTooltipForNextFrame(this.font,
                    GuiLayoutRenderer.buildElectricTooltip(ctx), Optional.empty(),
                    mouseX, mouseY);
        }
    }

    private Component modeLabel() {
        return menu.isReverse()
                ? Component.translatable("gui.omnitech.electric_engine.mode_reverse")
                : Component.translatable("gui.omnitech.electric_engine.mode_forward");
    }
}
