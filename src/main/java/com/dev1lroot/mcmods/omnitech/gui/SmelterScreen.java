package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Progress arrow sprite at UV (176, 14) — 24×16 pixels, drawn left to right
    private static final int ARROW_U = 176, ARROW_V = 14;
    private static final int ARROW_W = 24,  ARROW_H = 16;
    private static final int ARROW_X = 62,  ARROW_Y = 35;

    // Fluid gauge sprite at UV (176, 0) — 12×52 pixels, drawn bottom to top
    private static final int FLUID_U = 176, FLUID_V = 0;
    private static final int FLUID_W = 12,  FLUID_H = 52;
    private static final int FLUID_X = 90,  FLUID_Y = 17;

    public SmelterScreen(SmelterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Standard GUI size
//        this.imageWidth  = 176;
//        this.imageHeight = 166;
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

        // Background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Progress arrow (left → right fill)
        int arrowWidth = menu.getCookProgressWidth();
        if (arrowWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    (float) ARROW_U, (float) ARROW_V,
                    arrowWidth, ARROW_H, 256, 256);
        }

//        // Fluid gauge (bottom → top fill)
//        int fluidH = menu.getFluidBarHeight();
//        if (fluidH > 0) {
//            int yOff = FLUID_H - fluidH;
//            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
//                    x + FLUID_X, y + FLUID_Y + yOff,
//                    (float) FLUID_U, (float) (FLUID_V + yOff),
//                    FLUID_W, fluidH, 256, 256);
//        }

        // Рамка блока с жидкостью
        GuiUtil.renderFrame(graphics, x + FLUID_X, y + FLUID_Y, FLUID_W, FLUID_H);

        // Шкала жидкости
        GuiUtil.renderFluidBar(
                graphics,
                menu.getOutputFluid(),
                menu.getFluidAmount(),
                menu.getFluidCapacity(),
                x + FLUID_X,
                y + FLUID_Y,
                FLUID_W,
                FLUID_H
        );
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        // Current temperature
        String curTempStr = currentTemp + " °C";
        int curColor = getTempColor(currentTemp, requiredTemp);
        graphics.text(this.font, curTempStr, 8, 72, curColor, false);

        // Required temperature (only if recipe present)
        if (requiredTemp > 0) {
            String reqTempStr = "Min: " + requiredTemp + " °C";
            int reqColor = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            graphics.text(this.font, reqTempStr, 105, 25, reqColor, false);
        }

        // Fluid amount
        int amount   = menu.getFluidAmount();
        int capacity = menu.getFluidCapacity();
        if (amount > 0) {
            graphics.text(this.font, amount + " mb", 105, 40, 0xFFFF8800, false);
            graphics.text(this.font, "/ " + capacity, 105, 50, 0xFF886644, false);
        } else {
            graphics.text(this.font, "Empty", 105, 40, 0xFFAAAAAA, false);
        }
    }

    private int getTempColor(int current, int required) {
        if (required > 0 && current >= required) return 0xFFFF6600; // orange — ready
        if (current > 500) return 0xFFFFAA00;                       // yellow-orange
        if (current > 100) return 0xFFFFFF00;                       // yellow
        return 0xFFAAAAAA;                                           // grey — cold
    }
}
