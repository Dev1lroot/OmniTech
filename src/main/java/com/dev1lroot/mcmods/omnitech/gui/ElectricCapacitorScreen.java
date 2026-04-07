package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ElectricCapacitorScreen extends AbstractContainerScreen<ElectricCapacitorMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/electric_capacitor.png");

    // Charge bar position (inside the 176×166 GUI background)
    private static final int BAR_X = 39, BAR_Y = 34;
    // Sprite sheet coords for the filled bar portion
    private static final int BAR_U = 176, BAR_V = 0;
    private static final int BAR_H = 16;

    public ElectricCapacitorScreen(ElectricCapacitorMenu menu, Inventory playerInventory,
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

        // Energy bar (grows left→right)
        int barWidth = menu.getChargeBarWidth();
        if (barWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + BAR_X, y + BAR_Y,
                    (float) BAR_U, (float) BAR_V,
                    barWidth, BAR_H, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        String euText = menu.getStoredEu() + " / " + menu.getMaxEu() + " EU";
        graphics.text(this.font, euText,
                (this.imageWidth - this.font.width(euText)) / 2, 58, 0xFF44AAFF, false);
    }
}
