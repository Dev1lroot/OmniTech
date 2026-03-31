package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ManualCentrifugeScreen extends AbstractContainerScreen<ManualCentrifugeMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/manual_centrifuge.png");

    public ManualCentrifugeScreen(ManualCentrifugeMenu menu, Inventory playerInventory, Component title) {
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

        // Kinetic force progress bar (24px wide, at position 56,35)
        int progress = menu.getKineticProgress();
        if (progress > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 56, y + 35, 176.0F, 0.0F, progress, 16, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int force = menu.getKineticForce();
        int required = menu.getRequiredKineticForce();

        if (required > 0) {
            String forceText = force + " / " + required + " KF";
            int color = force >= required ? 0xFF00CC00 : 0xFFCCCC00;
            graphics.text(this.font, forceText, 102, 20, color, false);
        } else {
            graphics.text(this.font, "No recipe", 102, 20, 0xFF888888, false);
        }
    }
}
