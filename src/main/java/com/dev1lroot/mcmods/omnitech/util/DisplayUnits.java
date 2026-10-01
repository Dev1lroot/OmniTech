/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Player-selected display units ("Units" client config, {@code omnitech-units-client.toml}).
 *
 * <p>Every machine GUI, tooltip and HUD formats its readings through this class, so the stored
 * values stay in the internal units (°C, mB, kPa, W / J, µSv/h) and only the text changes.
 * The config is client-side; where it is not loaded (a dedicated server building a chat
 * message) the defaults are used.
 *
 * <h3>The less orthodox units</h3>
 * <ul>
 *   <li><b>Glazed donuts per bald eagle</b> — how many glazed donuts (190 kcal ≈ 795 kJ) of heat
 *       it takes to warm a bald eagle (4.5 kg, tissue c ≈ 3.47 kJ/(kg·K)) up from absolute zero:
 *       1 donut/eagle ≈ 50.9 K.</li>
 *   <li><b>Micro Olympic swimming pools</b> — a 50 × 25 × 2 m pool holds 2 500 m³, so
 *       1 mB (= 1 L) = 0.4 µpool.</li>
 *   <li><b>Cheeseburgers per hour</b> — one 300 kcal cheeseburger (1.2552 MJ) per hour ≈ 348.7 W.</li>
 *   <li><b>Pickup trucks per football field</b> — a 5 000 lb pickup resting on a 360 × 160 ft
 *       field (end zones included) presses with ≈ 4.156 Pa.</li>
 *   <li><b>Bananas</b> — the banana equivalent dose, 0.1 µSv per banana eaten.</li>
 * </ul>
 */
public final class DisplayUnits {
    private DisplayUnits() {}

    static final double KELVIN_PER_DONUT_PER_EAGLE = 795.0 / (4.5 * 3.47);

    // ── Unit choices ──────────────────────────────────────────────────────────

    public enum Temperature implements TranslatableEnum {
        CELSIUS("°C", true),
        KELVIN("K", false),
        FAHRENHEIT("°F", true),
        GLAZED_DONUTS_PER_BALD_EAGLE("donuts/eagle", false);

        public final String symbol;
        /** Relative scale with a meaningful sign (°C, °F) rather than an absolute one. */
        final boolean signed;

        Temperature(String symbol, boolean signed) {
            this.symbol = symbol;
            this.signed = signed;
        }

        public double fromCelsius(double c) {
            return switch (this) {
                case CELSIUS -> c;
                case KELVIN -> c + 273.15;
                case FAHRENHEIT -> c * 9.0 / 5.0 + 32.0;
                case GLAZED_DONUTS_PER_BALD_EAGLE -> (c + 273.15) / KELVIN_PER_DONUT_PER_EAGLE;
            };
        }

