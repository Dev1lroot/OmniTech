/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Reads every {@code data/omnitech/fluid/<name>.json} from the mod JAR at startup
 * and registers a fluid into {@link OmniTechFluids} for each one.
 *
 * <p>JSON schema (all fields optional, defaults shown):
 * <pre>{@code
 * {
 *   "density":     1000,   // kg/m³, negative = lighter than air
 *   "viscosity":   1000,
 *   "temperature":  300,   // Kelvin
 *   "light_level":   0,    // 0–15
 *   "min_temp":    -273,   // °C
 *   "max_temp":   10000,   // °C
 *   "min_pressure":   0,   // kPa
 *   "max_pressure": 100000,
 *   "world_placeable": false, // true = also registers a LiquidBlock so this fluid
 *                             // can be generated/placed directly in the world
 *
 *   "phase_diagram": {
 *     "melting_point":       0,      // normal melting point at 101 kPa (°C)
 *     "boiling_point":     100,      // normal boiling point at 101 kPa (°C)
 *     "boiling_slope":      51,      // d(T_boil)/d(ln P), °C per unit
 *     "critical_temp":     374,      // critical point temperature (°C)
 *     "critical_pressure": 22064,    // critical point pressure (kPa)
 *     "triple_point_temp":    0,     // triple point temperature (°C)
 *     "triple_point_pressure": 1,    // triple point pressure (kPa)
 *     "plasma_temp":       -1,       // plasma onset °C; -1 = none
 *     "has_solid":         true
 *   }
 * }
 * }</pre>
 */
public class FluidLoader {
    private static final Gson GSON = new Gson();
    static final String FLUID_DATA_PATH = "data/omnitech/fluid";

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        contents.visitContent(FLUID_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(FLUID_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return;

            String name = remainder.substring(0, remainder.length() - 5);

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                int density     = json.has("density")     ? json.get("density").getAsInt()     : 1000;
                int viscosity   = json.has("viscosity")   ? json.get("viscosity").getAsInt()   : 1000;
                int temperature = json.has("temperature") ? json.get("temperature").getAsInt() : 300;
                int lightLevel  = json.has("light_level") ? json.get("light_level").getAsInt() : 0;

                int   minTemp       = json.has("min_temp")        ? json.get("min_temp").getAsInt()        : -273;
                int   maxTemp       = json.has("max_temp")        ? json.get("max_temp").getAsInt()        : 10_000;
                int   minPressure   = json.has("min_pressure")    ? json.get("min_pressure").getAsInt()    : 0;
                int   maxPressure   = json.has("max_pressure")    ? json.get("max_pressure").getAsInt()    : 100_000;
                float neutronSlowing = json.has("neutron_slowing") ? json.get("neutron_slowing").getAsFloat() : 0.0f;
                boolean worldPlaceable = json.has("world_placeable") && json.get("world_placeable").getAsBoolean();

                FluidPhysicsRegistry.PhaseDiagram phaseDiagram = null;
                if (json.has("phase_diagram")) {
                    JsonObject pd = json.getAsJsonObject("phase_diagram");
                    int   meltingPoint     = pd.has("melting_point")       ? pd.get("melting_point").getAsInt()       : -273;
                    int   boilingPoint     = pd.has("boiling_point")       ? pd.get("boiling_point").getAsInt()       : 100;
                    float boilingSlope     = pd.has("boiling_slope")       ? pd.get("boiling_slope").getAsFloat()     : 0f;
                    int   criticalTemp     = pd.has("critical_temp")       ? pd.get("critical_temp").getAsInt()       : boilingPoint + 1000;
                    int   criticalPressure = pd.has("critical_pressure")   ? pd.get("critical_pressure").getAsInt()   : 100_000;
                    int   tripleTemp       = pd.has("triple_point_temp")   ? pd.get("triple_point_temp").getAsInt()   : meltingPoint;
                    int   triplePressure   = pd.has("triple_point_pressure")? pd.get("triple_point_pressure").getAsInt(): 1;
                    int   plasmaTemp       = pd.has("plasma_temp")         ? pd.get("plasma_temp").getAsInt()         : -1;
                    boolean hasSolid       = !pd.has("has_solid") || pd.get("has_solid").getAsBoolean();

                    phaseDiagram = new FluidPhysicsRegistry.PhaseDiagram(
                            meltingPoint, boilingPoint, boilingSlope,
                            criticalTemp, criticalPressure,
                            tripleTemp, triplePressure,
                            plasmaTemp, hasSolid);
                }

                FluidType.Properties props = FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(temperature)
                        .lightLevel(lightLevel);

                OmniTechFluids.registerFluid(name, props, worldPlaceable);
                FluidPhysicsRegistry.register(name,
                        new FluidPhysicsRegistry.FluidPhysics(minTemp, maxTemp, minPressure, maxPressure, phaseDiagram, neutronSlowing));

            } catch (Exception e) {
                OmniTech.LOGGER.error("[FluidLoader] Failed to parse fluid '{}': {}", name, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[FluidLoader] {} fluids registered from JSON", OmniTechFluids.all().size());

        registerVanillaPhysics();
    }

    /**
     * Registers physics data for vanilla Minecraft fluids so that machines,
     * the phase-diagram tooltip, and the reactor treat them correctly.
     */
    private static void registerVanillaPhysics() {
        // Shared water phase diagram (identical to distilled_water)
        FluidPhysicsRegistry.PhaseDiagram waterDiagram = new FluidPhysicsRegistry.PhaseDiagram(
                0,    // melting point °C
                100,  // boiling point °C
                51f,  // boiling slope
                374,  // critical temp °C
                22064,// critical pressure kPa
                0,    // triple point temp °C
                1,    // triple point pressure kPa
                -1,   // no plasma
                true  // has solid
        );

        // Regular water: same phase as pure water; ~8 % lower neutron moderation than
        // distilled water (dissolved minerals increase neutron absorption slightly).
        FluidPhysicsRegistry.registerExternal("minecraft", "water",
                new FluidPhysicsRegistry.FluidPhysics(0, 374, 0, 22_064, waterDiagram, 0.92f));

        // Lava: liquid basalt at ~1000–1200 °C; heavy Si/Al/Fe atoms are poor moderators.
        FluidPhysicsRegistry.registerExternal("minecraft", "lava",
                new FluidPhysicsRegistry.FluidPhysics(700, 10_000, 0, 100_000,
                        new FluidPhysicsRegistry.PhaseDiagram(
                                700, 2000, 180f, 4000, 100_000, 700, 1, -1, true),
                        0.02f));

        OmniTech.LOGGER.info("[FluidLoader] Vanilla fluid physics registered (water, lava)");
    }
}
