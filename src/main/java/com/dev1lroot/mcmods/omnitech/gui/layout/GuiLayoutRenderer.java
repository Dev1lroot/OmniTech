/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.layout;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;

/**
 * Stateless renderer for {@link GuiLayout} + {@link GuiDataContext}.
 *
 * <p>Call from the three rendering passes of {@code AbstractContainerScreen}:
 * <pre>{@code
 * // in extractBackground():
 * GuiLayoutRenderer.renderBackground(graphics, layout, ctx, leftPos, topPos, imageWidth, imageHeight);
 *
 * // in extractLabels():
 * GuiLayoutRenderer.renderLabels(graphics, font, layout, ctx, imageWidth);
 *
 * // in extractTooltip() — only when no item slot is hovered:
 * GuiLayoutRenderer.setFluidTooltip(graphics, font, layout, ctx, mouseX, mouseY, leftPos, topPos);
 * }</pre>
 *
 * <p><b>Background-pass elements</b> (non-text): {@code fluid_tank}, {@code slot},
 * {@code progressbar}.<br>
 * <b>Labels-pass elements</b> (text): {@code fluid_label}, {@code energy_label},
 * {@code eu_cost_label}.<br>
 * <b>Tooltip-pass</b>: hover over any {@code fluid_tank} shows a MC-style tooltip
 * with the fluid name and fill level.
 */
public final class GuiLayoutRenderer {

    private GuiLayoutRenderer() {}

    // ── Background pass ───────────────────────────────────────────────────────

    /**
     * Renders the background texture and all non-text elements.
     * Must be called <em>after</em> {@code super.extractBackground()} so slot
     * highlights still appear on top.
     */
    public static void renderBackground(GuiGraphicsExtractor graphics,
            GuiLayout layout, GuiDataContext ctx,
            int guiLeft, int guiTop, int imageWidth, int imageHeight,
            int mouseX, int mouseY) {

        Identifier bg = Identifier.fromNamespaceAndPath(OmniTech.MODID, layout.background);
        graphics.blit(RenderPipelines.GUI_TEXTURED, bg,
                guiLeft, guiTop, 0f, 0f, imageWidth, imageHeight, 256, 256);

        for (GuiElementDef el : layout.elements) {
            int x = guiLeft + el.x;
            int y = guiTop  + el.y;

            switch (el.type) {
                case "fluid_tank" -> {
                    GuiUtil.renderFrame(graphics, x, y, el.w, el.h);
                    GuiUtil.renderFluidBar(graphics, ctx.getFluid(el.source),
                            ctx.getFluidAmount(el.source), ctx.getFluidCapacity(el.source),
                            x, y, el.w, el.h);
                    if (mouseX >= x && mouseX < x + el.w && mouseY >= y && mouseY < y + el.h)
                        graphics.fill(x, y, x + el.w, y + el.h, 0x80FFFFFF);
                }
                case "slot" ->
                    GuiUtil.renderSlot(graphics, x, y);

                case "progressbar" ->
                    GuiUtil.renderProgressBar(graphics, x, y, el.w,
                            ctx.getFloat(el.source));
            }
        }
    }

    // ── Labels pass ───────────────────────────────────────────────────────────

    /**
     * Renders all text elements.  Coordinates are GUI-relative (no {@code leftPos}/{@code topPos}
     * offset) to match the coordinate space of {@code extractLabels()}.
     */
    public static void renderLabels(GuiGraphicsExtractor graphics, Font font,
            GuiLayout layout, GuiDataContext ctx, int imageWidth) {

        for (GuiElementDef el : layout.elements) {
            switch (el.type) {
                case "fluid_label"   -> renderFluidLabel  (graphics, font, el, ctx, imageWidth);
                case "energy_label"  -> renderEnergyLabel (graphics, font, el, ctx, imageWidth);
                case "eu_cost_label" -> renderEuCostLabel (graphics, font, el, ctx, imageWidth);
            }
        }
    }

