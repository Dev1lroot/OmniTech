package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * GUI screen for the Fractional Distiller multiblock.
 *
 * <p>Layout (176 × 166 base):
 * <ul>
 *   <li>Left side — input fluid tank.</li>
 *   <li>Centre — temperature readout + process bar.</li>
 *   <li>Right column — one output tank per structure segment (up to 4),
 *       stacked vertically.</li>
 * </ul>
 */
public class FractionalDistillerScreen extends AbstractContainerScreen<FractionalDistillerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Input tank (left side)
    private static final int IN_X = 8,  IN_Y = 17, IN_W = 16, IN_H = 52;

    // Output tanks column (right side) — one per structure segment
    private static final int OUT_X    = 152;
    private static final int OUT_Y0   = 10; // top of first output slot
    private static final int OUT_W    = 16;
    private static final int OUT_H    = 14; // height per slot (shrinks as height grows)
    private static final int OUT_GAP  = 2;  // gap between output tanks

    // Process bar (centre bottom)
    private static final int PROC_X = 52, PROC_Y = 50, PROC_W = 72;

    public FractionalDistillerScreen(FractionalDistillerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float pt) {
        super.extractBackground(graphics, mouseX, mouseY, pt);
        int x = this.leftPos, y = this.topPos;

        // GUI background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Input tank
        GuiUtil.renderFrame(graphics, x + IN_X, y + IN_Y, IN_W, IN_H);
        GuiUtil.renderFluidBar(graphics, menu.getInputFluid(), menu.getInputFluidAmount(),
                FractionalDistillerBlockEntity.INPUT_TANK_CAPACITY,
                x + IN_X, y + IN_Y, IN_W, IN_H);

        // Output tanks — one per segment
        int height = Math.max(1, Math.min(menu.getStructureHeight(),
                FractionalDistillerBlockEntity.MAX_HEIGHT));
        int slotH  = computeSlotHeight(height);

        for (int i = 0; i < height; i++) {
            int tY = y + OUT_Y0 + i * (slotH + OUT_GAP);
            FluidStack fluid = menu.getOutputFluid(i);
            GuiUtil.renderFrame(graphics, x + OUT_X, tY, OUT_W, slotH);
            GuiUtil.renderFluidBar(graphics, fluid, fluid.getAmount(),
                    FractionalDistillerBlockEntity.OUTPUT_TANK_CAPACITY,
                    x + OUT_X, tY, OUT_W, slotH);
        }

        // Process bar
        GuiUtil.renderProgressBar(graphics, x + PROC_X, y + PROC_Y, PROC_W,
                menu.getProcessProgressScaled());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // ── Input fluid label ─────────────────────────────────────────────────
        HudWriter inW = new HudWriter(graphics, font, IN_X + IN_W + 2, 20, 10, false);
        int inAmt = menu.getInputFluidAmount();
        if (inAmt > 0) {
            inW.setColor(0xFF4488FF).write(menu.getInputFluid().getHoverName().getString())
               .newLine()
               .setColor(0xFF4488FF).write(inAmt + "")
               .setColor(0xFF606060).write("/" + FractionalDistillerBlockEntity.INPUT_TANK_CAPACITY + " mB");
        } else {
            inW.setColor(0xFF888888).write("Empty");
        }

        // ── Temperature display ───────────────────────────────────────────────
        int stored   = menu.getStoredHeat();
        int reqTemp  = menu.getRequiredTemp();

        int heatColor = tempColor(stored);
        String tempStr = stored + " \u00b0C";
        int textW = font.width(tempStr);
        new HudWriter(graphics, font, (imageWidth - textW) / 2, 22, 10, false)
                .setColor(heatColor).write(tempStr);

        // Required threshold
        if (reqTemp != 0) {
            boolean met = reqTemp >= 0 ? stored >= reqTemp : stored <= reqTemp;
            String reqStr = (reqTemp >= 0 ? "need \u2265 " : "need \u2264 ") + reqTemp + " \u00b0C";
            int reqW = font.width(reqStr);
            new HudWriter(graphics, font, (imageWidth - reqW) / 2, 32, 10, false)
                    .setColor(met ? 0xFF44AA44 : 0xFFFF2200).write(reqStr);
        }

        // ── Process bar label ─────────────────────────────────────────────────
        HudWriter procW = new HudWriter(graphics, font, PROC_X, PROC_Y + 10, 10, false);
        if (menu.getProcessTotalTime() > 0 && menu.getStructureHeight() > 0) {
            float pct = menu.getProcessProgressScaled();
            if (pct > 0) {
                procW.setColor(0xFF44AA44).write(String.format("%.0f%%", pct))
                     .setColor(0xFF606060).write(" processing");
            } else {
                procW.setColor(0xFF888888).write("waiting...");
            }
        } else {
            procW.setColor(0xFF888888).write("no recipe");
        }

        // ── Output fluid labels (right column) ────────────────────────────────
        int height = Math.max(1, Math.min(menu.getStructureHeight(),
                FractionalDistillerBlockEntity.MAX_HEIGHT));
        int slotH  = computeSlotHeight(height);

        for (int i = 0; i < height; i++) {
            FluidStack fluid = menu.getOutputFluid(i);
            int tY = OUT_Y0 + i * (slotH + OUT_GAP);
            HudWriter outW = new HudWriter(graphics, font, OUT_X - 2, tY, 10, true);
            if (!fluid.isEmpty()) {
                outW.setColor(0xFFFF8800).write(fluid.getHoverName().getString())
                    .newLine()
                    .setColor(0xFFFF8800).write(fluid.getAmount() + "")
                    .setColor(0xFF606060).write(" mB");
            } else {
                outW.setColor(0xFF888888).write("slot " + (i + 1));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Distribute the available right-column height equally among segments. */
    private static int computeSlotHeight(int count) {
        // Total usable height ≈ 60px, shared among count slots with OUT_GAP gaps
        int totalGap = (count - 1) * OUT_GAP;
        return Math.max(8, (60 - totalGap) / count);
    }

    private static int tempColor(int t) {
        if (t <= 0)  return 0xFF44AAFF; // cold — blue
        if (t <= 50) return 0xFF888888; // ambient — grey
        if (t <= 200) return 0xFFFFAA00; // warm — orange
        return 0xFFFF2200;              // hot — red
    }
}
