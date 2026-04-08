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
 * Screen for the Electrolysis Machine.
 *
 * Layout (GUI-relative, 176×166):
 * <ul>
 *   <li>Left  (x=8,  y=10, 16×52): input fluid tank</li>
 *   <li>Anode  slot  (x=44,  y=26): nickel rod / anode electrode</li>
 *   <li>Center (x=70, y=28, 24px): cook-progress arrow</li>
 *   <li>Cathode slot (x=108, y=26): graphite rod / cathode electrode</li>
 *   <li>Right stacked output tanks (x=148, 16×16 each, spaced 20px):
 *       anode gas (y=10), cathode gas (y=30), solution (y=50)</li>
 * </ul>
 */
public class ElectrolysisMachineScreen extends AbstractContainerScreen<ElectrolysisMachineMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Input fluid tank (left)
    private static final int IN_X = 8,  IN_Y = 10, IN_W = 16, IN_H = 52;

    // Output tanks (right column, each 16×16)
    private static final int OUT_X = 148, OUT_W = 16, OUT_H = 16;
    private static final int ANODE_OUT_Y    = 10;
    private static final int CATHODE_OUT_Y  = 30;
    private static final int SOLUTION_OUT_Y = 50;

    // Cook progress bar (center, between the two item slots)
    private static final int PROG_X = 70, PROG_Y = 28, PROG_W = 24;

    public ElectrolysisMachineScreen(ElectrolysisMachineMenu menu,
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

        // Input fluid tank
        GuiUtil.renderFrame(graphics, x + IN_X, y + IN_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid(),
                menu.getInputFluidAmount(), menu.getInputFluidCapacity(),
                x + IN_X, y + IN_Y, IN_W, IN_H);

        // Anode output tank
        GuiUtil.renderFrame(graphics, x + OUT_X, y + ANODE_OUT_Y, OUT_W, OUT_H);
        GuiUtil.renderFluidBar(graphics, menu.getAnodeFluid(),
                menu.getAnodeFluidAmount(), menu.getAnodeFluidCapacity(),
                x + OUT_X, y + ANODE_OUT_Y, OUT_W, OUT_H);

        // Cathode output tank
        GuiUtil.renderFrame(graphics, x + OUT_X, y + CATHODE_OUT_Y, OUT_W, OUT_H);
        GuiUtil.renderFluidBar(graphics, menu.getCathodeFluid(),
                menu.getCathodeFluidAmount(), menu.getCathodeFluidCapacity(),
                x + OUT_X, y + CATHODE_OUT_Y, OUT_W, OUT_H);

        // Solution output tank
        GuiUtil.renderFrame(graphics, x + OUT_X, y + SOLUTION_OUT_Y, OUT_W, OUT_H);
        GuiUtil.renderFluidBar(graphics, menu.getSolutionFluid(),
                menu.getSolutionFluidAmount(), menu.getSolutionFluidCapacity(),
                x + OUT_X, y + SOLUTION_OUT_Y, OUT_W, OUT_H);

        // Electrode item slots
        GuiUtil.renderSlot(graphics, x + ElectrolysisMachineMenu.ANODE_SLOT_X,
                y + ElectrolysisMachineMenu.ANODE_SLOT_Y);
        GuiUtil.renderSlot(graphics, x + ElectrolysisMachineMenu.CATHODE_SLOT_X,
                y + ElectrolysisMachineMenu.CATHODE_SLOT_Y);

        // Cook progress arrow
        GuiUtil.renderProgressBar(graphics, x + PROG_X, y + PROG_Y, PROG_W,
                menu.getCookProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Input fluid label (right of input tank)
        HudWriter inWriter = new HudWriter(graphics, this.font, IN_X + IN_W + 4, IN_Y, 10, false);
        int inAmt = menu.getInputFluidAmount();
        if (inAmt > 0) {
            inWriter.setColor(0xFF4488FF)
                    .write(menu.getInputFluid().getHoverName().getString())
                    .newLine()
                    .setColor(0xFF4488FF).write(inAmt + "")
                    .setColor(0xFF606060).write("/" + menu.getInputFluidCapacity() + " mB");
        } else {
            inWriter.setColor(0xFF888888).write("Empty");
        }

        // Energy readout (center-bottom of machine area)
        String euText = String.format("%.0f/%.0f EU", menu.getEnergyStored(), menu.getMaxEu());
        int euColor = menu.getEnergyStored() > 0 ? 0xFF44AAFF : 0xFF888888;
        graphics.text(this.font, euText,
                (this.imageWidth - this.font.width(euText)) / 2, 54, euColor, false);

        // Output tank labels (to the left of each output tank)
        int labX = OUT_X - 2;

        int anodeAmt = menu.getAnodeFluidAmount();
        HudWriter anodeWriter = new HudWriter(graphics, this.font, 4, ANODE_OUT_Y, 10, false);
        if (anodeAmt > 0) {
            anodeWriter.setColor(0xFFFFAA00)
                    .write(menu.getAnodeFluid().getHoverName().getString());
        }

        int cathodeAmt = menu.getCathodeFluidAmount();
        HudWriter cathodeWriter = new HudWriter(graphics, this.font, 4, CATHODE_OUT_Y, 10, false);
        if (cathodeAmt > 0) {
            cathodeWriter.setColor(0xFF44FF88)
                    .write(menu.getCathodeFluid().getHoverName().getString());
        }

        int solutionAmt = menu.getSolutionFluidAmount();
        HudWriter solWriter = new HudWriter(graphics, this.font, 4, SOLUTION_OUT_Y, 10, false);
        if (solutionAmt > 0) {
            solWriter.setColor(0xFFFF4488)
                    .write(menu.getSolutionFluid().getHoverName().getString());
        }

        // EU per recipe (small hint below energy readout)
        float euPer = menu.getEuPerRecipe();
        if (euPer > 0f) {
            String costText = String.format("%.0f EU/cycle", euPer);
            graphics.text(this.font, costText,
                    (this.imageWidth - this.font.width(costText)) / 2, 64, 0xFF888888, false);
        }
    }
}
