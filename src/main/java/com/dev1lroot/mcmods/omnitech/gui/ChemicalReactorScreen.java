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

/**
 * Screen for the Chemical Reactor.
 *
 * Layout (GUI-relative, 176×166):
 * <ul>
 *   <li>Left pair (x=8,  y=8, 16×52): input fluid tank 1</li>
 *   <li>Left pair (x=28, y=8, 16×52): input fluid tank 2</li>
 *   <li>Catalyst slot  (x=80, y=8)</li>
 *   <li>Progress arrow (x=68, y=35, 24px wide)</li>
 *   <li>Right (x=148, y=8, 16×52): output fluid tank</li>
 * </ul>
 */
public class ChemicalReactorScreen extends AbstractContainerScreen<ChemicalReactorMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Input tanks (left pair)
    private static final int IN1_X = 8,  IN1_Y = 8, IN_W = 16, IN_H = 52;
    private static final int IN2_X = 28, IN2_Y = 8;

    // Output tank (right)
    private static final int OUT_X = 148, OUT_Y = 8, OUT_W = 16, OUT_H = 52;

    // Progress arrow (center-bottom)
    private static final int PROG_X = 68, PROG_Y = 38, PROG_W = 24;

    public ChemicalReactorScreen(ChemicalReactorMenu menu,
            Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 72;
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

        // Background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Input fluid tank 1
        GuiUtil.renderFrame(graphics, x + IN1_X, y + IN1_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid1(),
                menu.getInputFluid1Amount(), menu.getInputFluid1Capacity(),
                x + IN1_X, y + IN1_Y, IN_W, IN_H);

        // Input fluid tank 2
        GuiUtil.renderFrame(graphics, x + IN2_X, y + IN2_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid2(),
                menu.getInputFluid2Amount(), menu.getInputFluid2Capacity(),
                x + IN2_X, y + IN2_Y, IN_W, IN_H);

        // Catalyst slot
        GuiUtil.renderSlot(graphics, x + ChemicalReactorMenu.CATALYST_SLOT_X,
                y + ChemicalReactorMenu.CATALYST_SLOT_Y);

        // Output fluid tank
        GuiUtil.renderFrame(graphics, x + OUT_X, y + OUT_Y, OUT_W, OUT_H);
        GuiUtil.renderFluidBar(graphics, menu.getOutputFluid(),
                menu.getOutputFluidAmount(), menu.getOutputFluidCapacity(),
                x + OUT_X, y + OUT_Y, OUT_W, OUT_H);

        // Progress arrow
        GuiUtil.renderProgressBar(graphics, x + PROG_X, y + PROG_Y, PROG_W,
                menu.getProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Input fluid 1 label
        int in1Amt = menu.getInputFluid1Amount();
        HudWriter w1 = new HudWriter(graphics, this.font, IN1_X, IN1_Y + IN_H + 2, 8, false);
        if (in1Amt > 0) {
            w1.setColor(0xFF4488FF).write(menu.getInputFluid1().getHoverName().getString());
        }

        // Input fluid 2 label
        int in2Amt = menu.getInputFluid2Amount();
        HudWriter w2 = new HudWriter(graphics, this.font, IN2_X, IN2_Y + IN_H + 2, 8, false);
        if (in2Amt > 0) {
            w2.setColor(0xFF44CCFF).write(menu.getInputFluid2().getHoverName().getString());
        }

        // Temperature display
        int heat    = menu.getStoredHeat();
        int reqTemp = menu.getRequiredTemperature();
        String tempText = heat + "°C";
        if (reqTemp > 0) tempText += " / " + reqTemp + "°C";
        int tempColor = (reqTemp == 0 || heat >= reqTemp) ? 0xFFFF8844 : 0xFFFF4444;
        graphics.text(this.font, tempText,
                (this.imageWidth - this.font.width(tempText)) / 2, 26, tempColor, false);

        // Output fluid label
        int outAmt = menu.getOutputFluidAmount();
        HudWriter wOut = new HudWriter(graphics, this.font, OUT_X - 2, OUT_Y + OUT_H + 2, 8, true);
        if (outAmt > 0) {
            wOut.setColor(0xFFFFAA00).write(menu.getOutputFluid().getHoverName().getString());
        }
    }
}
