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

public class KineticGeneratorScreen extends AbstractContainerScreen<KineticGeneratorMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("kinetic_generator");
    private static final GuiDataContext DATA_CTX = new GuiDataContext();

    // Flame sprite at UV (176, 0) in the texture sheet, 14×14 pixels
    private static final int FLAME_W = 14, FLAME_H = 14;
    private static final int FLAME_X = 81, FLAME_Y = 17;

    public KineticGeneratorScreen(KineticGeneratorMenu menu, Inventory playerInventory,
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
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);

        // Flame burn indicator sprite from the texture sheet
        int flameHeight = menu.getFlameHeight();
        if (flameHeight > 0) {
            int yOffset = FLAME_H - flameHeight;
            Identifier tex = Identifier.fromNamespaceAndPath(OmniTech.MODID, LAYOUT.background);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    this.leftPos + FLAME_X, this.topPos + FLAME_Y + yOffset,
                    176.0F, (float) yOffset, FLAME_W, flameHeight, 256, 256);
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
