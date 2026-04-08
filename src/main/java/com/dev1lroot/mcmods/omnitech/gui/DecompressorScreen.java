package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.DecompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class DecompressorScreen extends AbstractContainerScreen<DecompressorMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    private static final int IN_TANK_X  = 8,   IN_TANK_Y  = 17, IN_TANK_W  = 16, IN_TANK_H  = 52;
    private static final int OUT_TANK_X = 152, OUT_TANK_Y = 17, OUT_TANK_W = 16, OUT_TANK_H = 52;
    private static final int PROC_BAR_X = 52,  PROC_BAR_Y = 50, PROC_BAR_W = 72;

    public DecompressorScreen(DecompressorMenu menu, Inventory playerInventory, Component title) {
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

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        GuiUtil.renderFrame(graphics, x + IN_TANK_X, y + IN_TANK_Y, IN_TANK_W, IN_TANK_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid(),
                menu.getInputFluidAmount(), menu.getInputFluidCapacity(),
                x + IN_TANK_X, y + IN_TANK_Y, IN_TANK_W, IN_TANK_H);

        GuiUtil.renderFrame(graphics, x + OUT_TANK_X, y + OUT_TANK_Y, OUT_TANK_W, OUT_TANK_H);
        GuiUtil.renderFluidBar(graphics, menu.getOutputFluid(),
                menu.getOutputFluidAmount(), menu.getOutputFluidCapacity(),
                x + OUT_TANK_X, y + OUT_TANK_Y, OUT_TANK_W, OUT_TANK_H);

        GuiUtil.renderProgressBar(graphics, x + PROC_BAR_X, y + PROC_BAR_Y, PROC_BAR_W,
                menu.getProcessProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Input fluid label
        HudWriter inWriter = new HudWriter(graphics, this.font, IN_TANK_X + IN_TANK_W + 2, 20, 10, false);
        int inAmt = menu.getInputFluidAmount();
        int inCap = menu.getInputFluidCapacity();
        if (inAmt > 0) {
            inWriter.setColor(0xFF44AAFF).write(menu.getInputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFF44AAFF).write(inAmt + "")
                    .setColor(0xFF606060).write("/" + inCap + " mB");
        } else {
            inWriter.setColor(0xFF888888).write("Empty");
        }

        // ── Cold readout (centre, prominent °C display) ───────────────────────
        int stored  = menu.getStoredCold();
        int maxCold = menu.getMaxCold();

        // Cool colours: light → deep blue as cold accumulates
        int coldColor;
        if (maxCold <= 0 || stored == 0) {
            coldColor = 0xFF888888;
        } else {
            float ratio = stored / (float) maxCold;
            if (ratio < 0.5f)       coldColor = 0xFF88CCFF; // light blue
            else if (ratio < 0.8f)  coldColor = 0xFF2288FF; // medium blue
            else                     coldColor = 0xFF0044DD; // deep blue
        }

        String coldStr = "-" + stored + " °C";
        int textW = this.font.width(coldStr);
        new HudWriter(graphics, this.font, (this.imageWidth - textW) / 2, 22, 10, false)
                .setColor(coldColor).write(coldStr);

        if (maxCold > 0) {
            String threshStr = "max -" + maxCold + " °C";
            int threshW = this.font.width(threshStr);
            HudWriter threshWriter = new HudWriter(graphics, this.font,
                    (this.imageWidth - threshW) / 2, 32, 10, false);
            if (stored >= maxCold) {
                threshWriter.setColor(0xFF2244FF).write("OVER-CHILLED");
            } else {
                threshWriter.setColor(0xFF606060).write(threshStr);
            }
        }

        // Process label
        HudWriter procWriter = new HudWriter(graphics, this.font, PROC_BAR_X, PROC_BAR_Y + 10, 10, false);
        if (maxCold > 0 && stored < maxCold) {
            procWriter.setColor(0xFF44AAFF)
                    .write(String.format("%.0f%%", menu.getProcessProgressScaled()))
                    .setColor(0xFF606060).write(" processing");
        } else if (maxCold > 0) {
            procWriter.setColor(0xFF2244FF).write("warming up...");
        } else {
            procWriter.setColor(0xFF888888).write("no recipe");
        }

        // Output fluid label
        int outAmt = menu.getOutputFluidAmount();
        int outCap = menu.getOutputFluidCapacity();
        HudWriter outWriter = new HudWriter(graphics, this.font, OUT_TANK_X - 2, 20, 10, true);
        if (outAmt > 0) {
            outWriter.setColor(0xFF88EEFF).write(menu.getOutputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFF88EEFF).write(outAmt + "")
                    .setColor(0xFF606060).write("/" + outCap + " mB");
        } else {
            outWriter.setColor(0xFF888888).write("Empty");
        }
    }
}
