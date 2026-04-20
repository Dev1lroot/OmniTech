package com.dev1lroot.mcmods.omnitech.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CokeOvenScreen extends AbstractContainerScreen<CokeOvenMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("omnitech", "textures/gui/coke_oven.png");

    public CokeOvenScreen(CokeOvenMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 248, 222);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 512, 256);

        if (menu.isLit()) {
            int progress = menu.getProcessProgress();
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    this.leftPos + 106, this.topPos + 35,
                    248.0F, 0.0F, progress, 16, 512, 256);
        }
    }
}
