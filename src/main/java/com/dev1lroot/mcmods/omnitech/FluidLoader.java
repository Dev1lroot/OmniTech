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
 * <p>Must be called before {@link OmniTechFluids#register} so that all
 * DeferredRegister entries are queued before the RegisterEvent fires.
 *
 * <p>JSON schema (all fields optional, defaults shown):
 * <pre>{@code
 * {
 *   "density":     1000,   // kg/m³, negative = lighter than air (rises)
 *   "viscosity":   1000,   // higher = thicker, slower-moving
 *   "temperature":  300,   // Kelvin
 *   "light_level":   0    // 0–15, light emitted when placed as a block
 * }
 * }</pre>
 *
 * The registry name is taken from the filename without extension.
 * The translation key is automatically set to {@code fluid.omnitech.<name>}.
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
            // Only direct children: data/omnitech/fluid/<name>.json
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(FLUID_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return; // skip sub-directories

            String name = remainder.substring(0, remainder.length() - 5); // strip .json

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                int density     = json.has("density")     ? json.get("density").getAsInt()     : 1000;
                int viscosity   = json.has("viscosity")   ? json.get("viscosity").getAsInt()   : 1000;
                int temperature = json.has("temperature") ? json.get("temperature").getAsInt() : 300;
                int lightLevel  = json.has("light_level") ? json.get("light_level").getAsInt() : 0;

                FluidType.Properties props = FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(temperature)
                        .lightLevel(lightLevel);

                OmniTechFluids.registerFluid(name, props);
                OmniTech.LOGGER.debug("[FluidLoader] Registered: {} (density={}, viscosity={}, temp={}K)",
                        name, density, viscosity, temperature);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[FluidLoader] Failed to parse fluid '{}': {}", name, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[FluidLoader] {} fluids registered from JSON", OmniTechFluids.all().size());
    }
}
