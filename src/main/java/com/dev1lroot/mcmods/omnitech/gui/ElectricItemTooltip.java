/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.AssemblerLoader;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.assembler.AssemblerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_capacitor.ElectricCapacitorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_charger.ElectricChargerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_furnace.ElectricFurnaceBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_transformer.PowerTransformerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.solar_panel.SolarPanelBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater.ElectricHeaterBlockEntity;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipeManager;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Item-tooltip electrical specs for electric machine blocks: production,
 * consumption, energy capacity, voltage and current.
 *
 * <p>All figures are derived from the block entities' own constants, so they
 * stay in sync with the simulation. Called from {@code OmniTechClient.onItemTooltip}.
 */
public final class ElectricItemTooltip {
    private ElectricItemTooltip() {}

    private static final ChatFormatting PRODUCTION  = ChatFormatting.GREEN;
    private static final ChatFormatting CONSUMPTION = ChatFormatting.RED;
    private static final ChatFormatting CAPACITY    = ChatFormatting.AQUA;
    private static final ChatFormatting DETAIL      = ChatFormatting.DARK_GRAY;

    /**
     * Appends the spec lines for the item with registry path {@code path}
     * (in the {@code omnitech} namespace). Does nothing for non-electric items.
     */
    public static void append(String path, Consumer<Component> out) {
        switch (path) {
            case "solar_panel" -> {
                double peak = ElectricUnits.toWatts(SolarPanelBlockEntity.EU_PER_TICK);
                production(out, ElectricUnits.formatPower(peak)
                        + " " + tr("tooltip.omnitech.electric.at_noon"));
                voltage(out, SolarPanelBlockEntity.MPP_VOLTAGE, peak);
                detail(out, tr("tooltip.omnitech.electric.solar_note"));
            }
            case "electric_engine" -> {
                double gen   = ElectricUnits.toWatts(ElectricEngineBlockEntity.EU_OUTPUT);
                double motor = ElectricUnits.toWatts(ElectricEngineBlockEntity.EU_CONSUME);
                production(out, ElectricUnits.formatPower(gen) + " "
                        + tr("tooltip.omnitech.electric.from_kf", "1.00"));
                voltage(out, ElectricEngineBlockEntity.LINE_VOLTAGE, gen);
                consumption(out, ElectricUnits.formatPower(motor) + " "
                        + tr("tooltip.omnitech.electric.motor_mode",
                                String.format(java.util.Locale.ROOT, "%.2f",
                                        ElectricEngineBlockEntity.KF_OUTPUT)));
                detail(out, tr("tooltip.omnitech.electric.efficiency", "90%"));
            }
            case "electric_capacitor" -> {
                float max = ElectricCapacitorBlockEntity.MAX_EU;
                double out1 = ElectricUnits.toWatts(ElectricCapacitorBlockEntity.DISCHARGE_RATE);
                capacity(out, max);
                production(out, tr("tooltip.omnitech.electric.up_to",
                        ElectricUnits.formatPower(out1)));
                detail(out, tr("tooltip.omnitech.electric.voltage") + " 0–"
                        + ElectricUnits.formatVoltage(ElectricCapacitorBlockEntity.RATED_VOLTAGE)
                        + " · " + ElectricUnits.formatCapacitance(ElectricCapacitorBlockEntity.CAPACITANCE));
                detail(out, tr("tooltip.omnitech.electric.runtime_1kw",
                        ElectricUnits.formatDuration(ElectricUnits.toJoules(max) / 1000.0)));
            }
            case "electric_furnace" -> {
                float perTick = ElectricFurnaceBlockEntity.EU_PER_RECIPE / ElectricFurnaceBlockEntity.COOK_TIME;
                double w = ElectricUnits.toWatts(perTick);
                consumption(out, ElectricUnits.formatPower(w) + " "
                        + tr("tooltip.omnitech.electric.while_working"));
                current(out, w);
                detail(out, tr("tooltip.omnitech.electric.per_operation",
                        ElectricUnits.formatEnergy(ElectricFurnaceBlockEntity.EU_PER_RECIPE),
                        ElectricUnits.formatDuration(
                                (double) ElectricFurnaceBlockEntity.COOK_TIME / ElectricUnits.TICKS_PER_SECOND)));
                buffer(out, ElectricFurnaceBlockEntity.MAX_EU);
            }
            case "assembler" -> {
                float perCraft = AssemblerBlockEntity.EU_PER_RECIPE;
                int minTime = Integer.MAX_VALUE, maxTime = 0;
                for (AssemblerLoader.AssemblerRecipe r : AssemblerLoader.all().values()) {
                    if (r.processingTime() <= 0) continue;
                    minTime = Math.min(minTime, r.processingTime());
                    maxTime = Math.max(maxTime, r.processingTime());
                }
                if (maxTime > 0) {
                    double hi = ElectricUnits.toWatts(perCraft / minTime);
                    double lo = ElectricUnits.toWatts(perCraft / maxTime);
                    consumption(out, range(lo, hi) + " " + tr("tooltip.omnitech.electric.while_working"));
                    current(out, hi);
                }
                detail(out, tr("tooltip.omnitech.electric.per_craft", ElectricUnits.formatEnergy(perCraft)));
                buffer(out, AssemblerBlockEntity.MAX_EU);
            }
            case "electrolysis_machine" -> {
                List<ElectrolysisRecipe> recipes = ElectrolysisRecipeManager.getAllRecipes();
                float lo = Float.MAX_VALUE, hi = 0f;
                for (ElectrolysisRecipe r : recipes) {
                    lo = Math.min(lo, r.getEnergyRequired());
                    hi = Math.max(hi, r.getEnergyRequired());
                }
                int ticks = ElectrolysisMachineBlockEntity.COOK_TIME;
                if (hi > 0f) {
                    double wHi = ElectricUnits.toWatts(hi / ticks);
                    consumption(out, range(ElectricUnits.toWatts(lo / ticks), wHi) + " "
                            + tr("tooltip.omnitech.electric.while_working"));
                    current(out, wHi);
                    detail(out, tr("tooltip.omnitech.electric.per_operation",
                            lo == hi ? ElectricUnits.formatEnergy(hi)
                                     : ElectricUnits.formatEnergy(lo) + "–" + ElectricUnits.formatEnergy(hi),
                            ElectricUnits.formatDuration((double) ticks / ElectricUnits.TICKS_PER_SECOND)));
                }
                buffer(out, ElectrolysisMachineBlockEntity.MAX_EU);
            }
            case "electric_charger" -> {
                double w = ElectricUnits.toWatts(ElectricChargerBlockEntity.CHARGE_RATE);
                consumption(out, tr("tooltip.omnitech.electric.up_to", ElectricUnits.formatPower(w)) + " "
                        + tr("tooltip.omnitech.electric.while_charging"));
                current(out, w);
                buffer(out, ElectricChargerBlockEntity.MAX_EU);
            }
            case "electric_heater" -> {
                double base = ElectricUnits.toWatts(ElectricHeaterBlockEntity.computeEuPerTick(
                        (int) ElectricHeaterBlockEntity.AMBIENT_TEMP));
                double at500  = ElectricUnits.toWatts(ElectricHeaterBlockEntity.computeEuPerTick(500));
                double at1000 = ElectricUnits.toWatts(ElectricHeaterBlockEntity.computeEuPerTick(1000));
                consumption(out, ElectricUnits.formatPower(base) + " "
                        + tr("tooltip.omnitech.electric.at_temp", "20 °C"));
                detail(out, "  " + ElectricUnits.formatPower(at500) + " "
                        + tr("tooltip.omnitech.electric.at_temp", "500 °C") + " · "
                        + ElectricUnits.formatPower(at1000) + " "
                        + tr("tooltip.omnitech.electric.at_temp", "1000 °C"));
                current(out, base);
                buffer(out, ElectricHeaterBlockEntity.MAX_EU);
            }
            case "power_transformer" -> {
                int rated = PowerTransformerBlockEntity.RATED_POWER_KW;
                out.accept(label("tooltip.omnitech.electric.rating",
                        ElectricUnits.formatPower(rated * 1000.0), CAPACITY));
                detail(out, tr("tooltip.omnitech.electric.voltage") + " "
                        + ElectricUnits.formatVoltage(PowerTransformerBlockEntity.MIN_VOLTAGE) + "–"
                        + ElectricUnits.formatVoltage(PowerTransformerBlockEntity.MAX_VOLTAGE));
                detail(out, tr("tooltip.omnitech.electric.efficiency_range",
                        pct(PowerTransformerBlockEntity.efficiency(0f)),
                        pct(PowerTransformerBlockEntity.efficiency(
                                (float) ElectricUnits.fromWatts(rated * 1000.0)))));
                detail(out, tr("tooltip.omnitech.electric.transformer_faces"));
            }
            case "electric_wire" -> detail(out, tr("tooltip.omnitech.electric.wire_note"));
            default -> {}
        }
    }

