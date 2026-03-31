package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/alloy_furnace.png");

    private static final int MAX_TEMPERATURE = 2000;

    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Render burn progress (flame)
        if (menu.isLit()) {
            int burnProgress = menu.getBurnProgress();
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 44, y + 36 + 14 - burnProgress, 176.0F, (float)(14 - burnProgress), 14, burnProgress + 1, 256, 256);
        }

        // Render cook progress (arrow)
        int cookProgress = menu.getCookProgress();
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 86, y + 35, 176.0F, 14.0F, cookProgress, 16, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        // Determine color based on temperature
        int tempColor = getTemperatureColor(currentTemp, requiredTemp);

        // Current temperature display (below the progressbar)
        String currentTempText = currentTemp + " C";
        graphics.text(this.font, currentTempText, 90, 58, tempColor, false);

        // Required temperature display (above the progressbar, only if there's a recipe)
        if (requiredTemp > 0) {
            String requiredTempText = requiredTemp + " C";
            int reqColor = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000; // Green if reached, red if not (with alpha)
            graphics.text(this.font, requiredTempText, 90, 22, reqColor, false);
        }
    }

    private int getTemperatureColor(int currentTemp, int requiredTemp) {
        // Critical temperature warning (close to max)
        if (currentTemp >= 1800) {
            return 0xFFFF0000; // Bright red - danger! (with alpha)
        }
        // Hot enough for recipe
        if (requiredTemp > 0 && currentTemp >= requiredTemp) {
            return 0xFFFF6600; // Orange - working temperature
        }
        // Heating up
        if (currentTemp > 500) {
            return 0xFFFFAA00; // Yellow-orange
        }
        // Cool/warming
        if (currentTemp > 100) {
            return 0xFFFFFF00; // Yellow
        }
        // Cold
        return 0xFFAAAAAA; // Gray (with alpha)
    }
}
