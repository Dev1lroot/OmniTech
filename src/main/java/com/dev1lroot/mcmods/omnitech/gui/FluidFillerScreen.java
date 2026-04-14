package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Fluid Filler machine.
 *
 * Layout (GUI-relative, 176×166):
 * <ul>
 *   <li>Left  (x=8,  y=8,  16×52): input fluid tank</li>
 *   <li>Center (x=80, y=8):  input canister slot</li>
 *   <li>Center (x=80, y=30): output canister slot</li>
 *   <li>Progress bar (x=28, y=30, 48px wide)</li>
 *   <li>Right  (x=148, y=8,  16×52): output fluid tank</li>
 * </ul>
 */
public class FluidFillerScreen extends AbstractContainerScreen<FluidFillerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    private static final int IN_X  = 8,  IN_Y  = 8,  IN_W  = 16, IN_H  = 52;
    private static final int OUT_X = 148, OUT_Y = 8, OUT_W = 16, OUT_H = 52;

    private static final int PROG_X = 28, PROG_Y = 30, PROG_W = 48;

    public FluidFillerScreen(FluidFillerMenu menu, Inventory playerInventory, Component title) {
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

        // Input fluid tank
        GuiUtil.renderFrame(graphics, x + IN_X, y + IN_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid(),
                menu.getInputFluidAmount(), menu.getInputFluidCapacity(),
                x + IN_X, y + IN_Y, IN_W, IN_H);

        // Output fluid tank
        GuiUtil.renderFrame(graphics, x + OUT_X, y + OUT_Y, OUT_W, OUT_H);
        GuiUtil.renderFluidBar(graphics, menu.getOutputFluid(),
                menu.getOutputFluidAmount(), menu.getOutputFluidCapacity(),
                x + OUT_X, y + OUT_Y, OUT_W, OUT_H);

        // Canister slots
        GuiUtil.renderSlot(graphics, x + FluidFillerMenu.IN_CANISTER_SLOT_X,
                y + FluidFillerMenu.IN_CANISTER_SLOT_Y);
        GuiUtil.renderSlot(graphics, x + FluidFillerMenu.OUT_CANISTER_SLOT_X,
                y + FluidFillerMenu.OUT_CANISTER_SLOT_Y);

        // Progress bar
        GuiUtil.renderProgressBar(graphics, x + PROG_X, y + PROG_Y, PROG_W,
                menu.getProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Input fluid label
        int inAmt = menu.getInputFluidAmount();
        if (inAmt > 0) {
            graphics.text(this.font,
                    menu.getInputFluid().getHoverName().getString() + " " + inAmt + " mB",
                    IN_X + IN_W + 2, IN_Y + 2, 0xFF4488FF, false);
        }

        // Output fluid label
        int outAmt = menu.getOutputFluidAmount();
        if (outAmt > 0) {
            String outLabel = menu.getOutputFluid().getHoverName().getString() + " " + outAmt + " mB";
            graphics.text(this.font, outLabel,
                    OUT_X - this.font.width(outLabel) - 2, OUT_Y + 2, 0xFF44FF88, false);
        }
    }
}
