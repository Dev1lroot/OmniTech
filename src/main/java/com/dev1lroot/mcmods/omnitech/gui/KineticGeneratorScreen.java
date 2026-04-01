package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class KineticGeneratorScreen extends AbstractContainerScreen<KineticGeneratorMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/kf_generator.png");

    // Flame sprite coordinates inside the texture (matches vanilla furnace layout)
    private static final int FLAME_U = 176, FLAME_V = 0;
    private static final int FLAME_W = 14,  FLAME_H = 14;
    // Flame icon position in the GUI
    private static final int FLAME_X = 81,  FLAME_Y = 17;

    public KineticGeneratorScreen(KineticGeneratorMenu menu, Inventory playerInventory,
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

        // Draw background texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Draw flame burn indicator (grows from bottom when burning)
        int flameHeight = menu.getFlameHeight();
        if (flameHeight > 0) {
            int yOffset = FLAME_H - flameHeight;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + FLAME_X, y + FLAME_Y + yOffset,
                    (float) FLAME_U, (float) (FLAME_V + yOffset),
                    FLAME_W, flameHeight, 256, 256);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (menu.getBurnTime() > 0) {
            graphics.text(this.font, "KF: Active", 60, 56, 0xFF44FF44, false);
        } else {
            graphics.text(this.font, "KF: Idle", 60, 56, 0xFF888888, false);
        }
    }
}
