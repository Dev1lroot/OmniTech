package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class RotaryCompressorScreen extends AbstractContainerScreen<RotaryCompressorMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Input fluid tank (left side)
    private static final int IN_TANK_X = 8,  IN_TANK_Y = 17, IN_TANK_W = 16, IN_TANK_H = 52;

    // Output fluid tank (right side)
    private static final int OUT_TANK_X = 152, OUT_TANK_Y = 17, OUT_TANK_W = 16, OUT_TANK_H = 52;

    // KF progress bar (center)
    private static final int KF_BAR_X = 52, KF_BAR_Y = 35, KF_BAR_W = 72;

    public RotaryCompressorScreen(RotaryCompressorMenu menu, Inventory playerInventory, Component title) {
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

        // Background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // Input tank (left)
        GuiUtil.renderFrame(graphics, x + IN_TANK_X, y + IN_TANK_Y, IN_TANK_W, IN_TANK_H);
        GuiUtil.renderFluidBar(
                graphics,
                menu.getInputFluid(),
                menu.getInputFluidAmount(),
                menu.getInputFluidCapacity(),
                x + IN_TANK_X, y + IN_TANK_Y, IN_TANK_W, IN_TANK_H);

        // Output tank (right)
        GuiUtil.renderFrame(graphics, x + OUT_TANK_X, y + OUT_TANK_Y, OUT_TANK_W, OUT_TANK_H);
        GuiUtil.renderFluidBar(
                graphics,
                menu.getOutputFluid(),
                menu.getOutputFluidAmount(),
                menu.getOutputFluidCapacity(),
                x + OUT_TANK_X, y + OUT_TANK_Y, OUT_TANK_W, OUT_TANK_H);

        // KF progress bar
        GuiUtil.renderProgressBar(graphics, x + KF_BAR_X, y + KF_BAR_Y, KF_BAR_W, menu.getKfProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        HudWriter writer = new HudWriter(graphics, this.font, 32, 20, 10, false);

        // Input fluid
        int inAmt = menu.getInputFluidAmount();
        int inCap = menu.getInputFluidCapacity();
        if (inAmt > 0) {
            writer.setColor(0xFF4488FF).write(menu.getInputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFF4488FF).write(inAmt + "")
                    .setColor(0xFF404040).write("/" + inCap + " mB");
        } else {
            writer.setColor(0xFF888888).write("Empty");
        }

        // KF progress
        float kf    = menu.getKineticForce();
        float reqKf = menu.getRequiredKineticForce();
        HudWriter kfWriter = new HudWriter(graphics, this.font, KF_BAR_X, KF_BAR_Y + 12, 10, false);
        if (reqKf > 0f) {
            kfWriter.setColor(0xFF44AA44)
                    .write(String.format("%.1f", kf))
                    .setColor(0xFF404040)
                    .write("/")
                    .setColor(0xFF226622)
                    .write(String.format("%.1f KF", reqKf));
        } else {
            kfWriter.setColor(0xFF888888).write("No recipe");
        }

        // Output fluid (right side)
        int outAmt = menu.getOutputFluidAmount();
        int outCap = menu.getOutputFluidCapacity();
        HudWriter outWriter = new HudWriter(graphics, this.font, OUT_TANK_X + OUT_TANK_W + 2, 20, 10, false);
        if (outAmt > 0) {
            outWriter.setColor(0xFFFF8800).write(menu.getOutputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFFFF8800).write(outAmt + "")
                    .setColor(0xFF404040).write("/" + outCap + " mB");
        } else {
            outWriter.setColor(0xFF888888).write("Empty");
        }
    }
}
