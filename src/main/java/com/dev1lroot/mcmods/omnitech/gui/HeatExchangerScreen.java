package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HeatExchangerScreen extends AbstractContainerScreen<HeatExchangerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("heat_exchanger");
    private GuiDataContext dataCtx;

    public HeatExchangerScreen(HeatExchangerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input",  menu::getInputFluid,  menu::getInputFluidAmount,  menu::getInputFluidCapacity)
                .fluid("output", menu::getOutputFluid, menu::getOutputFluidAmount, menu::getOutputFluidCapacity)
                .value("process_progress", menu::getProcessProgressScaled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Heat display (centre)
        int stored  = menu.getStoredHeat();
        int maxHeat = menu.getMaxHeat();

        int heatColor;
        if (maxHeat <= 0 || stored == 0) {
            heatColor = 0xFF888888;
        } else {
            float ratio = stored / (float) maxHeat;
            if (ratio < 0.5f)      heatColor = 0xFF44AAFF;
            else if (ratio < 0.8f) heatColor = 0xFFFF8800;
            else                    heatColor = 0xFFFF2200;
        }

        String tempStr = stored + " °C";
        int textW = this.font.width(tempStr);
        new HudWriter(graphics, this.font, (LAYOUT.width - textW) / 2, 22, 10, false)
                .setColor(heatColor).write(tempStr);

        if (maxHeat > 0) {
            String threshStr = "max " + maxHeat + " °C";
            int threshW = this.font.width(threshStr);
            HudWriter threshWriter = new HudWriter(graphics, this.font,
                    (LAYOUT.width - threshW) / 2, 32, 10, false);
            if (stored >= maxHeat) {
                threshWriter.setColor(0xFFFF2200).write("OVERHEATED");
            } else {
                threshWriter.setColor(0xFF606060).write(threshStr);
            }
        }

        // Process label
        HudWriter procWriter = new HudWriter(graphics, this.font, 52, 60, 10, false);
        if (maxHeat > 0 && stored < maxHeat) {
            procWriter.setColor(0xFF44AA44)
                    .write(String.format("%.0f%%", menu.getProcessProgressScaled()))
                    .setColor(0xFF606060).write(" processing");
        } else if (maxHeat > 0) {
            procWriter.setColor(0xFFFF2200).write("cooling down...");
        } else {
            procWriter.setColor(0xFF888888).write("no recipe");
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
}
