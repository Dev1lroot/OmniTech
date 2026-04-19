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

public class SolvationMachineScreen extends AbstractContainerScreen<SolvationMachineMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("solvation_machine");
    private GuiDataContext dataCtx;

    public SolvationMachineScreen(SolvationMachineMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .fluid("input",  menu::getInputFluid,
                        menu::getInputFluidAmount,  menu::getInputFluidCapacity)
                .fluid("output", menu::getOutputFluid,
                        menu::getOutputFluidAmount, menu::getOutputFluidCapacity)
                .value("kf_progress", menu::getKfProgressScaled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // KF progress label (below the bar, bar at y=58)
        float kf    = menu.getKineticForce();
        float reqKf = menu.getRequiredKineticForce();
        HudWriter kfWriter = new HudWriter(graphics, this.font, 52, 70, 10, false);
        if (reqKf > 0f) {
            kfWriter.setColor(0xFF44AA44)
                    .write(String.format("%.1f", kf))
                    .setColor(0xFF404040).write("/")
                    .setColor(0xFF226622).write(String.format("%.1f KF", reqKf));
        } else {
            kfWriter.setColor(0xFF888888).write("No recipe");
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
