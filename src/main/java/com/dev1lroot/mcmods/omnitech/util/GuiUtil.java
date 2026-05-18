/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
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

    private static final Identifier PROGRESS_TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/progression_arrow.png");

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

        // Bottom-aligned: fluid rises from the bottom of the tank
        int startY = y + (height - filledHeight);

        // One scissor over the filled region handles all partial-tile clipping at edges.
        // Each tile is drawn full 16×16; the scissor clips anything outside the fill area.
        graphics.enableScissor(x, startY, x + width, startY + filledHeight);
        for (int drawX = 0; drawX < width; drawX += 16) {
            for (int drawY = 0; drawY < filledHeight; drawY += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                        x + drawX, startY + drawY, 16, 16, packedColor);
            }
        }
        graphics.disableScissor();
    }

    /**
     * Рендерит масштабируемый прямоугольник (9-slice) на основе текстуры слота.
     * Полезно для подложек под шкалы или кастомные окна.
     */
    public static void renderFrame(GuiGraphicsExtractor graphics, int ix, int iy, int iw, int ih) {
        // slot.png is 18×18: 1px border on every side, 16×16 interior.
        // s   = border thickness (1px)
        // mid = interior width/height in the texture (16px)
        int x   = ix - 1;
        int y   = iy - 1;
        int w   = iw + 2;
        int h   = ih + 2;
        int s   = 1;
        int mid = 16;

        // ── Corners: 1×1 source → 1×1 dest, no stretch needed ────────────────
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x,         y,         0f,  0f,  s, s, s,   s,   18, 18); // TL
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y,         17f, 0f,  s, s, s,   s,   18, 18); // TR
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x,         y + h - s, 0f,  17f, s, s, s,   s,   18, 18); // BL
        graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y + h - s, 17f, 17f, s, s, s,   s,   18, 18); // BR

        // ── Horizontal edges: 16×1 source stretched to (w-2)×1 ───────────────
        // Using 12-param blit(pipeline, tex, dx, dy, u, v, dw, dh, sw, sh, tw, th)
        // so srcW/srcH stay within the 18×18 texture regardless of frame size.
        if (w > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y,         1f, 0f,  w - s*2, s, mid, s,   18, 18); // Top
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y + h - s, 1f, 17f, w - s*2, s, mid, s,   18, 18); // Bottom
        }

        // ── Vertical edges: 1×16 source stretched to 1×(h-2) ─────────────────
        if (h > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x,         y + s, 0f,  1f, s, h - s*2, s,   mid, 18, 18); // Left
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + w - s, y + s, 17f, 1f, s, h - s*2, s,   mid, 18, 18); // Right
        }

        // ── Center fill: 16×16 source stretched to (w-2)×(h-2) ──────────────
        if (w > s * 2 && h > s * 2) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, x + s, y + s, 1f, 1f, w - s*2, h - s*2, mid, mid, 18, 18);
        }
    }

    /**
     * Рендерит прогресс-бар в виде стрелки.
     * @param graphics Экстрактор графики
     * @param x        Координата X
     * @param y        Координата Y
     * @param w        Общая ширина всей стрелки (включая рукоять и наконечник)
     * @param progress Прогресс в процентах (от 0.0 до 100.0)
     */
    public static void renderProgressBar(GuiGraphicsExtractor graphics, int x, int y, int w, float progress) {
        // ВАЖНО: tipWidth всегда 8, так как это физический размер наконечника в твоей текстуре 9x32
        int tipWidth = 8;

        // Переводим проценты (0-100) в коэффициент (0.0-1.0)
        float percent = Math.max(0, Math.min(100, progress)) / 100f;

        // Вычисляем, сколько всего пикселей по горизонтали должна занимать "активная" часть
        int activeWidth = (int) (w * percent);

        // 1. Рендерим деактивированную стрелку (фон)
        // v = 16.0f (нижняя половина атласа), limitW = w (рисуем всю ширину)
        renderArrowPart(graphics, x, y, w, 16.0f, tipWidth, w);

        // 2. Рендерим активную стрелку поверх
        // v = 0.0f (верхняя половина атласа), limitW = activeWidth (рисуем только часть прогресса)
        if (activeWidth > 0) {
            renderArrowPart(graphics, x, y, w, 0.0f, tipWidth, activeWidth);
        }
    }

    private static void renderArrowPart(GuiGraphicsExtractor graphics, int x, int y, int totalW, float v, int tipW, int limitW) {
        int handleWidth = totalW - tipW;

        // Размеры твоего PNG файла 9x32
        int texW = 9;
        int texH = 32;

        // 1. Рисуем рукоять
        int drawHandleW = Math.min(handleWidth, limitW);
        if (drawHandleW > 0) {
            // Мы берем 1 пиксель из текстуры (srcWidth = 1)
            // и растягиваем его на drawHandleW на экране
            graphics.blit(RenderPipelines.GUI_TEXTURED, PROGRESS_TEXTURE,
                    x, y,              // x, y на экране
                    0f, v,             // u, v в текстуре (начало рукояти)
                    drawHandleW, 16,   // width, height на ЭКРАНЕ
                    1, 16,             // srcWidth, srcHeight в ТЕКСТУРЕ (берем только 1 пиксель ширины)
                    texW, texH);       // полные размеры атласа
        }

        // 2. Рисуем наконечник
        int drawTipW = Math.min(tipW, limitW - handleWidth);
        if (drawTipW > 0) {
            // Начинаем со второго пикселя (u = 1f)
            // Берем из текстуры столько же пикселей, сколько рисуем на экране (srcWidth = drawTipW)
            graphics.blit(RenderPipelines.GUI_TEXTURED, PROGRESS_TEXTURE,
                    x + handleWidth, y,
                    1f, v,             // u = 1.0 (пропускаем рукоять), v
                    drawTipW, 16,      // width, height на ЭКРАНЕ
                    drawTipW, 16,      // srcWidth, srcHeight в ТЕКСТУРЕ
                    texW, texH);
        }
    }
}