        /** Extra decimals so a whole-degree reading still shows movement in coarse units. */
        int extraDecimals() {
            return this == GLAZED_DONUTS_PER_BALD_EAGLE ? 2 : 0;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("omnitech.units.temperature." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Volume implements TranslatableEnum {
        MILLIBUCKETS("mB", 1.0),
        LITERS("L", 1.0),
        US_GALLONS("gal", 1.0 / 3.785411784),
        OIL_BARRELS("bbl", 1.0 / 158.987294928),
        MICRO_OLYMPIC_POOLS("µpools", 0.4);

        public final String symbol;
        /** Display units per millibucket (1 mB = 1 L). */
        final double perMb;

        Volume(String symbol, double perMb) {
            this.symbol = symbol;
            this.perMb = perMb;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("omnitech.units.volume." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Power implements TranslatableEnum {
        WATTS("W", 1.0, "J", 1.0),
        HORSEPOWER("hp", 745.699872, "hp·h", 745.699872 * 3600.0),
        BTU_PER_HOUR("BTU/h", 1055.05585 / 3600.0, "BTU", 1055.05585),
        CHEESEBURGERS_PER_HOUR("burgers/h", 1_255_200.0 / 3600.0, "burgers", 1_255_200.0);

        public final String symbol;
        final double wattsPer;
        public final String energySymbol;
        final double joulesPer;

        Power(String symbol, double wattsPer, String energySymbol, double joulesPer) {
            this.symbol = symbol;
            this.wattsPer = wattsPer;
            this.energySymbol = energySymbol;
            this.joulesPer = joulesPer;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("omnitech.units.power." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Pressure implements TranslatableEnum {
        KILOPASCALS("kPa", 1.0),
        BAR("bar", 0.01),
        ATMOSPHERES("atm", 1.0 / 101.325),
        PSI("psi", 0.14503773773),
        MMHG("mmHg", 7.50061683),
        PICKUP_TRUCKS_PER_FOOTBALL_FIELD("trucks/field", 1000.0 / (2267.96 * 9.80665 / 5351.215));

        public final String symbol;
        /** Display units per kilopascal. */
        final double perKPa;

        Pressure(String symbol, double perKPa) {
            this.symbol = symbol;
            this.perKPa = perKPa;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("omnitech.units.pressure." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Radiation implements TranslatableEnum {
        SIEVERTS,
        REM,
        BANANAS;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("omnitech.units.radiation." + name().toLowerCase(Locale.ROOT));
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Temperature> TEMPERATURE = BUILDER
            .comment("Temperature unit shown by machines, tooltips and the space suit HUD")
            .translation("omnitech.configuration.units.temperature")
            .defineEnum("temperature", Temperature.CELSIUS);

    public static final ModConfigSpec.EnumValue<Volume> VOLUME = BUILDER
            .comment("Fluid volume unit")
            .translation("omnitech.configuration.units.volume")
            .defineEnum("volume", Volume.MILLIBUCKETS);

    public static final ModConfigSpec.EnumValue<Power> POWER = BUILDER
            .comment("Electric power unit (stored energy uses the matching energy unit)")
            .translation("omnitech.configuration.units.power")
            .defineEnum("power", Power.WATTS);

    public static final ModConfigSpec.EnumValue<Pressure> PRESSURE = BUILDER
            .comment("Pressure unit")
            .translation("omnitech.configuration.units.pressure")
            .defineEnum("pressure", Pressure.KILOPASCALS);

    public static final ModConfigSpec.EnumValue<Radiation> RADIATION = BUILDER
            .comment("Radiation dose-rate unit")
            .translation("omnitech.configuration.units.radiation")
            .defineEnum("radiation", Radiation.SIEVERTS);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static <T extends Enum<T>> T get(ModConfigSpec.EnumValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static Temperature temperatureUnit() { return get(TEMPERATURE); }
    public static Volume      volumeUnit()      { return get(VOLUME); }
    public static Power       powerUnit()       { return get(POWER); }
    public static Pressure    pressureUnit()    { return get(PRESSURE); }
    public static Radiation   radiationUnit()   { return get(RADIATION); }

    // ── Temperature (internal: °C) ────────────────────────────────────────────

    /** {@code 20 → "20 °C"} / {@code "293 K"} / {@code "68 °F"} / {@code "5.76 donuts/eagle"}. */
    public static String temperature(double celsius) {
        return temperature(celsius, 0);
    }

    public static String temperature(double celsius, int decimals) {
        return temperatureNumber(celsius, decimals) + " " + temperatureUnit().symbol;
    }

    /** {@code "20 / 1500 °C"}. */
    public static String temperatureOf(double celsius, double maxCelsius) {
        return temperatureNumber(celsius, 0) + " / " + temperature(maxCelsius);
    }

    /** Signed reading ({@code "+15.0 °C"}); absolute scales (K, donuts) get no sign. */
    public static String temperatureSigned(double celsius, int decimals) {
        Temperature unit = temperatureUnit();
        String n = temperatureNumber(celsius, decimals);
        return (unit.signed && unit.fromCelsius(celsius) >= 0 ? "+" : "") + n + " " + unit.symbol;
    }

    /** Readings that can reach millions of degrees: {@code "950 °C"}, {@code "12.5k °C"}, {@code "1.20M °C"}. */
    public static String temperatureCompact(double celsius) {
        Temperature unit = temperatureUnit();
        double v = unit.fromCelsius(celsius);
        double a = Math.abs(v);
        if (a < 1_000)     return temperature(celsius);
        if (a < 1_000_000) return String.format(Locale.ROOT, "%.1fk %s", v / 1_000, unit.symbol);
        return String.format(Locale.ROOT, "%.2fM %s", v / 1_000_000, unit.symbol);
    }

    public static String temperatureNumber(double celsius, int decimals) {
        Temperature unit = temperatureUnit();
        return fixed(unit.fromCelsius(celsius), decimals + unit.extraDecimals());
    }

    // ── Volume (internal: mB) ─────────────────────────────────────────────────

    /** {@code 250 → "250 mB"}. */
    public static String volume(double mB) {
        return volumeNumber(mB) + " " + volumeUnit().symbol;
    }

    /** {@code "250 / 8000 mB"}. */
    public static String volumeOf(double mB, double capacityMb) {
        return volumeNumber(mB) + " / " + volume(capacityMb);
    }

    public static String volumeNumber(double mB) {
        return scaled(mB * volumeUnit().perMb);
    }

    public static String volumeSymbol() {
        return volumeUnit().symbol;
    }

    // ── Pressure (internal: kPa) ──────────────────────────────────────────────

    /** {@code 101 → "101 kPa"} / {@code "1.01 bar"} / {@code "14.7 psi"}. */
    public static String pressure(double kPa) {
        return pressureNumber(kPa) + " " + pressureUnit().symbol;
    }

    /** {@code "300 / 1000 kPa"}. */
    public static String pressureOf(double kPa, double maxKPa) {
        return pressureNumber(kPa) + " / " + pressure(maxKPa);
    }

    public static String pressureNumber(double kPa) {
        return scaled(kPa * pressureUnit().perKPa);
    }

    /** Pascals with an automatic Pa / kPa / MPa prefix in the SI setting (atmosphere readouts). */
    public static String pressurePascals(double pa) {
        if (pressureUnit() != Pressure.KILOPASCALS) return pressure(pa / 1000.0);
        if (pa < 10_000)    return String.format(Locale.ROOT, "%.0f Pa", pa);
        if (pa < 1_000_000) return String.format(Locale.ROOT, "%.1f kPa", pa / 1_000.0);
        return String.format(Locale.ROOT, "%.2f MPa", pa / 1_000_000.0);
    }

    // ── Power / energy (internal: W / J) ──────────────────────────────────────

    /** Watts with an SI prefix in the chosen unit, e.g. {@code "20.0 kW"} or {@code "26.8 hp"}. */
    public static String power(double watts) {
        Power unit = powerUnit();
        return ElectricUnits.formatSi(watts / unit.wattsPer, unit.symbol);
    }

    /** Joules in the energy unit matching the chosen power unit ({@code J}, {@code hp·h}, {@code BTU}, burgers). */
    public static String energy(double joules) {
        Power unit = powerUnit();
        return ElectricUnits.formatSi(joules / unit.joulesPer, unit.energySymbol);
    }

    // ── Radiation (internal: µSv/h) ───────────────────────────────────────────

    /** Dose rate: {@code "2.50 mSv/h"}, {@code "250 mrem/h"} or {@code "25.0k bananas/h"}. */
    public static String doseRate(double microSvPerHour) {
        double usvh = Math.max(0.0, microSvPerHour);
        return switch (radiationUnit()) {
            case SIEVERTS -> {
                if (usvh < 1_000.0)     yield String.format(Locale.ROOT, "%.2f μSv/h", usvh);
                if (usvh < 1_000_000.0) yield String.format(Locale.ROOT, "%.2f mSv/h", usvh / 1_000.0);
                yield String.format(Locale.ROOT, "%.2f Sv/h", usvh / 1_000_000.0);
            }
            case REM -> usvh == 0.0 ? "0.00 rem/h" : ElectricUnits.formatSi(usvh / 10_000.0, "rem/h");
            case BANANAS -> {
                double b = usvh / 0.1;
                if (b < 10_000.0)        yield scaled(b) + " bananas/h";
                if (b < 1_000_000.0)     yield String.format(Locale.ROOT, "%.1fk bananas/h", b / 1_000.0);
                if (b < 1_000_000_000.0) yield String.format(Locale.ROOT, "%.2fM bananas/h", b / 1_000_000.0);
                yield String.format(Locale.ROOT, "%.2fB bananas/h", b / 1_000_000_000.0);
            }
        };
    }

    // ── Number formatting ─────────────────────────────────────────────────────

    private static String fixed(double v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + Math.max(0, decimals) + "f", v);
        return s.startsWith("-") && s.matches("-0(\\.0*)?") ? s.substring(1) : s;
    }

    /**
     * Whole numbers print as-is (so native mB / kPa readings are unchanged); anything else
     * is rounded to three significant digits, e.g. {@code 0.264}, {@code 13.2}, {@code 2114}.
     */
    private static String scaled(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        if (Math.abs(v) >= 100.0) return String.format(Locale.ROOT, "%.0f", v);
        return new BigDecimal(v).round(new MathContext(3)).stripTrailingZeros().toPlainString();
    }
}
