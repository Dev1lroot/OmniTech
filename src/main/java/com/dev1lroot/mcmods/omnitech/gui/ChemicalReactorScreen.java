package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ChemicalReactorScreen extends AbstractContainerScreen<ChemicalReactorMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("chemical_reactor");
    private GuiDataContext dataCtx;

    public ChemicalReactorScreen(ChemicalReactorMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input1", menu::getInputFluid1,
                        menu::getInputFluid1Amount, menu::getInputFluid1Capacity)
                .fluid("input2", menu::getInputFluid2,
                        menu::getInputFluid2Amount, menu::getInputFluid2Capacity)
                .fluid("output", menu::getOutputFluid,
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

        // Temperature display (centred)
        int heat    = menu.getStoredHeat();
        int reqTemp = menu.getRequiredTemperature();
        String tempText = heat + "°C";
        if (reqTemp > 0) tempText += " / " + reqTemp + "°C";
        int tempColor = (reqTemp == 0 || heat >= reqTemp) ? 0xFFFF8844 : 0xFFFF4444;
        graphics.text(this.font, tempText,
                (LAYOUT.width - this.font.width(tempText)) / 2, 26, tempColor, false);

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
