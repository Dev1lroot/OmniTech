package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

public class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("foundry");
    private GuiDataContext dataCtx;

    public FoundryScreen(FoundryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input_fluid", menu::getInputFluid,
                        menu::getFluidAmount, menu::getFluidCapacity)
                .value("cook_progress", menu::getCookProgressScaled);
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

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        graphics.text(this.font, currentTemp + " °C", 128, 0, getTempColor(currentTemp, requiredTemp), false);

        if (requiredTemp > 0) {
            String reqStr = "Min: " + requiredTemp + " °C";
            int reqColor  = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            graphics.text(this.font, reqStr, 0, 0, reqColor, false);
        }

        FluidStack fluid  = menu.getInputFluid();
        int amount        = menu.getFluidAmount();
        int capacity      = menu.getFluidCapacity();
        if (!fluid.isEmpty() && amount > 0) {
            String fluidName = fluid.getFluidType().getDescription().getString();
            graphics.text(this.font, fluidName, 64, 0, 0xFFCCCCCC, false);
            graphics.text(this.font, amount + " mb", 16, 0, 0xFFFF8800, false);
            graphics.text(this.font, "/ " + capacity, 16, 64, 0xFF886644, false);
        } else {
            graphics.text(this.font, "Empty", 64, 0, 0xFFAAAAAA, false);
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

    private int getTempColor(int current, int required) {
        if (required > 0 && current >= required) return 0xFFFF6600;
        if (current > 500) return 0xFFFFAA00;
        if (current > 100) return 0xFFFFFF00;
        return 0xFFAAAAAA;
    }
}
