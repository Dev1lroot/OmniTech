/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorStructure;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import com.dev1lroot.mcmods.omnitech.network.DepressurizeReactorPacket;
import com.dev1lroot.mcmods.omnitech.network.ScramReactorPacket;
import com.dev1lroot.mcmods.omnitech.network.SetControlRodPacket;
import com.dev1lroot.mcmods.omnitech.network.StartReactorPacket;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {

    public ReactorScreen(ReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.imageWidth, menu.imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY     = menu.gridOffsetY - 11;
        this.inventoryLabelX = menu.invOffsetX;
        this.inventoryLabelY = menu.invOffsetY - 10;

        addRenderableWidget(Button.builder(
                Component.literal("START"),
                btn -> ClientPacketDistributor.sendToServer(new StartReactorPacket(menu.containerId)))
                .bounds(leftPos + menu.scramBtnX, topPos + menu.startBtnY,
                        menu.scramBtnW, ReactorMenu.SCRAM_BTN_H)
                .build());
        addRenderableWidget(Button.builder(
                Component.literal("FLUSH"),
                btn -> ClientPacketDistributor.sendToServer(new DepressurizeReactorPacket(menu.containerId)))
                .bounds(leftPos + menu.scramBtnX, topPos + menu.ventBtnY,
                        menu.scramBtnW, ReactorMenu.SCRAM_BTN_H)
                .build());
        addRenderableWidget(Button.builder(
                Component.literal("SCRAM"),
                btn -> ClientPacketDistributor.sendToServer(new ScramReactorPacket(menu.containerId)))
                .bounds(leftPos + menu.scramBtnX, topPos + menu.scramBtnY,
                        menu.scramBtnW, ReactorMenu.SCRAM_BTN_H)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Panel background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);

        // Full fixed MAX_SIZE grid border
        int gx = leftPos + menu.gridOffsetX;
        int gy = topPos  + menu.gridOffsetY;
        int gw = ReactorStructure.MAX_SIZE * ReactorMenu.SLOT_SIZE;
        int gh = ReactorStructure.MAX_SIZE * ReactorMenu.SLOT_SIZE;
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF999999);
        g.fill(gx,     gy,     gx + gw,     gy + gh,     0xFFC6C6C6);

        // Cell slot backgrounds
        for (int[] lp : menu.cellLocalPositions) {
            int sx = leftPos + menu.gridOffsetX
                    + (lp[0] + menu.cellDisplayOffsetX) * ReactorMenu.SLOT_SIZE + 1;
            int sy = topPos  + menu.gridOffsetY
                    + (lp[1] + menu.cellDisplayOffsetY) * ReactorMenu.SLOT_SIZE + 1;
            GuiUtil.renderSlot(g, sx, sy);
        }

        // Player inventory slot backgrounds
        int iox = leftPos + menu.invOffsetX;
        int ioy = topPos  + menu.invOffsetY;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                GuiUtil.renderSlot(g,
                        iox + col * ReactorMenu.SLOT_SIZE + 1,
                        ioy + row * ReactorMenu.SLOT_SIZE + 1);
        int hotbarY = ioy + 3 * ReactorMenu.SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++)
            GuiUtil.renderSlot(g, iox + col * ReactorMenu.SLOT_SIZE + 1, hotbarY + 1);

        renderTankBar(g);
        renderHeatBar(g);
        renderTempGraph(g);
        renderFlowGraph(g);
    }

    private void renderTankBar(GuiGraphicsExtractor g) {
        int tx = leftPos + menu.tankBarX;
        int ty = topPos  + menu.tankBarY;
        int th = menu.tankBarH;
        int tw = ReactorMenu.TANK_BAR_W;

        GuiUtil.renderFrame(g, tx, ty, tw, th);

        int water    = menu.getWaterBuckets();
        int maxWater = menu.getWaterCapacityBuckets();
        if (maxWater <= 0 || water <= 0) return;

        FluidStack fs = menu.getWaterFluid();
        if (fs.isEmpty()) {
            var fo = OmniTechFluids.get("distilled_water");
            if (fo != null) fs = new FluidStack(fo.source.get(), water * 1000);
        }
        if (!fs.isEmpty()) {
            GuiUtil.renderFluidBar(g, fs, water * 1000, maxWater * 1000, tx, ty, tw, th);
        }
    }

    private void renderHeatBar(GuiGraphicsExtractor g) {
        int tx = leftPos + menu.heatBarX;
        int ty = topPos  + menu.heatBarY;
        int th = menu.heatBarH;
        int tw = ReactorMenu.HEAT_BAR_W;

        GuiUtil.renderFrame(g, tx, ty, tw, th);

        int temp    = menu.getCoreTemperature();
        int maxTemp = ReactorBlockEntity.MAX_TEMPERATURE;
        if (temp <= 0 || maxTemp <= 0) return;

        int filledH = temp * th / maxTemp;
        if (filledH <= 0) return;

        float fraction = (float) temp / maxTemp;
        int green = (int)(0x88 * (1.0f - fraction));
        int color = ARGB.color(0xCC, 0xFF, green, 0x00);

        int startY = ty + th - filledH;
        g.fill(tx, startY, tx + tw, ty + th, color);
    }

    // Temperature mini-graph: dark blue (cold) → orange/red (hot)
    private void renderTempGraph(GuiGraphicsExtractor g) {
        int ox = leftPos + menu.tempGraphX;
        int oy = topPos  + menu.tempGraphY;
        int s  = ReactorMenu.MINI_GRAPH_SIZE;

        // Background
        g.fill(ox - 1, oy - 1, ox + s + 1, oy + s + 1, 0xFF666666);
        g.fill(ox, oy, ox + s, oy + s, 0xFF222222);

        int maxTemp = ReactorBlockEntity.MAX_TEMPERATURE;
        for (int i = 0; i < menu.cellLocalPositions.size(); i++) {
            int[] lp  = menu.cellLocalPositions.get(i);
            int px = ox + (lp[0] + menu.cellDisplayOffsetX) * ReactorMenu.MINI_CELL_PX;
            int py = oy + (lp[1] + menu.cellDisplayOffsetY) * ReactorMenu.MINI_CELL_PX;

            int temp = 0;
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof ReactorRodItem) {
                temp = Math.max(0, ReactorRodItem.getTemperature(stack));
            }

            int color = tempToColor(temp, maxTemp, !stack.isEmpty() && stack.getItem() instanceof ReactorRodItem);
            g.fill(px, py, px + ReactorMenu.MINI_CELL_PX, py + ReactorMenu.MINI_CELL_PX, color);
        }
    }

    // Neutron flow mini-graph: black (0%) → light blue (100%), lime green dot = rod present
    private void renderFlowGraph(GuiGraphicsExtractor g) {
        int ox = leftPos + menu.flowGraphX;
        int oy = topPos  + menu.flowGraphY;
        int s  = ReactorMenu.MINI_GRAPH_SIZE;
        int c  = ReactorMenu.MINI_CELL_PX;

        // Background
        g.fill(ox - 1, oy - 1, ox + s + 1, oy + s + 1, 0xFF666666);
        g.fill(ox, oy, ox + s, oy + s, 0xFF000000);

        for (int i = 0; i < menu.cellLocalPositions.size(); i++) {
            int[] lp = menu.cellLocalPositions.get(i);
            int px = ox + (lp[0] + menu.cellDisplayOffsetX) * c;
            int py = oy + (lp[1] + menu.cellDisplayOffsetY) * c;

            // Flow intensity as background color (shows for all cells, rod or empty)
            int pct   = menu.getNeutronFlowPct(i);
            int color = flowToColor(pct);
            g.fill(px, py, px + c, py + c, color);

            // Lime green full-cell overlay for fuel rods only
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof ReactorFuelRodItem) {
                g.fill(px, py, px + c, py + c, 0xFF55FF55);
            }
        }
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private static int tempToColor(int temp, int maxTemp, boolean hasRod) {
        if (!hasRod) return 0xFF444444; // empty slot: dark gray
        if (temp <= 0) return 0xFF000066; // cold rod: dark blue
        float f = Math.min(1f, (float) temp / maxTemp);
        int r, g, b;
        if (f < 0.15f) {
            float t = f / 0.15f;
            r = 0; g = 0; b = (int)(66 + 165 * t);
        } else if (f < 0.40f) {
            float t = (f - 0.15f) / 0.25f;
            r = 0; g = (int)(120 * t); b = (int)(231 - 231 * t);
        } else if (f < 0.65f) {
            float t = (f - 0.40f) / 0.25f;
            r = (int)(255 * t); g = (int)(120 + 135 * (1f - t)); b = 0;
        } else {
            float t = Math.min(1f, (f - 0.65f) / 0.35f);
            r = 255; g = (int)(120 * (1f - t)); b = 0;
        }
        return ARGB.color(0xFF, r, g, b);
    }

    private static int flowToColor(int pct) {
        if (pct <= 0) return 0xFF111111;
        float f = Math.min(1f, pct / 100f);
        int r = 0;
        int g = (int)(170 * f);
        int b = (int)(44 + 211 * f);
        return ARGB.color(0xFF, r, g, b);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null) return;

        // Coolant tank bar tooltip
        int tx = leftPos + menu.tankBarX;
        int ty = topPos  + menu.tankBarY;
        if (mouseX >= tx && mouseX < tx + ReactorMenu.TANK_BAR_W
                && mouseY >= ty && mouseY < ty + menu.tankBarH) {
            int amount   = menu.getWaterBuckets()         * 1000;
            int capacity = menu.getWaterCapacityBuckets() * 1000;
            FluidStack waterFluid = menu.getWaterFluid();
            List<Component> lines = GuiUtil.buildFluidTooltip(waterFluid, amount, capacity);
            int pressure = menu.getPressure();
            if (amount > 0) {
                net.minecraft.ChatFormatting pFmt = pressure >= 700 ? net.minecraft.ChatFormatting.RED
                                                  : pressure >= 300 ? net.minecraft.ChatFormatting.YELLOW
                                                  :                   net.minecraft.ChatFormatting.GRAY;
                lines.add(Component.literal(pressure + " / " + ReactorBlockEntity.MAX_PRESSURE + " kPa")
                        .withStyle(pFmt));
            }
            if (amount == 0) {
                lines.add(Component.literal("Reactor offline — no coolant")
                        .withStyle(s -> s.withColor(0xFFFF4444)));
            } else if (pressure == 0 && amount > 0) {
                lines.add(Component.literal("Depressurized — reduced moderation")
                        .withStyle(s -> s.withColor(0xFFFFAA00)));
            } else if (menu.getCoolantTemperature() >= 250) {
                lines.add(Component.literal("Cooling reduced — replace coolant!")
                        .withStyle(s -> s.withColor(0xFFFF4444)));
            }
            g.setTooltipForNextFrame(this.font, lines,
                    GuiUtil.buildPhaseDiagramComponent(waterFluid), mouseX, mouseY);
            return;
        }

        // Core temperature bar tooltip
        int hx = leftPos + menu.heatBarX;
        int hy = topPos  + menu.heatBarY;
        if (mouseX >= hx && mouseX < hx + ReactorMenu.HEAT_BAR_W
                && mouseY >= hy && mouseY < hy + menu.heatBarH) {
            int temp    = menu.getCoreTemperature();
            int maxTemp = ReactorBlockEntity.MAX_TEMPERATURE;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Core Temperature")
                    .withStyle(s -> s.withColor(0xFFFF8800)));
            lines.add(Component.literal(temp + " / " + maxTemp + " °C")
                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
            if (temp >= 1200) {
                lines.add(Component.literal("MELTDOWN RISK!")
                        .withStyle(s -> s.withColor(0xFFFF2222)));
            } else if (temp >= 300) {
                lines.add(Component.literal("Heating up")
                        .withStyle(s -> s.withColor(0xFFFFAA00)));
            }
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }

        // Temperature mini-graph tooltip
        int tgx = leftPos + menu.tempGraphX;
        int tgy = topPos  + menu.tempGraphY;
        int gs  = ReactorMenu.MINI_GRAPH_SIZE;
        if (mouseX >= tgx && mouseX < tgx + gs && mouseY >= tgy && mouseY < tgy + gs) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Cell Temperatures")
                    .withStyle(s -> s.withColor(0xFFFF8800)));
            lines.add(Component.literal("Dark blue = cold   Orange/red = hot")
                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
            lines.add(Component.literal("Coolant: " + menu.getCoolantTemperature() + " °C")
                    .withStyle(s -> s.withColor(0xFF88CCFF)));
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }

        // Neutron flow mini-graph tooltip
        int fgx = leftPos + menu.flowGraphX;
        int fgy = topPos  + menu.flowGraphY;
        if (mouseX >= fgx && mouseX < fgx + gs && mouseY >= fgy && mouseY < fgy + gs) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Neutron Flux Field")
                    .withStyle(s -> s.withColor(0xFF00AAFF)));
            lines.add(Component.literal("Black = no flux   Light blue = peak flux")
                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
            lines.add(Component.literal("Relative — brightest cell = 100%")
                    .withStyle(s -> s.withColor(0xFF888888)));
            lines.add(Component.literal("Lime green = rod inserted")
                    .withStyle(s -> s.withColor(0xFF55FF55)));
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hoveredSlot != null
                && hoveredSlot.getSlotIndex() < menu.cellLocalPositions.size()
                && hoveredSlot.getItem().getItem() instanceof ReactorControlRodItem) {
            int delta = scrollY > 0 ? 1 : -1;
            ClientPacketDistributor.sendToServer(
                    new SetControlRodPacket(menu.containerId, hoveredSlot.getSlotIndex(), delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
