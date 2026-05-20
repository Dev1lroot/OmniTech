/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.network.SetMachineValuePacket;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class RotaryCompressorScreen extends AbstractContainerScreen<RotaryCompressorMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("rotary_compressor");
    private static final int MIN_P = RotaryCompressorBlockEntity.MIN_PRESSURE;
    private static final int MAX_P = RotaryCompressorBlockEntity.MAX_PRESSURE;

    private GuiDataContext dataCtx;
    private PressureSlider pressureSlider;

    public RotaryCompressorScreen(RotaryCompressorMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input",  menu::getInputFluid,
                        menu::getInputFluidAmount,  menu::getInputFluidCapacity)
                .fluid("output", menu::getOutputFluid,
                        menu::getOutputFluidAmount, menu::getOutputFluidCapacity)
                .value("kf_progress", menu::getKfProgressScaled);

        pressureSlider = new PressureSlider(leftPos + 30, topPos + 62, 116, 14);
        addRenderableWidget(pressureSlider);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pressureSlider != null) pressureSlider.syncFromServer();
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

        // Status line
        HudWriter procWriter = new HudWriter(graphics, this.font, 52, 47, 10, false);
        float kfPct = menu.getKfProgressScaled();
        if (menu.getProcessCooldown() > 0) {
            procWriter.setColor(0xFF44AAFF).write("compressing");
        } else if (kfPct > 0f) {
            procWriter.setColor(0xFFFFAA00)
                    .write(String.format("%.0f%%", kfPct))
                    .setColor(0xFF606060).write(" charged");
        } else {
            procWriter.setColor(0xFF888888).write("idle");
        }

        // KF line
        float kfCurrent  = menu.getKineticForce();
        float kfRequired = menu.getKineticForceRequired();
        HudWriter kfWriter = new HudWriter(graphics, this.font, 52, 57, 10, false);
        kfWriter.setColor(0xFF888888).write("KF ");
        if (kfRequired <= 0f) {
            kfWriter.setColor(0xFF888888).write("N/A");
        } else {
            int kfColor = kfCurrent >= kfRequired ? 0xFF44FF44 : kfCurrent > 0f ? 0xFFFFAA00 : 0xFFFF5555;
            kfWriter.setColor(kfColor).write(String.format("%.2f", kfCurrent))
                    .setColor(0xFF888888).write(" / ")
                    .setColor(0xFFAAAAAA).write(String.format("%.2f", kfRequired));
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

    // ── Slider ────────────────────────────────────────────────────────────────

    private class PressureSlider extends AbstractSliderButton {
        PressureSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), toSliderValue(menu.getTargetPressure()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(toKPa() + " kPa"));
        }

        @Override
        protected void applyValue() {
            ClientPacketDistributor.sendToServer(
                    new SetMachineValuePacket(menu.getBlockPos(), "targetPressure", toKPa()));
        }

        void syncFromServer() {
            if (!isDragging()) {
                int serverKPa = menu.getTargetPressure();
                if (toKPa() != serverKPa) {
                    value = toSliderValue(serverKPa);
                    updateMessage();
                }
            }
        }

        private int toKPa() { return MIN_P + (int)(value * (MAX_P - MIN_P)); }
        private static double toSliderValue(int kPa) { return (double)(kPa - MIN_P) / (MAX_P - MIN_P); }
    }
}
