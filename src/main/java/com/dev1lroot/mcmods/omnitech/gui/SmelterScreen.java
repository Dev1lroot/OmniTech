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

public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("smelter");
    private GuiDataContext dataCtx;

    public SmelterScreen(SmelterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("output_fluid", menu::getOutputFluid,
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

        HudWriter writer = new HudWriter(graphics, this.font, 66, 20, 10, false);

        int curColor = getTempColor(currentTemp, requiredTemp);
        writer.setColor(curColor).write(currentTemp + "°C");

        if (requiredTemp > 0) {
            int reqColor = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            writer.setColor(0xFF404040).write("/")
                    .setColor(reqColor).write(requiredTemp + "°C");
        }

        int amount   = menu.getFluidAmount();
        int capacity = menu.getFluidCapacity();
        if (amount > 0) {
            writer.write("\n")
                    .setColor(0xFF404040).write(menu.getOutputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFFFF8800).write(amount + "")
                    .setColor(0xFF404040).write("/")
                    .setColor(0xFF886644).write(capacity + " mb");
        } else {
            writer.write("\n").setColor(0xFF404040).write("Empty");
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
