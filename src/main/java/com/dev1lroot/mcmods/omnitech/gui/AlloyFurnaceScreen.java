package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/alloy_furnace.png");
    private static final Identifier LIT_PROGRESS_SPRITE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "container/alloy_furnace/lit_progress");
    private static final Identifier BURN_PROGRESS_SPRITE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "container/alloy_furnace/burn_progress");

    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
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
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Render burn progress (flame)
        if (menu.isLit()) {
            int burnProgress = menu.getBurnProgress();
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 56, y + 36 + 13 - burnProgress, 176.0F, (float)(13 - burnProgress), 14, burnProgress + 1, 256, 256);
        }

        // Render cook progress (arrow)
        int cookProgress = menu.getCookProgress();
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 89, y + 34, 176.0F, 14.0F, cookProgress + 1, 16, 256, 256);
    }
}
