package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FluidFillerScreen extends AbstractContainerScreen<FluidFillerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("fluid_filler");
    private GuiDataContext dataCtx;

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

        int inAmt = menu.getInputFluidAmount();
        if (inAmt > 0) {
            graphics.text(this.font,
                    menu.getInputFluid().getHoverName().getString() + " " + inAmt + " mB",
                    26, 10, 0xFF4488FF, false);
        }

        int outAmt = menu.getOutputFluidAmount();
        if (outAmt > 0) {
            String outLabel = menu.getOutputFluid().getHoverName().getString() + " " + outAmt + " mB";
            graphics.text(this.font, outLabel,
                    148 - this.font.width(outLabel) - 2, 10, 0xFF44FF88, false);
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
