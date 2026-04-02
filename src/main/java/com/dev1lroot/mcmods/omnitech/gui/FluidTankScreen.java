package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class FluidTankScreen extends AbstractContainerScreen<FluidTankMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/fluid_tank.png");

    // Fluid gauge fill sprite: 12×52 at UV (176, 0) — drawn bottom-up
    private static final int FLUID_FILL_U = 176, FLUID_FILL_V = 0;
    private static final int FLUID_FILL_W = 12,  FLUID_FILL_H = 52;
    private static final int FLUID_BAR_X  = 53,  FLUID_BAR_Y  = 17;

    public FluidTankScreen(FluidTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Fluid gauge (blue, fills from bottom)
        int fluidH = menu.getFluidBarHeight();
        if (fluidH > 0) {
            int yOff = FLUID_FILL_H - fluidH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + FLUID_BAR_X, y + FLUID_BAR_Y + yOff,
                    (float) FLUID_FILL_U, (float) (FLUID_FILL_V + yOff),
                    FLUID_FILL_W, fluidH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int stored = menu.getStoredFluid();
        int max    = menu.getMaxFluid();
        graphics.text(this.font, stored + " mb", 90, 20, 0xFF4488FF, false);
        graphics.text(this.font, "/ " + max + " mb", 90, 32, 0xFF8888AA, false);

        if (stored >= max) {
            graphics.text(this.font, "Full", 90, 44, 0xFF44FF44, false);
        } else if (stored <= 0) {
            graphics.text(this.font, "Empty", 90, 44, 0xFFAAAAAA, false);
        }
    }
}
