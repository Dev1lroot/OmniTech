package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/electric_furnace.png");

    // Progress arrow sprite coordinates
    private static final int ARROW_U = 176, ARROW_V = 0;
    private static final int ARROW_X = 79,  ARROW_Y = 34;
    private static final int ARROW_H = 16;

    // Power indicator (small lightning bolt icon)
    private static final int POWER_U = 176, POWER_V = 16;
    private static final int POWER_X = 56,  POWER_Y = 55;
    private static final int POWER_W = 10,  POWER_H = 10;

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory playerInventory,
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

        // Progress arrow (grows left→right while smelting)
        int arrowWidth = menu.getProgressArrowWidth();
        if (arrowWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    (float) ARROW_U, (float) ARROW_V,
                    arrowWidth, ARROW_H, 256, 256);
        }

        // Power indicator (shows when EU is being received)
        if (menu.isPowered()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + POWER_X, y + POWER_Y,
                    (float) POWER_U, (float) POWER_V,
                    POWER_W, POWER_H, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        String status = menu.isPowered() ? "EU: Active" : "EU: Idle";
        int color = menu.isPowered() ? 0xFF44AAFF : 0xFF888888;
        graphics.text(this.font, status, 8, 56, color, false);
    }
}
