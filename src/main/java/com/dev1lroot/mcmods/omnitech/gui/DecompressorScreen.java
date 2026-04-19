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

public class DecompressorScreen extends AbstractContainerScreen<DecompressorMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("decompressor");
    private GuiDataContext dataCtx;

    public DecompressorScreen(DecompressorMenu menu, Inventory playerInventory, Component title) {
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

        // Cold readout (centre)
        int stored  = menu.getStoredCold();
        int maxCold = menu.getMaxCold();

        int coldColor;
        if (maxCold <= 0 || stored == 0) {
            coldColor = 0xFF888888;
        } else {
            float ratio = stored / (float) maxCold;
            if (ratio < 0.5f)       coldColor = 0xFF88CCFF;
            else if (ratio < 0.8f)  coldColor = 0xFF2288FF;
            else                     coldColor = 0xFF0044DD;
        }

        String coldStr = "-" + stored + " °C";
        int textW = this.font.width(coldStr);
        new HudWriter(graphics, this.font, (LAYOUT.width - textW) / 2, 22, 10, false)
                .setColor(coldColor).write(coldStr);

        if (maxCold > 0) {
            String threshStr = "max -" + maxCold + " °C";
            int threshW = this.font.width(threshStr);
            HudWriter threshWriter = new HudWriter(graphics, this.font,
                    (LAYOUT.width - threshW) / 2, 32, 10, false);
            if (stored >= maxCold) {
                threshWriter.setColor(0xFF2244FF).write("OVER-CHILLED");
            } else {
                threshWriter.setColor(0xFF606060).write(threshStr);
            }
        }

        // Process label
        HudWriter procWriter = new HudWriter(graphics, this.font, 52, 60, 10, false);
        if (maxCold > 0 && stored < maxCold) {
            procWriter.setColor(0xFF44AAFF)
                    .write(String.format("%.0f%%", menu.getProcessProgressScaled()))
                    .setColor(0xFF606060).write(" processing");
        } else if (maxCold > 0) {
            procWriter.setColor(0xFF2244FF).write("warming up...");
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
