package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {

    public ReactorScreen(ReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.imageWidth, menu.imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY     = menu.gridOffsetY - 11;
        this.inventoryLabelX = menu.invOffsetX;
        this.inventoryLabelY = menu.invOffsetY - 10;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Panel background
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);

        // Grid area border (1-px darker inset)
        int gx = leftPos + menu.gridOffsetX;
        int gy = topPos  + menu.gridOffsetY;
        int gw = menu.structWidth  * ReactorMenu.SLOT_SIZE;
        int gh = menu.structDepth  * ReactorMenu.SLOT_SIZE;
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, 0xFF999999);
        g.fill(gx,     gy,     gx + gw,     gy + gh,     0xFFC6C6C6);

        // Cell slot backgrounds (only where ReactorCells exist)
        for (int[] lp : menu.cellLocalPositions) {
            int sx = leftPos + menu.gridOffsetX + lp[0] * ReactorMenu.SLOT_SIZE + 1;
            int sy = topPos  + menu.gridOffsetY + lp[1] * ReactorMenu.SLOT_SIZE + 1;
            GuiUtil.renderSlot(g, sx, sy);
        }

        // Player inventory slot backgrounds
        int iox = leftPos + menu.invOffsetX;
        int ioy = topPos  + menu.invOffsetY;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                GuiUtil.renderSlot(g,
                        iox + col * ReactorMenu.SLOT_SIZE + 1,
                        ioy + row * ReactorMenu.SLOT_SIZE + 1);

        int hotbarY = ioy + 3 * ReactorMenu.SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++)
            GuiUtil.renderSlot(g, iox + col * ReactorMenu.SLOT_SIZE + 1, hotbarY + 1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
    }
}