    // ── Tooltip pass ─────────────────────────────────────────────────────────

    /**
     * If the mouse is over a {@code fluid_tank} element, schedules a MC-style
     * tooltip via {@code graphics.setTooltipForNextFrame()}.
     *
     * <p>Call from {@code extractTooltip()} <em>after</em> {@code super.extractTooltip()}
     * and only when {@code hoveredSlot == null}, so fluid and item tooltips
     * never overlap:
     * <pre>{@code
     * if (hoveredSlot == null)
     *     GuiLayoutRenderer.setFluidTooltip(graphics, font, LAYOUT, dataCtx,
     *             mouseX, mouseY, leftPos, topPos);
     * }</pre>
     *
     * @return {@code true} if a tooltip was queued
     */
    public static boolean setFluidTooltip(GuiGraphicsExtractor graphics, Font font,
            GuiLayout layout, GuiDataContext ctx,
            int mouseX, int mouseY, int guiLeft, int guiTop) {

        for (GuiElementDef el : layout.elements) {
            if (!"fluid_tank".equals(el.type)) continue;

            int ex = guiLeft + el.x;
            int ey = guiTop  + el.y;
            if (mouseX < ex || mouseX >= ex + el.w) continue;
            if (mouseY < ey || mouseY >= ey + el.h) continue;

            // Mouse is inside this tank — build tooltip
            FluidStack fluid    = ctx.getFluid(el.source);
            int        amount   = ctx.getFluidAmount(el.source);
            int        capacity = ctx.getFluidCapacity(el.source);

            List<Component> lines = GuiUtil.buildFluidTooltip(fluid, amount, capacity);

            graphics.setTooltipForNextFrame(font, lines,
                    GuiUtil.buildPhaseDiagramComponent(fluid), mouseX, mouseY);
            return true;
        }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void renderFluidLabel(GuiGraphicsExtractor graphics, Font font,
            GuiElementDef el, GuiDataContext ctx, int imageWidth) {

        int amount    = ctx.getFluidAmount(el.source);
        FluidStack fs = ctx.getFluid(el.source);
        int lh        = el.line_height > 0 ? el.line_height : 10;

        int startX = el.centered
                ? (imageWidth - font.width(fs.isEmpty() ? el.empty_text : fs.getHoverName().getString())) / 2
                : el.x;

        HudWriter writer = new HudWriter(graphics, font, startX, el.y, lh, false);

        if (!fs.isEmpty() && amount > 0) {
            writer.setColor(el.getColorFilled()).write(fs.getHoverName().getString());
            if (el.show_amount) {
                writer.newLine()
                      .setColor(el.getColorFilled()).write(String.valueOf(amount))
                      .setColor(el.getColorEmpty())
                      .write("/" + ctx.getFluidCapacity(el.source) + " mB");
            }
        } else if (!el.empty_text.isEmpty()) {
            writer.setColor(el.getColorEmpty()).write(el.empty_text);
        }
    }

    private static void renderEnergyLabel(GuiGraphicsExtractor graphics, Font font,
            GuiElementDef el, GuiDataContext ctx, int imageWidth) {

        float stored = ctx.getFloat("energy_stored");
        float max    = ctx.getFloat("energy_max");
        String text  = String.format("%.0f/%.0f EU", stored, max);
        int color    = stored > 0f ? el.getColorActive() : el.getColorInactive();
        int x        = el.centered ? (imageWidth - font.width(text)) / 2 : el.x;
        graphics.text(font, text, x, el.y, color, false);
    }

    private static void renderEuCostLabel(GuiGraphicsExtractor graphics, Font font,
            GuiElementDef el, GuiDataContext ctx, int imageWidth) {

        float cost = ctx.getFloat("eu_per_cycle");
        if (cost <= 0f) return;
        String text = String.format("%.0f EU/cycle", cost);
        int x       = el.centered ? (imageWidth - font.width(text)) / 2 : el.x;
        graphics.text(font, text, x, el.y, el.getColor(), false);
    }
}
