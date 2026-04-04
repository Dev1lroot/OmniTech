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

    // Индикаторы (используем те же координаты UV, но логика теперь — Пар и Вода)
    private static final int FILL_W = 12,  FILL_H = 52;

    private static final int WATER_FILL_U = 176, WATER_FILL_V = 0;
    private static final int WATER_BAR_X  = 53,  WATER_BAR_Y  = 17;

    private static final int STEAM_FILL_U = 188, STEAM_FILL_V = 0; // Можно подставить текстуру посветлее
    private static final int STEAM_BAR_X  = 71,  STEAM_BAR_Y  = 17;

    public StirlingEngineScreen(StirlingEngineMenu menu, Inventory playerInventory, Component title) {
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
        int x = this.leftPos, y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Шкала воды (справа)
        int waterH = menu.getWaterBarHeight();
        if (waterH > 0) {
            int yOff = FILL_H - waterH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + WATER_BAR_X, y + WATER_BAR_Y + yOff,
                    (float) WATER_FILL_U, (float) (WATER_FILL_V + yOff),
                    FILL_W, waterH, 256, 256);
        }

        // Шкала пара (слева)
        int steamH = menu.getSteamBarHeight();
        if (steamH > 0) {
            int yOff = FILL_H - steamH;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + STEAM_BAR_X, y + STEAM_BAR_Y + yOff,
                    (float) STEAM_FILL_U, (float) (STEAM_FILL_V + yOff),
                    FILL_W, steamH, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Вывод количества пара
        int steam = menu.getStoredSteam();
        graphics.text(this.font, "Steam: " + steam + " mb", 90, 20, 0xFFDDDDDD, false);

        // Вывод количества воды
        int water = menu.getStoredWater();
        graphics.text(this.font, "Water: " + water + " mb", 90, 32, 0xFF4488FF, false);

        // Статус работы
        if (menu.isRunning()) {
            graphics.text(this.font, "Working", 96, 44, 0xFF44FF44, false);
        } else if (steam <= 0) {
            graphics.text(this.font, "No Steam", 96, 44, 0xFFFF4444, false);
        } else {
            graphics.text(this.font, "Full Water", 96, 44, 0xFFFFAA00, false);
        }
    }
}