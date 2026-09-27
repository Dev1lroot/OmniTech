/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.layout;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
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
 * {@code eu_cost_label}, {@code power_label}.<br>
 * <b>Tooltip-pass</b>: hover over any {@code fluid_tank} shows a MC-style tooltip
 * with the fluid name and fill level; hover over an electric label shows the
 * machine's electrical readings.
 *
 * <h3>Electric data keys</h3>
 * All optional; the tooltip lists only the ones a screen registers.
 * <ul>
 *   <li>{@code energy_stored}, {@code energy_max} – kJ</li>
 *   <li>{@code eu_per_cycle} – kJ per recipe</li>
 *   <li>{@code voltage} – V (terminal / line voltage)</li>
 *   <li>{@code input_voltage} – V (line voltage of the charging source, if it differs)</li>
 *   <li>{@code power_in}, {@code power_out}, {@code power_load}, {@code power_gen},
 *       {@code power_peak}, {@code power_loss}, {@code rated_power} – W</li>
 *   <li>{@code capacitance} – F</li>
 * </ul>
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
                case "power_label"   -> renderPowerLabel  (graphics, font, el, ctx, imageWidth);
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

    /**
     * If the mouse is over an {@code energy_label}, {@code eu_cost_label} or
     * {@code power_label}, schedules the electrical tooltip. Call like
     * {@link #setFluidTooltip}.
     *
     * @return {@code true} if a tooltip was queued
     */
    public static boolean setElectricTooltip(GuiGraphicsExtractor graphics, Font font,
            GuiLayout layout, GuiDataContext ctx,
            int mouseX, int mouseY, int guiLeft, int guiTop, int imageWidth) {

        for (GuiElementDef el : layout.elements) {
            switch (el.type) {
                case "energy_label", "eu_cost_label", "power_label" -> {}
                default -> { continue; }
            }
            int ex = guiLeft + (el.centered ? 0 : el.x);
            int ew = el.centered ? imageWidth : (el.w > 0 ? el.w : imageWidth - el.x);
            int ey = guiTop + el.y;
            if (mouseX < ex || mouseX >= ex + ew) continue;
            if (mouseY < ey - 1 || mouseY >= ey + 9) continue;

            graphics.setTooltipForNextFrame(font, buildElectricTooltip(ctx), Optional.empty(),
                    mouseX, mouseY);
            return true;
        }
        return false;
    }

    /**
     * Builds the electrical readout tooltip from whichever electric data keys
     * {@code ctx} provides (see class docs). Usable by hand-drawn screens too.
     */
    public static List<Component> buildElectricTooltip(GuiDataContext ctx) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.omnitech.electric.header")
                .withStyle(ChatFormatting.YELLOW));

        float volts = ctx.getFloat("voltage");

        if (ctx.has("energy_stored") && ctx.has("energy_max")) {
            float stored = ctx.getFloat("energy_stored");
            float max    = ctx.getFloat("energy_max");
            int   pct    = max > 0f ? Math.round(stored * 100f / max) : 0;
            lines.add(line("gui.omnitech.electric.stored",
                    ElectricUnits.formatEnergy(stored) + " / " + ElectricUnits.formatEnergy(max)
                            + " (" + pct + "%)"));
            lines.add(Component.literal("  " + ElectricUnits.formatKwh(stored) + " / "
                    + ElectricUnits.formatKwh(max)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ctx.has("capacitance")) {
            lines.add(line("gui.omnitech.electric.capacitance",
                    ElectricUnits.formatCapacitance(ctx.getFloat("capacitance"))));
        }
        if (ctx.has("voltage")) {
            lines.add(volts > 0f
                    ? line("gui.omnitech.electric.voltage", ElectricUnits.formatVoltage(volts))
                    : line("gui.omnitech.electric.voltage",
                            Component.translatable("gui.omnitech.electric.no_supply").getString()));
        }

        powerLine(lines, ctx, "power_peak", "gui.omnitech.electric.peak",      -1f);
        powerLine(lines, ctx, "power_gen",  "gui.omnitech.electric.generated", -1f);
        powerLine(lines, ctx, "power_in",   "gui.omnitech.electric.input",
                ctx.has("input_voltage") ? ctx.getFloat("input_voltage") : volts);
        powerLine(lines, ctx, "power_out",  "gui.omnitech.electric.output", volts);
        powerLine(lines, ctx, "power_load", "gui.omnitech.electric.load",   -1f);
        powerLine(lines, ctx, "power_loss", "gui.omnitech.electric.losses", -1f);

        if (ctx.has("rated_power") && ctx.getFloat("rated_power") > 0f) {
            float rated = ctx.getFloat("rated_power");
            lines.add(line("gui.omnitech.electric.rated", ElectricUnits.formatPower(rated)));
            if (volts > 0f) {
                lines.add(line("gui.omnitech.electric.rated_current",
                        ElectricUnits.formatCurrent(ElectricUnits.current(rated, volts))));
                lines.add(line("gui.omnitech.electric.resistance",
                        ElectricUnits.formatResistance(ElectricUnits.resistance(rated, volts))));
            }
        }
        if (ctx.has("eu_per_cycle") && ctx.getFloat("eu_per_cycle") > 0f) {
            lines.add(line("gui.omnitech.electric.per_cycle",
                    ElectricUnits.formatEnergy(ctx.getFloat("eu_per_cycle"))));
        }

        // Time to full / empty from the net power flow into the buffer
        if (ctx.has("energy_stored") && ctx.has("energy_max")) {
            double net = ctx.getFloat("power_in") - ctx.getFloat("power_out")
                    - ctx.getFloat("power_load") - ctx.getFloat("power_loss");
            float stored = ctx.getFloat("energy_stored");
            float max    = ctx.getFloat("energy_max");
            if (net > 1.0 && stored < max) {
                double s = ElectricUnits.toJoules(max - stored) / net;
                lines.add(line("gui.omnitech.electric.time_full", ElectricUnits.formatDuration(s)));
            } else if (net < -1.0 && stored > 0f) {
                double s = ElectricUnits.toJoules(stored) / -net;
                lines.add(line("gui.omnitech.electric.time_empty", ElectricUnits.formatDuration(s)));
            }
        }
        return lines;
    }

    /** "Label: P (I)" — current shown only when {@code volts} > 0. */
    private static void powerLine(List<Component> lines, GuiDataContext ctx, String key,
            String labelKey, float volts) {
        if (!ctx.has(key)) return;
        float watts = ctx.getFloat(key);
        String value = ElectricUnits.formatPower(watts);
        if (volts > 0f && watts > 0f) {
            value += "  (" + ElectricUnits.formatCurrent(ElectricUnits.current(watts, volts)) + ")";
        }
        lines.add(line(labelKey, value));
    }

    private static Component line(String labelKey, String value) {
        return Component.translatable(labelKey).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" " + value).withStyle(ChatFormatting.WHITE));
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
        String text  = ElectricUnits.formatEnergy(stored) + " / " + ElectricUnits.formatEnergy(max);
        int color    = stored > 0f ? el.getColorActive() : el.getColorInactive();
        int x        = el.centered ? (imageWidth - font.width(text)) / 2 : el.x;
        graphics.text(font, text, x, el.y, color, false);
    }

    private static void renderEuCostLabel(GuiGraphicsExtractor graphics, Font font,
            GuiElementDef el, GuiDataContext ctx, int imageWidth) {

        float cost = ctx.getFloat("eu_per_cycle");
        if (cost <= 0f) return;
        String text = ElectricUnits.formatEnergy(cost) + "/cycle";
        float rated = ctx.getFloat("rated_power");
        if (rated > 0f) text += " · " + ElectricUnits.formatPower(rated);
        int x       = el.centered ? (imageWidth - font.width(text)) / 2 : el.x;
        graphics.text(font, text, x, el.y, el.getColor(), false);
    }

    private static void renderPowerLabel(GuiGraphicsExtractor graphics, Font font,
            GuiElementDef el, GuiDataContext ctx, int imageWidth) {

        float volts = ctx.getFloat(el.voltage_source.isEmpty() ? "voltage" : el.voltage_source);
        float watts = ctx.getFloat(el.source.isEmpty() ? "power_in" : el.source);
        String text = powerLineText(el.label, volts, watts);
        int color   = volts > 0f && watts > 0f ? el.getColorActive() : el.getColorInactive();
        int x = el.centered ? (imageWidth - font.width(text)) / 2 : el.x;
        graphics.text(font, text, x, el.y, color, false);
    }

    /**
     * Formats a one-line power readout: {@code "[label] 400 V · 50.0 A · 20.0 kW"},
     * or {@code "[label] No supply"} when {@code volts} is 0.
     *
     * @param labelKey translation key of the prefix, or empty for none
     */
    public static String powerLineText(String labelKey, float volts, float watts) {
        String prefix = labelKey.isEmpty() ? "" : Component.translatable(labelKey).getString() + " ";
        if (volts <= 0f) {
            return prefix + Component.translatable("gui.omnitech.electric.no_supply").getString();
        }
        return prefix + ElectricUnits.formatVoltage(volts) + " · "
                + ElectricUnits.formatCurrent(ElectricUnits.current(watts, volts)) + " · "
                + ElectricUnits.formatPower(watts);
    }
}
