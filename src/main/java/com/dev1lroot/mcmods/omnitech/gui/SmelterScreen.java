package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudStringFormatter;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Progress arrow sprite at UV (176, 14) — 24×16 pixels, drawn left to right
    private static final int ARROW_U = 176, ARROW_V = 14;
    private static final int ARROW_W = 54,  ARROW_H = 16;
    private static final int ARROW_X = 79,  ARROW_Y = 54;

    // Fluid gauge sprite at UV (176, 0) — 12×52 pixels, drawn bottom to top
    private static final int FLUID_U = 176, FLUID_V = 0;
    private static final int FLUID_W = 16,  FLUID_H = 52;
    private static final int FLUID_X = 153,  FLUID_Y = 17;

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
//        int arrowWidth = menu.getCookProgressWidth();
//        if (arrowWidth > 0) {
//            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
//                    x + ARROW_X, y + ARROW_Y,
//                    (float) ARROW_U, (float) ARROW_V,
//                    arrowWidth, ARROW_H, 256, 256);
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

        // Входные предметы
        // 9 input slots in 3×3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                GuiUtil.renderSlot(graphics, x + SmelterMenu.GRID_START_X + col * 18, y + SmelterMenu.GRID_START_Y + row * 18);
            }
        }

        GuiUtil.renderProgressBar(graphics, x + ARROW_X, y + ARROW_Y, ARROW_W, menu.getCookProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        HudWriter writer = new HudWriter(graphics,this.font,66,20,10, false);

        // Current temperature
        int curColor = getTempColor(currentTemp, requiredTemp);
        writer.setColor(curColor).write(currentTemp + "°C");

        // Required temperature (only if recipe present)
        if (requiredTemp > 0) {
            int reqColor = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            writer.setColor(0xFF404040).write("/")
                    .setColor(reqColor)
                    .write( requiredTemp + "°C");
        }

        // Fluid amount
        int amount   = menu.getFluidAmount();
        int capacity = menu.getFluidCapacity();
        if (amount > 0) {
            writer.write("\n")
                    .setColor(0xFF404040)
                    .write(menu.getOutputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFFFF8800)
                    .write(amount + "")
                    .setColor(0xFF404040)
                    .write("/")
                    .setColor(0xFF886644)
                    .write(capacity + " mb");
        } else {
            writer.write("\n")
                    .setColor(0xFF404040)
                    .write("Empty");
        }
    }

    private int getTempColor(int current, int required) {
        if (required > 0 && current >= required) return 0xFFFF6600; // orange — ready
        if (current > 500) return 0xFFFFAA00;                       // yellow-orange
        if (current > 100) return 0xFFFFFF00;                       // yellow
        return 0xFFAAAAAA;                                           // grey — cold
    }
}