    // ── Line builders ────────────────────────────────────────────────────────

    private static void production(Consumer<Component> out, String value) {
        out.accept(label("tooltip.omnitech.electric.production", value, PRODUCTION));
    }

    private static void consumption(Consumer<Component> out, String value) {
        out.accept(label("tooltip.omnitech.electric.consumption", value, CONSUMPTION));
    }

    private static void capacity(Consumer<Component> out, float units) {
        out.accept(label("tooltip.omnitech.electric.capacity",
                ElectricUnits.formatEnergy(units) + " (" + ElectricUnits.formatKwh(units) + ")", CAPACITY));
    }

    /** Internal buffer of a consumer. */
    private static void buffer(Consumer<Component> out, float units) {
        out.accept(label("tooltip.omnitech.electric.buffer", ElectricUnits.formatEnergy(units), CAPACITY));
    }

    /** "Voltage: 400 V · 45.0 A" for a source delivering {@code watts}. */
    private static void voltage(Consumer<Component> out, float volts, double watts) {
        detail(out, tr("tooltip.omnitech.electric.voltage") + " " + ElectricUnits.formatVoltage(volts)
                + " · " + ElectricUnits.formatCurrent(ElectricUnits.current(watts, volts)));
    }

    /** Current drawn from the standard grid voltage at {@code watts}. */
    private static void current(Consumer<Component> out, double watts) {
        detail(out, tr("tooltip.omnitech.electric.current_at",
                ElectricUnits.formatCurrent(ElectricUnits.current(watts, ElectricUnits.GRID_VOLTAGE)),
                ElectricUnits.formatVoltage(ElectricUnits.GRID_VOLTAGE)));
    }

    private static void detail(Consumer<Component> out, String text) {
        out.accept(Component.literal(text).withStyle(DETAIL));
    }

    private static Component label(String key, String value, ChatFormatting color) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" " + value).withStyle(color));
    }

    private static String range(double lo, double hi) {
        return Math.abs(hi - lo) < 0.5
                ? ElectricUnits.formatPower(hi)
                : ElectricUnits.formatPower(lo) + "–" + ElectricUnits.formatPower(hi);
    }

    private static String pct(float fraction) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", fraction * 100f);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
