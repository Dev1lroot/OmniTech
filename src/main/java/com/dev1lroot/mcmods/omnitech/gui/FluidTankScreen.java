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
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidTankScreen extends AbstractContainerScreen<FluidTankMenu> {

    // Используем пустую или стандартную подложку, так как рамки рисует GuiUtil
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Параметры основного резервуара (согласно вашему расположению)
    private static final int TANK_X = 51, TANK_Y = 15, TANK_W = 16, TANK_H = 56;

    // Координаты слотов из Menu
    private static final int BUCKET_IN_X  = 27, BUCKET_IN_Y  = 17;
    private static final int BUCKET_OUT_X = 27, BUCKET_OUT_Y = 53;

    public FluidTankScreen(FluidTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        // Центрируем заголовок
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;

        // Фон GUI
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Рендерим слоты для ведер
        GuiUtil.renderSlot(graphics, x + BUCKET_IN_X, y + BUCKET_IN_Y);
        GuiUtil.renderSlot(graphics, x + BUCKET_OUT_X, y + BUCKET_OUT_Y);

        // Рендерим рамку и саму жидкость через GuiUtil
        GuiUtil.renderFrame(graphics, x + TANK_X, y + TANK_Y, TANK_W, TANK_H);

        FluidStack fluidStack = menu.getFluidStack();
        GuiUtil.renderFluidBar(graphics, fluidStack,
                menu.getStoredFluid(), menu.getMaxFluid(),
                x + TANK_X, y + TANK_Y, TANK_W, TANK_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Используем HudWriter для информативного вывода справа от бака
        HudWriter writer = new HudWriter(graphics, this.font, TANK_X + TANK_W + 8, TANK_Y + 4, 10, false);

        int stored = menu.getStoredFluid();
        int max = menu.getMaxFluid();
        FluidStack stack = menu.getFluidStack();

        if (stored > 0 && !stack.isEmpty()) {
            // Название жидкости
            writer.setColor(0xFF4488FF)
                    .write(stack.getHoverName().getString())
                    .newLine();

            // Количество
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
}