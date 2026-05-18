package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.network.SetControlRodPacket;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
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
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Panel background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);

        // Grid area border (1-px darker inset)
        int gx = leftPos + menu.gridOffsetX;
        int gy = topPos  + menu.gridOffsetY;
        int gw = menu.structWidth * ReactorMenu.SLOT_SIZE;
        int gh = menu.structDepth * ReactorMenu.SLOT_SIZE;
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF999999);
        g.fill(gx,     gy,     gx + gw,     gy + gh,     0xFFC6C6C6);

        // Cell slot backgrounds (only where ReactorCells exist)
        for (int[] lp : menu.cellLocalPositions) {
            int sx = leftPos + menu.gridOffsetX + lp[0] * ReactorMenu.SLOT_SIZE + 1;
            int sy = topPos  + menu.gridOffsetY + lp[1] * ReactorMenu.SLOT_SIZE + 1;
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

        // Coolant tank bar
        renderTankBar(g);
        // Core temperature bar
        renderHeatBar(g);
    }

    private void renderTankBar(GuiGraphicsExtractor g) {
        int tx   = leftPos + menu.tankBarX;
        int ty   = topPos  + menu.tankBarY;
        int th   = menu.tankBarH;
        int tw   = ReactorMenu.TANK_BAR_W;

        GuiUtil.renderFrame(g, tx, ty, tw, th);

        int water    = menu.getWaterBuckets();
        int maxWater = menu.getWaterCapacityBuckets();
        if (maxWater <= 0 || water <= 0) return;

        FluidStack fs = menu.getWaterFluid();
        if (fs.isEmpty()) {
            // Fallback: construct a FluidStack just for rendering the water sprite
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null) return;

        // Tooltip for the coolant tank bar
        int tx = leftPos + menu.tankBarX;
        int ty = topPos  + menu.tankBarY;
        if (mouseX >= tx && mouseX < tx + ReactorMenu.TANK_BAR_W
                && mouseY >= ty && mouseY < ty + menu.tankBarH) {
            int amount   = menu.getWaterBuckets()         * 1000;
            int capacity = menu.getWaterCapacityBuckets() * 1000;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("fluid.omnitech.distilled_water"));
            lines.add(Component.literal(amount + " / " + capacity + " mB")
                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
            if (amount == 0) {
                lines.add(Component.literal("Reactor offline — no coolant")
                        .withStyle(s -> s.withColor(0xFFFF4444)));
            }
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }

        // Tooltip for the core temperature bar
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
