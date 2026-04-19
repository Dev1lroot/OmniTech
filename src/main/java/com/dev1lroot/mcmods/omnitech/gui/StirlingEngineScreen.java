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

public class StirlingEngineScreen extends AbstractContainerScreen<StirlingEngineMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("stirling_engine");
    private static final GuiDataContext DATA_CTX = new GuiDataContext();

    // Bar sprites at UV (176, 0) and (188, 0) in the texture sheet, 12×52 pixels
    private static final int FILL_W = 12, FILL_H = 52;
    private static final int WATER_BAR_X = 53, WATER_BAR_Y = 17;
    private static final int STEAM_BAR_X = 71, STEAM_BAR_Y = 17;

    public StirlingEngineScreen(StirlingEngineMenu menu, Inventory playerInventory,
            Component title) {
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

        // Water fill bar (bottom-up sprite)
        int waterH = menu.getWaterBarHeight();
        if (waterH > 0) {
            int yOff = FILL_H - waterH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + WATER_BAR_X, this.topPos + WATER_BAR_Y + yOff,
                    176.0F, (float) yOff, FILL_W, waterH, 256, 256);
        }

        // Steam fill bar (bottom-up sprite)
        int steamH = menu.getSteamBarHeight();
        if (steamH > 0) {
            int yOff = FILL_H - steamH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + STEAM_BAR_X, this.topPos + STEAM_BAR_Y + yOff,
                    188.0F, (float) yOff, FILL_W, steamH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int steam = menu.getStoredSteam();
        graphics.text(this.font, "Steam: " + steam + " mb", 90, 20, 0xFFDDDDDD, false);

        int water = menu.getStoredWater();
        graphics.text(this.font, "Water: " + water + " mb", 90, 32, 0xFF4488FF, false);

        if (menu.isRunning()) {
            graphics.text(this.font, "Working",    96, 44, 0xFF44FF44, false);
        } else if (steam <= 0) {
            graphics.text(this.font, "No Steam",   96, 44, 0xFFFF4444, false);
        } else {
            graphics.text(this.font, "Full Water", 96, 44, 0xFFFFAA00, false);
        }
    }
}
