package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;

public class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/foundry.png");

    // Texture layout (176×166 background + sprites at U≥176):
    //   Progress arrow : UV (176, 14), 24×16 px — left-to-right fill
    //   Fluid gauge    : UV (176,  0), 12×52 px — bottom-to-top fill

    private static final int ARROW_U = 176, ARROW_V = 14;
    private static final int ARROW_W = 24,  ARROW_H = 16;
    private static final int ARROW_X = 62,  ARROW_Y = 35;

    private static final int FLUID_U = 176, FLUID_V = 0;
    private static final int FLUID_W = 12,  FLUID_H = 52;
    private static final int FLUID_X = 8,   FLUID_Y = 17;

    public FoundryScreen(FoundryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Обязательно первым делом рисуем стандартный фон (слоты рисуются здесь в супер-классе)
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;

        // 1. Рисуем фон (основное окно)
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        // 2. Стрелочка прогресса
        int arrowWidth = menu.getCookProgressWidth();
        if (arrowWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    (float) ARROW_U, (float) ARROW_V,
                    arrowWidth, ARROW_H, 256, 256);
        }

        // 3. Шкала жидкости (Исправлено)
        renderFluidBar(graphics, x + FLUID_X, y + FLUID_Y);
    }

    private void renderFluidBar(GuiGraphicsExtractor graphics, int x, int y) {
        FluidStack fluidStack = menu.getInputFluid();
        int fluidH = menu.getFluidBarHeight();

        if (!fluidStack.isEmpty() && fluidH > 0) {
            // Получаем спрайт и цвет из примера, который вы скинули
            var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
            FluidModel fluidModel = modelSet.get(fluidStack.getFluid().defaultFluidState());
            TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();

            int color = -1;
            if (fluidModel.fluidTintSource() != null) {
                color = fluidModel.fluidTintSource().colorAsStack(fluidStack);
            }

            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = 0xFF; // В GUI обычно рисуем непрозрачно

            int yOff = FLUID_H - fluidH;

            // Рисуем текстуру жидкости из атласа блоков
            // ВАЖНО: переключаем RenderPipeline, так как текстура жидкости не в нашем foundry.png
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                    x, y + yOff, FLUID_W, fluidH,
                    ARGB.color(a, r, g, b));

            // Сбрасываем цвет для последующих отрисовок
            //RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        // Current temperature
        graphics.text(this.font, currentTemp + " °C", 26, 72, getTempColor(currentTemp, requiredTemp), false);

        // Required temperature (only when a recipe is matched)
        if (requiredTemp > 0) {
            String reqStr = "Min: " + requiredTemp + " °C";
            int reqColor  = currentTemp >= requiredTemp ? 0xFF00AA00 : 0xFFAA0000;
            graphics.text(this.font, reqStr, 105, 25, reqColor, false);
        }

        // Input fluid — type (from synced BE) and amount (from ContainerData)
        FluidStack fluid = menu.getInputFluid();
        int amount   = menu.getFluidAmount();
        int capacity = menu.getFluidCapacity();
        if (!fluid.isEmpty() && amount > 0) {
            String fluidName = fluid.getFluidType().getDescription().getString();
            graphics.text(this.font, fluidName, 105, 35, 0xFFCCCCCC, false);
            graphics.text(this.font, amount + " mb", 105, 44, 0xFFFF8800, false);
            graphics.text(this.font, "/ " + capacity, 105, 53, 0xFF886644, false);
        } else {
            graphics.text(this.font, "Empty", 105, 40, 0xFFAAAAAA, false);
        }
    }

    private int getTempColor(int current, int required) {
        if (required > 0 && current >= required) return 0xFFFF6600;
        if (current > 500) return 0xFFFFAA00;
        if (current > 100) return 0xFFFFFF00;
        return 0xFFAAAAAA;
    }
}
