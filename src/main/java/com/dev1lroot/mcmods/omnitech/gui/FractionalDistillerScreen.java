/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;

/**
 * GUI screen for the Fractional Distiller multiblock.
 *
 * <p>Layout (176 × 166 base):
 * <ul>
 *   <li>Left side — input fluid tank.</li>
 *   <li>Centre — temperature readout + process bar.</li>
 *   <li>Right column — one output tank per structure segment (up to 4),
 *       stacked vertically.</li>
 * </ul>
 * Fluid details are shown as hover tooltips via {@link GuiUtil#buildFluidTooltip}.
 */
public class FractionalDistillerScreen extends AbstractContainerScreen<FractionalDistillerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Input tank (left side)
    private static final int IN_X = 8,  IN_Y = 17, IN_W = 16, IN_H = 52;

    // Output tanks column (right side) — one per structure segment
    private static final int OUT_X   = 152;
    private static final int OUT_Y0  = 10;
    private static final int OUT_W   = 16;
    private static final int OUT_GAP = 2;

    // Process bar (centre bottom)
    private static final int PROC_X = 52, PROC_Y = 50, PROC_W = 72;

    public FractionalDistillerScreen(FractionalDistillerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float pt) {
        super.extractBackground(graphics, mouseX, mouseY, pt);
        int x = this.leftPos, y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Input tank
        GuiUtil.renderFrame(graphics, x + IN_X, y + IN_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid(), menu.getInputFluidAmount(),
                FractionalDistillerBlockEntity.INPUT_TANK_CAPACITY,
                x + IN_X, y + IN_Y, IN_W, IN_H);

        // Output tanks — one per segment
        int height = Math.max(1, Math.min(menu.getStructureHeight(),
                FractionalDistillerBlockEntity.MAX_HEIGHT));
        int slotH  = computeSlotHeight(height);

        for (int i = 0; i < height; i++) {
            int tY = y + OUT_Y0 + i * (slotH + OUT_GAP);
            FluidStack fluid = menu.getOutputFluid(i);
            GuiUtil.renderFrame(graphics, x + OUT_X, tY, OUT_W, slotH);
            GuiUtil.renderFluidBar(graphics, fluid, fluid.getAmount(),
                    FractionalDistillerBlockEntity.OUTPUT_TANK_CAPACITY,
                    x + OUT_X, tY, OUT_W, slotH);
        }

        // Process bar
        GuiUtil.renderProgressBar(graphics, x + PROC_X, y + PROC_Y, PROC_W,
                menu.getProcessProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Temperature readout
        float temp    = menu.getTemperature();
        int   reqTemp = menu.getRequiredTemp();

        String tempStr = String.format("%.1f °C", temp);
        int textW = font.width(tempStr);
        new HudWriter(graphics, font, (imageWidth - textW) / 2, 22, 10, false)
                .setColor(tempColor(temp)).write(tempStr);

        if (reqTemp != 0) {
            boolean met = reqTemp >= 0 ? temp >= reqTemp : temp <= reqTemp;
            String reqStr = (reqTemp >= 0 ? "need ≥ " : "need ≤ ") + reqTemp + " °C";
            int reqW = font.width(reqStr);
            new HudWriter(graphics, font, (imageWidth - reqW) / 2, 32, 10, false)
                    .setColor(met ? 0xFF44AA44 : 0xFFFF2200).write(reqStr);
        }

        // Process status
        HudWriter procW = new HudWriter(graphics, font, PROC_X, PROC_Y + 10, 10, false);
        if (menu.getProcessTotalTime() > 0 && menu.getStructureHeight() > 0) {
            float pct = menu.getProcessProgressScaled();
            if (pct > 0) {
                procW.setColor(0xFF44AA44).write(String.format("%.0f%%", pct))
                     .setColor(0xFF606060).write(" processing");
            } else {
                procW.setColor(0xFF888888).write("waiting...");
            }
        } else {
            procW.setColor(0xFF888888).write("no recipe");
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null) return;

        int x = this.leftPos, y = this.topPos;

        // Input tank
        if (mouseX >= x + IN_X && mouseX < x + IN_X + IN_W
                && mouseY >= y + IN_Y && mouseY < y + IN_Y + IN_H) {
            List<Component> lines = GuiUtil.buildFluidTooltip(
                    menu.getInputFluid(),
                    menu.getInputFluidAmount(),
                    FractionalDistillerBlockEntity.INPUT_TANK_CAPACITY);
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }

        // Output tanks
        int height = Math.max(1, Math.min(menu.getStructureHeight(),
                FractionalDistillerBlockEntity.MAX_HEIGHT));
        int slotH  = computeSlotHeight(height);

        for (int i = 0; i < height; i++) {
            int tY = y + OUT_Y0 + i * (slotH + OUT_GAP);
            if (mouseX >= x + OUT_X && mouseX < x + OUT_X + OUT_W
                    && mouseY >= tY && mouseY < tY + slotH) {
                FluidStack fluid = menu.getOutputFluid(i);
                List<Component> lines = GuiUtil.buildFluidTooltip(
                        fluid, fluid.getAmount(),
                        FractionalDistillerBlockEntity.OUTPUT_TANK_CAPACITY);
                g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private static int computeSlotHeight(int count) {
        int totalGap = (count - 1) * OUT_GAP;
        return Math.max(8, (60 - totalGap) / count);
    }

    private static int tempColor(float t) {
        if (t <= 0f)   return 0xFF44AAFF;
        if (t <= 50f)  return 0xFF888888;
        if (t <= 200f) return 0xFFFFAA00;
        return 0xFFFF2200;
    }
}
