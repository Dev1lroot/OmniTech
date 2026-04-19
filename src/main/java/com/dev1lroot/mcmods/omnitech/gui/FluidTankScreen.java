package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidTankScreen extends AbstractContainerScreen<FluidTankMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("fluid_tank");
    private GuiDataContext dataCtx;

    public FluidTankScreen(FluidTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("fluid", menu::getFluidStack, menu::getStoredFluid, menu::getMaxFluid);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Tank info to the right of the fluid bar (tank at x=51, w=16 → label at x=75)
        HudWriter writer = new HudWriter(graphics, this.font, 75, 19, 10, false);

        int stored = menu.getStoredFluid();
        int max    = menu.getMaxFluid();
        FluidStack stack = menu.getFluidStack();

        if (stored > 0 && !stack.isEmpty()) {
            writer.setColor(0xFF4488FF)
                    .write(stack.getHoverName().getString())
                    .newLine();
            writer.setColor(0xFFFFFFFF).write(stored + "")
                    .setColor(0xFF888888).write(" / " + max + " mB");
            if (stored >= max) {
                writer.newLine().setColor(0xFF44FF44).write("Status: Full");
            }
        } else {
            writer.setColor(0xFF888888).write("Empty Tank")
                    .newLine()
                    .write("0 / " + max + " mB");
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null) {
            GuiLayoutRenderer.setFluidTooltip(graphics, this.font, LAYOUT, dataCtx,
                    mouseX, mouseY, this.leftPos, this.topPos);
        }
    }
}
