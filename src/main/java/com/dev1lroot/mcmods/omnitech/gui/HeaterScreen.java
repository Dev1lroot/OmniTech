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

public class HeaterScreen extends AbstractContainerScreen<HeaterMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("heater");
    private static final GuiDataContext DATA_CTX = new GuiDataContext();

    // Sprite coordinates inside heater.png
    private static final int FLAME_W = 14, FLAME_H = 14;
    private static final int FLAME_X = 81, FLAME_Y = 17;

    private static final int HEAT_FILL_W = 12, HEAT_FILL_H = 52;
    private static final int HEAT_BAR_X  = 141, HEAT_BAR_Y = 15;

    public HeaterScreen(HeaterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, DATA_CTX,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height);

        Identifier tex = Identifier.fromNamespaceAndPath(OmniTech.MODID, LAYOUT.background);

        // Flame burn indicator (grows from bottom)
        int flameH = menu.getFlameHeight();
        if (flameH > 0) {
            int yOff = FLAME_H - flameH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + FLAME_X, this.topPos + FLAME_Y + yOff,
                    176.0F, (float) yOff, FLAME_W, flameH, 256, 256);
        }

        // Heat gauge fill (grows from bottom)
        int heatH = menu.getHeatBarHeight();
        if (heatH > 0) {
            int yOff = HEAT_FILL_H - heatH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + HEAT_BAR_X, this.topPos + HEAT_BAR_Y + yOff,
                    176.0F, (float) (14 + yOff), HEAT_FILL_W, heatH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        int heat  = menu.getStoredHeat();
        int color = heatColor(heat);
        graphics.text(this.font, heat + " °C", 110, 20, color, false);
    }

    private static int heatColor(int heat) {
        if (heat >= 250) return 0xFFFF2200;
        if (heat >= 150) return 0xFFFF8800;
        if (heat >= 50)  return 0xFFFFCC00;
        return 0xFFAAAAAA;
    }
}
