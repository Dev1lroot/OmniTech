/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side utility that calculates temperature (°C) and pressure (Pa) for
 * the player's current position in any dimension.
 *
 * <p>Data comes from {@link CelestialBody} entries in {@code space_map.json}.
 * Vanilla dimensions ({@code minecraft:overworld}, {@code minecraft:the_nether},
 * {@code minecraft:the_end}) are handled by a built-in fallback table.
 *
 * <h3>Temperature formula</h3>
 * <pre>
 *   T = base_temperature + temperature_amplitude × cos( (dayTime − 6000) / 24000 × 2π )
 * </pre>
 * Peak at noon (dayTime 6 000), trough at midnight (dayTime 18 000).
 *
 * <h3>Pressure formula</h3>
 * <pre>
 *   P = max( 0,  surface_pressure  +  pressure_gradient × (surface_y − posY) )
 * </pre>
 * Positive gradient → pressure rises going deeper; 0 gradient → flat (vacuum).
 */
public final class DimensionEnvironment {

    private DimensionEnvironment() {}

    // ── Fallback table for vanilla dimensions ─────────────────────────────────

    private record Env(double baseTemp, double tempAmp, double surfacePressure,
                       double pressureGradient, int surfaceY) {}

    private static final Map<String, Env> FALLBACK = Map.of(
            "minecraft:overworld",  new Env(15,   10,  101_325,  12,     64),
            "minecraft:the_nether", new Env(300,  20,  500_000,  5_000,  64),
            "minecraft:the_end",    new Env(-270,  0,       0,      0,  64)
    );

    // ── Lazy-loaded body map: dimensionId → CelestialBody ─────────────────────

    @Nullable private static Map<String, CelestialBody> bodyCache = null;

    private static Map<String, CelestialBody> bodies() {
        if (bodyCache == null) {
            bodyCache = new HashMap<>();
            SpaceMap map = SpaceMapLoader.load(Minecraft.getInstance().getResourceManager());
            if (map != null && map.galaxies != null) {
                for (var galaxy : map.galaxies) {
                    if (galaxy.star_systems == null) continue;
                    for (var system : galaxy.star_systems) {
                        if (system.bodies == null) continue;
                        indexBodies(system.bodies);
                    }
                }
            }
        }
        return bodyCache;
    }

    private static void indexBodies(java.util.List<CelestialBody> list) {
        for (CelestialBody b : list) {
            if (b.dimension != null) bodyCache.put(b.dimension, b);
            if (b.moons != null)     indexBodies(b.moons);
        }
    }

    /** Invalidates the cache (call when resources are reloaded). */
    public static void invalidate() {
        bodyCache = null;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Current temperature in °C for the given level and block position.
     * Varies with the day/night cycle when {@code temperature_amplitude > 0}.
     */
    public static double getTemperature(Level level, BlockPos pos) {
        String dimId = level.dimension().identifier().toString();
        CelestialBody body = bodies().get(dimId);
        if (body != null) return calcTemp(body.base_temperature, body.temperature_amplitude, level);

        Env fallback = FALLBACK.get(dimId);
        if (fallback != null) return calcTemp(fallback.baseTemp(), fallback.tempAmp(), level);

        return 20.0; // unknown dimension — Earth-like default
    }

    /**
     * Current atmospheric pressure in Pascals for the given level and block position.
     * Changes with depth/altitude according to the {@code pressure_gradient} field.
     */
    public static double getPressure(Level level, BlockPos pos) {
        String dimId = level.dimension().identifier().toString();
        CelestialBody body = bodies().get(dimId);
        if (body != null)
            return calcPressure(body.surface_pressure, body.pressure_gradient, body.surface_y, pos.getY());

        Env fallback = FALLBACK.get(dimId);
        if (fallback != null)
            return calcPressure(fallback.surfacePressure(), fallback.pressureGradient(), fallback.surfaceY(), pos.getY());

        return 101_325.0;
    }

    /** Human-readable dimension name (uses the path component, title-cased). */
    public static String getDimensionName(Level level) {
        String dimId = level.dimension().identifier().toString();
        CelestialBody body = bodies().get(dimId);
        if (body != null) return formatBodyName(body.id);

        // Fallback: pretty-print the resource location path
        String path = level.dimension().identifier().getPath();
        return toTitleCase(path.replace('_', ' '));
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private static double calcTemp(double base, double amplitude, Level level) {
        if (amplitude == 0.0) return base;
        float dayTimeFraction = (float) ((level.getOverworldClockTime() % 24_000L) / 24_000.0);
        double phase = (dayTimeFraction - 0.25f) * 2 * Math.PI;
        return base + amplitude * Math.cos(phase);
    }

    private static double calcPressure(double surface, double gradient, int surfaceY, int posY) {
        return Math.max(0.0, surface + gradient * (surfaceY - posY));
    }

    private static String formatBodyName(String id) {
        return toTitleCase(id.replace('_', ' '));
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
