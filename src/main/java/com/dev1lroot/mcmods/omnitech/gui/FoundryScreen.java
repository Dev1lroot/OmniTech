package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

public class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/foundry.png");

    // Texture layout (176×166 background + sprites at U≥176):
    //   Progress arrow : UV (176, 14), 24×16 px — left-to-right fill
    //   Fluid gauge    : UV (176,  0), 12×52 px — bottom-to-top fill

    private static final int ARROW_U = 176, ARROW_V = 14;
    private static final int ARROW_W = 24,  ARROW_H = 16;
    private static final int ARROW_X = 62,  ARROW_Y = 35;

    private static final int FLUID_U = 176, FLUID_V = 0;
    private static final int FLUID_W = 12,  FLUID_H = 52;
    private static final int FLUID_X = 8,   FLUID_Y = 17;

    public FoundryScreen(FoundryMenu menu, Inventory playerInventory, Component title) {
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

        // Fluid gauge (bottom → top fill)
        int fluidH = menu.getFluidBarHeight();
        if (fluidH > 0) {
            int yOff = FLUID_H - fluidH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + FLUID_X, y + FLUID_Y + yOff,
                    (float) FLUID_U, (float) (FLUID_V + yOff),
                    FLUID_W, fluidH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        // Current temperature
        graphics.text(this.font, currentTemp + " °C", 26, 72, getTempColor(currentTemp, requiredTemp), false);

        // Required temperature (only when a recipe is matched)
        if (requiredTemp > 0) {
            String reqStr = "Min: " + requiredTemp + " °C";
            int reqColor  = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            graphics.text(this.font, reqStr, 105, 25, reqColor, false);
        }

        // Input fluid — type (from synced BE) and amount (from ContainerData)
        FluidStack fluid = menu.getInputFluid();
        int amount   = menu.getFluidAmount();
        int capacity = menu.getFluidCapacity();
        if (!fluid.isEmpty() && amount > 0) {
            String fluidName = fluid.getFluidType().getDescription().getString();
            graphics.text(this.font, fluidName, 105, 35, 0xFFCCCCCC, false);
            graphics.text(this.font, amount + " mb", 105, 44, 0xFFFF8800, false);
            graphics.text(this.font, "/ " + capacity, 105, 53, 0xFF886644, false);
        } else {
            graphics.text(this.font, "Empty", 105, 40, 0xFFAAAAAA, false);
        }
    }

    private int getTempColor(int current, int required) {
        if (required > 0 && current >= required) return 0xFFFF6600;
        if (current > 500) return 0xFFFFAA00;
        if (current > 100) return 0xFFFFFF00;
        return 0xFFAAAAAA;
    }
}
