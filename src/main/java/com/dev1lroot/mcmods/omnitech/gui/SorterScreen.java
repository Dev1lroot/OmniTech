package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Screen for {@link SorterMenu}.
 *
 * <p>Layout (176 × 166 px background):
 * <pre>
 *  y=18  ← row 0: LEFT  side filters  (label "L" at x=154)
 *  y=36  ← row 1: BACK  side filters  (label "B" at x=154)
 *  y=54  ← row 2: RIGHT side filters  (label "R" at x=154)
 *  y=84  ← player inventory (3 rows)
 *  y=142 ← player hotbar
 * </pre>
 *
 * <p>Requires {@code textures/gui/sorter.png} (256×256). Until the texture
 * is created the background will show as missing-texture purple; all slots
 * render correctly regardless.
 */
public class SorterScreen extends AbstractContainerScreen<SorterMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/sorter.png");

    // Row label positions (right of the last filter slot)
    private static final int LABEL_X       = 154;
    private static final int LABEL_Y_LEFT  = 22;
    private static final int LABEL_Y_BACK  = 40;
    private static final int LABEL_Y_RIGHT = 58;

    // Separator line between filter area and player inventory (drawn as a dark rectangle)
    private static final int SEP_X = 7, SEP_Y = 72, SEP_W = 162, SEP_H = 1;

    public SorterScreen(SorterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // Draw main GUI background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Draw a thin separator between filters and player inventory
        graphics.fill(x + SEP_X, y + SEP_Y,
                x + SEP_X + SEP_W, y + SEP_Y + SEP_H, 0xFF555555);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Side labels to the right of each filter row
        graphics.text(this.font, "L", LABEL_X, LABEL_Y_LEFT,  0xFFCCCCCC, false);
        graphics.text(this.font, "B", LABEL_X, LABEL_Y_BACK,  0xFFCCCCCC, false);
        graphics.text(this.font, "R", LABEL_X, LABEL_Y_RIGHT, 0xFFCCCCCC, false);
    }
}
