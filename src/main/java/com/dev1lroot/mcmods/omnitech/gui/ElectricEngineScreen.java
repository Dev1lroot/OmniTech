package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ElectricEngineScreen extends AbstractContainerScreen<ElectricEngineMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/electric_engine.png");

    // Small "power arrow" sprite (shows KF → EU conversion direction)
    private static final int ARROW_U = 176, ARROW_V = 0;
    private static final int ARROW_X = 79,  ARROW_Y = 34;
    private static final int ARROW_W = 22,  ARROW_H = 16;

    public ElectricEngineScreen(ElectricEngineMenu menu, Inventory playerInventory,
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

        // Background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Conversion arrow (shown only while active)
        if (menu.isPowered()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    (float) ARROW_U, (float) ARROW_V,
                    ARROW_W, ARROW_H, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        boolean powered = menu.isPowered();

        // KF input row
        String kfLabel;
        if (powered) {
            // centi-KF → display as decimal (e.g. 10 → "0.10 KF")
            int centi = menu.getKfCenti();
            kfLabel = String.format("KF Input:  %.2f KF/t", centi / 100f);
        } else {
            kfLabel = "KF Input:  Idle";
        }
        int kfColor = powered ? 0xFF55FF55 : 0xFF888888;
        graphics.text(this.font, kfLabel, 8, 24, kfColor, false);

        // EU output row
        String euLabel;
        if (powered) {
            euLabel = "EU Output: " + menu.getEuPerTick() + " EU/t";
        } else {
            euLabel = "EU Output: 0 EU/t";
        }
        int euColor = powered ? 0xFF44AAFF : 0xFF888888;
        graphics.text(this.font, euLabel, 8, 36, euColor, false);

        // Status banner
        String status = powered ? "[ RUNNING ]" : "[  IDLE  ]";
        int stColor   = powered ? 0xFFFFFF44 : 0xFF666666;
        graphics.text(this.font, status,
                (this.imageWidth - this.font.width(status)) / 2, 56, stColor, false);
    }
}
