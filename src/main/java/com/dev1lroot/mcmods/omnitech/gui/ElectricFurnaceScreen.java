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

    // Energy bar sprite (left side of GUI)
    private static final int BAR_U = 176, BAR_V = 0;
    private static final int BAR_X = 56,  BAR_Y = 17;
    private static final int BAR_H = 14;

    // Cook progress arrow sprite (right side)
    private static final int ARROW_U = 176, ARROW_V = 14;
    private static final int ARROW_X = 79,  ARROW_Y = 34;
    private static final int ARROW_H = 17;

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

        // Energy-fill bar (grows left→right)
        int barWidth = menu.getEnergyBarWidth();
        if (barWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + BAR_X, y + BAR_Y,
                    (float) BAR_U, (float) BAR_V,
                    barWidth, BAR_H, 256, 256);
        }

        // Cook progress arrow (grows left→right while cooking)
        int arrowWidth = menu.getCookProgressWidth();
        if (arrowWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    (float) ARROW_U, (float) ARROW_V,
                    arrowWidth, ARROW_H, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // EU buffer readout (stored / max)
        String euText = String.format("%.0f / %.0f EU", menu.getEnergyStored(), menu.getMaxEu());
        int euColor = menu.getEnergyStored() > 0 ? 0xFF44AAFF : 0xFF888888;
        graphics.text(this.font, euText,
                (this.imageWidth - this.font.width(euText)) / 2, 56, euColor, false);

        // Recipe cost reminder
        String costText = String.format("%.0f EU/recipe", menu.getEuPerRecipe());
        graphics.text(this.font, costText,
                (this.imageWidth - this.font.width(costText)) / 2, 66, 0xFF888888, false);
    }
}
