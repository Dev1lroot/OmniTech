package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class StirlingEngineScreen extends AbstractContainerScreen<StirlingEngineMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/stirling_engine.png");

    // Water gauge fill sprite: 12×52 at UV (176, 0) — drawn bottom-up
    private static final int WATER_FILL_U = 176, WATER_FILL_V = 0;
    private static final int WATER_FILL_W = 12,  WATER_FILL_H = 52;
    private static final int WATER_BAR_X  = 53,  WATER_BAR_Y  = 17;

    // Heat gauge fill sprite: 12×52 at UV (188, 0) — drawn bottom-up
    private static final int HEAT_FILL_U = 188, HEAT_FILL_V = 0;
    private static final int HEAT_FILL_W = 12,  HEAT_FILL_H = 52;
    private static final int HEAT_BAR_X  = 71,  HEAT_BAR_Y  = 17;

    public StirlingEngineScreen(StirlingEngineMenu menu, Inventory playerInventory,
            Component title) {
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

        // Water gauge (blue, fills from bottom)
        int waterH = menu.getWaterBarHeight();
        if (waterH > 0) {
            int yOff = WATER_FILL_H - waterH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + WATER_BAR_X, y + WATER_BAR_Y + yOff,
                    (float) WATER_FILL_U, (float) (WATER_FILL_V + yOff),
                    WATER_FILL_W, waterH, 256, 256);
        }

        // Heat gauge (orange-red, fills from bottom)
        int heatH = menu.getHeatBarHeight();
        if (heatH > 0) {
            int yOff = HEAT_FILL_H - heatH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + HEAT_BAR_X, y + HEAT_BAR_Y + yOff,
                    (float) HEAT_FILL_U, (float) (HEAT_FILL_V + yOff),
                    HEAT_FILL_W, heatH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Heat readout
        int heat   = menu.getStoredHeat();
        int hColor = heatColor(heat);
        graphics.text(this.font, heat + " °C", 90, 20, hColor, false);

        // Water readout
        int water = menu.getStoredWater();
        graphics.text(this.font, water + " mb", 90, 32, 0xFF4488FF, false);

        // Running status
        if (menu.isRunning()) {
            graphics.text(this.font, "Running", 96, 44, 0xFF44FF44, false);
        } else if (heat <= 100) {
            graphics.text(this.font, "Heating…", 96, 44, 0xFFAAAAAA, false);
        } else {
            graphics.text(this.font, "No water", 96, 44, 0xFFFF4444, false);
        }
    }

    private static int heatColor(int heat) {
        if (heat >= 250) return 0xFFFF2200;
        if (heat >= 150) return 0xFFFF8800;
        if (heat >= 100) return 0xFFFFCC00;
        return 0xFFAAAAAA;
    }
}
