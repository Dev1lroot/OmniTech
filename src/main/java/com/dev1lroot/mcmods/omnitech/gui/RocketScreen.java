package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class RocketScreen extends AbstractContainerScreen<RocketMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Fuel bar — spans the width of the GUI below the slots
    private static final int FUEL_BAR_X = 8;
    private static final int FUEL_BAR_Y = 58;
    private static final int FUEL_BAR_W = 160;

    // Label row above the fuel bar
    private static final int LABEL_Y = FUEL_BAR_Y - 12;

    public RocketScreen(RocketMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 72;
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

        // Background texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Fuel input slot (water bucket)
        GuiUtil.renderSlot(graphics, x + RocketMenu.INPUT_SLOT_X, y + RocketMenu.INPUT_SLOT_Y);
        // Empty bucket output slot
        GuiUtil.renderSlot(graphics, x + RocketMenu.OUTPUT_SLOT_X, y + RocketMenu.OUTPUT_SLOT_Y);

        // Fuel bar
        int fuel    = menu.getFuelAmount();
        int maxFuel = RocketEntity.MAX_FUEL;
        float progress = maxFuel > 0 ? (float) fuel / maxFuel * 100f : 0f;
        GuiUtil.renderProgressBar(graphics, x + FUEL_BAR_X, y + FUEL_BAR_Y, FUEL_BAR_W, progress);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Slot labels
        new HudWriter(graphics, this.font, RocketMenu.INPUT_SLOT_X - 1, RocketMenu.INPUT_SLOT_Y - 10, 10, false)
                .setColor(0xFF888888).write("Fuel In");

        new HudWriter(graphics, this.font, RocketMenu.OUTPUT_SLOT_X - 1, RocketMenu.OUTPUT_SLOT_Y - 10, 10, false)
                .setColor(0xFF888888).write("Bucket");

        // Fuel amount label above the bar
        int fuel    = menu.getFuelAmount();
        int maxFuel = RocketEntity.MAX_FUEL;
        new HudWriter(graphics, this.font, FUEL_BAR_X, LABEL_Y, 10, false)
                .setColor(0xFF4488FF).write(fuel + "")
                .setColor(0xFF404040).write(" / " + maxFuel + " mB");
    }
}
