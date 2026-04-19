package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("alloy_furnace");
    private GuiDataContext dataCtx;

    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .value("cook_progress", menu::getCookProgressScaled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);

        // Burn flame sprite from texture sheet
        if (menu.isLit()) {
            int burnProgress = menu.getBurnProgress();
            Identifier tex = Identifier.fromNamespaceAndPath(OmniTech.MODID, LAYOUT.background);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + 44, this.topPos + 36 + 14 - burnProgress,
                    176.0F, (float) (14 - burnProgress), 14, burnProgress + 1, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        int tempColor = getTemperatureColor(currentTemp, requiredTemp);
        graphics.text(this.font, currentTemp + " C", 90, 58, tempColor, false);

        if (requiredTemp > 0) {
            int reqColor = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            graphics.text(this.font, requiredTemp + " C", 90, 22, reqColor, false);
        }
    }

    private int getTemperatureColor(int current, int required) {
        if (current >= 1800) return 0xFFFF0000;
        if (required > 0 && current >= required) return 0xFFFF6600;
        if (current > 500) return 0xFFFFAA00;
        if (current > 100) return 0xFFFFFF00;
        return 0xFFAAAAAA;
    }
}
