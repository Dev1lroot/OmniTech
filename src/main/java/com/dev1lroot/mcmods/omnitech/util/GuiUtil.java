package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.fluids.FluidStack;

public class GuiUtil
{
    private static final Identifier SLOT_TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/slot.png");

    /**
     * Рендерит стандартный спрайт слота.
     * @param graphics Экстрактор графики
     * @param x Координата X (самого слота, метод сам вычтет 1 для рамки)
     * @param y Координата Y (самого слота, метод сам вычтет 1 для рамки)
     */
    public static void renderSlot(GuiGraphicsExtractor graphics, int x, int y) {
        // Рендерим текстуру 18x18, начиная с x-1, y-1
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE,
                x - 1, y - 1, 0.0F, 0.0F, 18, 18, 18, 18);
    }

    /**
     * Рендерит шкалу жидкости методом замощения (tiling)
     * * @param graphics   Экстрактор графики
     * @param fluidStack Стак жидкости
     * @param amount     Текущее количество
     * @param capacity   Максимальная емкость
     * @param x          Координата X шкалы (левый верхний угол)
     * @param y          Координата Y шкалы (левый верхний угол)
     * @param width      Ширина всей шкалы
     * @param height     Полная высота всей шкалы
     */
    public static void renderFluidBar(GuiGraphicsExtractor graphics, FluidStack fluidStack, int amount, int capacity, int x, int y, int width, int height)
    {
        if (fluidStack.isEmpty() || amount <= 0 || capacity <= 0) return;

        // Рассчитываем высоту заполнения в пикселях
        int filledHeight = (int) ((long) amount * height / capacity);
        if (filledHeight <= 0) return;

        // Получаем модель и спрайт жидкости
        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        FluidModel fluidModel = modelSet.get(fluidStack.getFluid().defaultFluidState());
        TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();

        // Получаем цвет тинта
        int color = -1;
        if (fluidModel.fluidTintSource() != null) {
            color = fluidModel.fluidTintSource().colorAsStack(fluidStack);
        }

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 0xFF;
        int packedColor = ARGB.color(a, r, g, b);

        // Смещение, чтобы жидкость начиналась снизу (y + общая высота - высота заполнения)
        int startY = y + (height - filledHeight);

        // Замощение (Tiling) по 16 пикселей
        for (int drawX = 0; drawX < width; drawX += 16) {
            int currentWidth = Math.min(16, width - drawX);

            for (int drawY = 0; drawY < filledHeight; drawY += 16) {
                int currentHeight = Math.min(16, filledHeight - drawY);

                // Используем твой рабочий blitSprite
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                        x + drawX, startY + drawY,
                        currentWidth, currentHeight,
                        packedColor);
            }
        }
    }

    /**
     * Рендерит масштабируемый прямоугольник (9-slice) на основе текстуры слота.
     * Полезно для подложек под шкалы или кастомные окна.
     */
    public static void renderFrame(GuiGraphicsExtractor graphics, int ix, int iy, int iw, int ih) {
        // Углы и рамки берем из slot.png (предполагаем размер 18x18)
        // Толщина рамки обычно 1 пиксель

        int x = ix - 1;
        int y = iy - 1;
        int w = iw + 2;
        int h = ih + 2;

        int s = 1;
        int mid = 16; // Внутренняя часть (18 - 1 - 1)

        // 1. Углы (не тянутся)
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x, y, 0, 0, s, s, 18, 18); // Top-Left
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y, 17, 0, s, s, 18, 18); // Top-Right
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x, y + h - s, 0, 17, s, s, 18, 18); // Bottom-Left
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y + h - s, 17, 17, s, s, 18, 18); // Bottom-Right

        // 2. Горизонтальные грани (тянутся по ширине)
        if (w > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y, s, 0, w - s * 2, s, 18, 18); // Top
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y + h - s, s, 17, w - s * 2, s, 18, 18); // Bottom
        }

        // 3. Вертикальные грани (тянутся по высоте)
        if (h > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x, y + s, 0, s, s, h - s * 2, 18, 18); // Left
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y + s, 17, s, s, h - s * 2, 18, 18); // Right
        }

        // 4. Центр (заполнение)
        if (w > s * 2 && h > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y + s, s, s, w - s * 2, h - s * 2, 18, 18);
        }
    }
}