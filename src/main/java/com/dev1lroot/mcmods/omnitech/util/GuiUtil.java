/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.tooltip.PhaseDiagramTooltipData;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        // A mixture is drawn as stacked bands, one per component, not as a single phase sprite.
        if (SolutionFluids.isMixture(fluidStack)) {
            renderMixtureBar(graphics, fluidStack, amount, capacity, x, y, width, height);
            return;
        }

        // ── Phase from T/P ────────────────────────────────────────────────────
        var physics = FluidPhysicsRegistry.get(fluidStack.getFluid());
        Integer tempBox     = fluidStack.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        Integer pressureBox = fluidStack.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        int tempC       = tempBox     != null ? tempBox     : 20;
        int pressureKPa = pressureBox != null ? pressureBox : 101;
        FluidPhase phase = FluidPhaseUtil.getPhase(tempC, pressureKPa, physics.phaseDiagram());

        // ── Sprite selection ──────────────────────────────────────────────────
        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        FluidModel fluidModel = modelSet.get(fluidStack.getFluid().defaultFluidState());
        final TextureAtlasSprite sprite;
        if (phase != null) {
            Identifier phaseId = Identifier.fromNamespaceAndPath(OmniTech.MODID,
                    "block/fluid/phase/" + phase.phaseKey() + "_still");
            sprite = ((TextureAtlas) Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS))
                    .getSprite(phaseId);
        } else {
            sprite = fluidModel.stillMaterial().sprite();
        }

        // ── Tint colour ───────────────────────────────────────────────────────
        int tint = (fluidModel.fluidTintSource() != null)
                ? fluidModel.fluidTintSource().colorAsStack(fluidStack)
                : -1;
        int r = ARGB.red(tint);
        int g = ARGB.green(tint);
        int b = ARGB.blue(tint);

        // ── Geometry and alpha driven by phase ────────────────────────────────
        boolean gasLike = phase == FluidPhase.VAPOUR || phase == FluidPhase.GAS
                || phase == FluidPhase.SUPERCRITICAL || phase == FluidPhase.PLASMA;

        final int filledHeight;
        final int startY;
        final int alpha;
        if (gasLike) {
            filledHeight = height;                                    // gas occupies full column
            startY       = y;                                         // top-aligned
            alpha        = Math.max(1, (int)((float) amount / capacity * 204)); // opacity ∝ fill
        } else {
            filledHeight = Math.max(1, (int)((long) amount * height / capacity));
            startY       = y + (height - filledHeight);               // bottom-aligned
            alpha        = 0xFF;
        }

        int packedColor = ARGB.color(alpha, r, g, b);

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
     * Draws a mixture's fill level as stacked bands — one per component, each in that fluid's own
     * texture, in proportion to its share — so a tank holding "water + ethanol + yeast" shows all
     * three instead of one anonymous colour. Bands too thin to reach a pixel are omitted here but
     * still listed by {@link #buildFluidTooltip}.
     */
    public static void renderMixtureBar(GuiGraphicsExtractor graphics, FluidStack mixture,
            int amount, int capacity, int x, int y, int width, int height) {
        Solution solution = SolutionFluids.toSolution(mixture);
        if (solution.isEmpty()) return;

        int filled = Math.max(1, (int) ((long) amount * height / capacity));
        int[] weights = new int[solution.components().size()];
        for (int i = 0; i < weights.length; i++) weights[i] = solution.components().get(i).amount();
        int[] bandHeights = Solution.apportion(weights, filled);

        int bottom = y + height;
        for (int i = 0; i < bandHeights.length; i++) {
            if (bandHeights[i] <= 0) continue;
            Solution.Part part = solution.components().get(i);
            renderFluidBand(graphics, new FluidStack(part.fluid(), Math.max(1, part.amount())),
                    x, bottom - bandHeights[i], width, bandHeights[i]);
            bottom -= bandHeights[i];
        }
    }

    /**
     * Appends one line per component of a mixture: name, mB and share, whether it is dissolved or
     * suspended, its boiling point at the mixture's pressure, and — when the mixture is at or above
     * that point — that it is boiling right now.
     */
    public static void appendMixtureLines(List<Component> lines, FluidStack mixture) {
        Solution solution = SolutionFluids.toSolution(mixture);
        int total = solution.totalAmount();
        int temp = fluidTempOf(mixture);
        int pressure = fluidPressureOf(mixture);

        for (Solution.Part part : solution.components()) {
            int pct = total > 0 ? Math.round(100f * part.amount() / total) : 0;
            var line = Component.literal(" ")
                    .append(new FluidStack(part.fluid(), part.amount()).getHoverName().copy())
                    .append(Component.literal(": " + part.amount() + " mB (" + pct + "%)"))
                    .withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(part.dissolved() ? "  dissolved" : "  undissolved")
                    .withStyle(part.dissolved() ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            // The phase this component is in at the mixture's temperature and pressure.
            var phase = SolutionPhases.phaseOf(part, temp, pressure);
            line.append(Component.literal("  ")
                    .append(Component.translatable("omnitech.fluid.phase." + phase.phaseKey()))
                    .withStyle(phaseColor(phase)));

            var diagram = FluidPhysicsRegistry.get(part.fluid()).phaseDiagram();
            if (part.dissolved() && diagram != null) {
                int boils = FluidPhaseUtil.boilingPointAtPressure(pressure, diagram);
                line.append(Component.literal("  BP " + boils + " °C").withStyle(ChatFormatting.GRAY));
                if (temp >= boils)
                    line.append(Component.literal("  BOILING").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            lines.add(line);
        }

        // Where the volume is: only worth a line when the mixture is split over several phases.
        var fractions = SolutionPhases.fractions(solution, temp, pressure);
        if (fractions.phaseCount() > 1 && total > 0) {
            var summary = Component.literal(" ").withStyle(ChatFormatting.GRAY);
            String sep = "";
            String[][] labels = {
                    {"solid", String.valueOf(fractions.solid().totalAmount())},
                    {"liquid", String.valueOf(fractions.liquid().totalAmount())},
                    {"vapour", String.valueOf(fractions.vapour().totalAmount())},
                    {"gas", String.valueOf(fractions.gas().totalAmount())}};
            for (String[] l : labels) {
                int mb = Integer.parseInt(l[1]);
                if (mb <= 0) continue;
                summary.append(Component.literal(sep));
                summary.append(Component.translatable("omnitech.fluid.phase." + l[0]));
                summary.append(Component.literal(" " + Math.round(100f * mb / total) + "%"));
                sep = " · ";
            }
            lines.add(summary);
        }
    }

    private static ChatFormatting phaseColor(com.dev1lroot.mcmods.omnitech.FluidPhase phase) {
        return switch (phase) {
            case SOLID -> ChatFormatting.WHITE;
            case LIQUID -> ChatFormatting.BLUE;
            case VAPOUR -> ChatFormatting.YELLOW;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static int fluidTempOf(FluidStack stack) {
        Integer t = stack.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

    private static int fluidPressureOf(FluidStack stack) {
        Integer p = stack.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : 101;
    }

    /**
     * Fills the rectangle with the fluid's own still texture (tiled), ignoring phase and fill
     * level. {@link #renderFluidBar} always uses the generic per-phase sprite, which is right for a
     * single-fluid tank but would make every band of a multi-fluid mixture look the same — this is
     * for callers that stack several fluids and need each one to be told apart.
     */
    public static void renderFluidBand(GuiGraphicsExtractor graphics, FluidStack fluidStack,
            int x, int y, int width, int height) {
        if (fluidStack.isEmpty() || width <= 0 || height <= 0) return;

        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        FluidModel fluidModel = modelSet.get(fluidStack.getFluid().defaultFluidState());
        TextureAtlasSprite sprite = fluidModel.stillMaterial().sprite();

        graphics.enableScissor(x, y, x + width, y + height);
        for (int drawX = 0; drawX < width; drawX += 16) {
            for (int drawY = 0; drawY < height; drawY += 16) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                        x + drawX, y + drawY, 16, 16, -1);
            }
        }
        graphics.disableScissor();
    }

    /**
     * Builds a standard fluid tooltip: name, "amount / capacity mB", and — when the
     * fluid carries a {@link OmniTechDataComponents#FLUID_TEMPERATURE} component above
     * ambient — a colour-coded temperature line.  Callers may append additional lines
     * before passing the list to {@code GuiGraphicsExtractor.setTooltipForNextFrame}.
     */
    public static List<Component> buildFluidTooltip(FluidStack fluid, int amount, int capacity) {
        List<Component> lines = new ArrayList<>();
        if (fluid.isEmpty() || amount <= 0) {
            lines.add(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
            if (capacity > 0)
                lines.add(Component.literal("0 / " + capacity + " mB")
                        .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            Component phaseSuffix = FluidPhaseUtil.getPhaseLabelComponent(fluid);
            net.minecraft.network.chat.MutableComponent nameLine = fluid.getHoverName().copy();
            if (phaseSuffix != null) {
                nameLine = nameLine
                        .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                        .append(phaseSuffix.copy().withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(nameLine);
            lines.add(Component.literal(amount + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
            Integer tempBox = fluid.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            int temp = tempBox != null ? tempBox : 20;
            ChatFormatting tempFmt = temp >= 250 ? ChatFormatting.RED
                                   : temp >= 100 ? ChatFormatting.GOLD
                                   : temp >   20 ? ChatFormatting.YELLOW
                                   :               ChatFormatting.GRAY;
            lines.add(Component.literal(temp + " °C").withStyle(tempFmt));
            Integer pressureBox = fluid.get(OmniTechDataComponents.FLUID_PRESSURE.get());
            int pressure = pressureBox != null ? pressureBox : 101;
            ChatFormatting pressFmt = pressure >= 2000 ? ChatFormatting.RED
                                    : pressure >= 500  ? ChatFormatting.GOLD
                                    : pressure >  101  ? ChatFormatting.YELLOW
                                    :                    ChatFormatting.GRAY;
            lines.add(Component.literal(pressure + " kPa").withStyle(pressFmt));
            if (SolutionFluids.isMixture(fluid)) appendMixtureLines(lines, fluid);

            // Molecular formula / SMILES / shift-structure hint, for a chemical_compound stack
            // (per-stack SMILES component) or any statically-registered fluid that opted in via
            // "smiles" in its JSON (ethanol, acetone, …) — see FluidChemistryRegistry.
            String smiles = fluid.get(OmniTechDataComponents.SMILES.get());
            if (smiles == null) smiles = com.dev1lroot.mcmods.omnitech.FluidChemistryRegistry.get(fluid.getFluid());
            if (smiles != null) ChemistryTooltipUtil.appendLines(smiles, lines::add);
        }
        return lines;
    }

    /**
     * Returns a {@link PhaseDiagramTooltipData} wrapped in an Optional for use
     * as the {@code Optional<TooltipComponent>} argument of
     * {@code setTooltipForNextFrame}.  Returns {@link Optional#empty()} when the
     * fluid is empty or has no phase diagram registered.
     */
    public static Optional<TooltipComponent> buildPhaseDiagramComponent(FluidStack fluid) {
        if (fluid.isEmpty()) return Optional.empty();
        var diagram = FluidPhysicsRegistry.get(fluid.getFluid()).phaseDiagram();
        if (diagram == null) return Optional.empty();
        Integer tempBox     = fluid.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        Integer pressureBox = fluid.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        int temp     = tempBox     != null ? tempBox     : 20;
        int pressure = pressureBox != null ? pressureBox : 101;
        return Optional.of(new PhaseDiagramTooltipData(diagram, temp, pressure));
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