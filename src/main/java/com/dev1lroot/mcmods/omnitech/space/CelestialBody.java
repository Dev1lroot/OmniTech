/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a planet or moon — deserialized directly from space_map.json. */
public class CelestialBody {
    public String id;
    /** "planet" or "moon" */
    public String type;
    /** Nullable — null means the dimension has not been implemented yet. */
    public String dimension;
    public String texture;
    /**
     * Fuel cost in mB.
     * @deprecated Fuel is now calculated dynamically from {@link #orbital_distance_km}
     *             and {@link #parent_distance_km} via {@code TravelDistanceCalculator}.
     *             This field is ignored at runtime but kept for JSON backwards-compatibility.
     */
    @Deprecated
    public int fuel_cost;
    /** Orbit radius in scene units at zoom=1.0 (pixels). Default 120 if omitted. */
    public int orbital_radius = 120;
    /**
     * Distance from the star in kilometres (planets only).
     * Moons set this to 0 and use {@link #parent_distance_km} instead.
     */
    public long orbital_distance_km = 0L;
    /**
     * Distance from the parent planet in kilometres (moons only).
     * Planets leave this as 0.
     */
    public long parent_distance_km = 0L;
    /**
     * Angular-speed multiplier relative to {@code ORBIT_SPEED} in the GUI.
     * 0 (default) means the screen falls back to the Kepler formula.
     */
    public float orbital_speed = 0f;
    /**
     * Orbital inclination in degrees relative to the ecliptic plane (Earth's orbital plane).
     * Used by the sky renderer to tilt the body's orbit above/below the ecliptic.
     * 0 means the body lies exactly in the ecliptic.
     */
    public float orbital_inclination = 0f;
    /**
     * Longitude of the ascending node in degrees (Ω).
     * Determines the compass direction of the orbital tilt in the ecliptic plane.
     * Used together with {@link #orbital_inclination} by the sky renderer.
     */
    public float ascending_node = 0f;
    /**
     * Physical diameter relative to Earth (Earth = 1.0).
     * Used by the sky renderer to scale apparent size alongside distance.
     * 0 means "use a sensible default".
     */
    public float size = 0f;
    /** Optional background texture shown when this planet's moons are listed. */
    public String background;

    // ── Atmospheric environment ────────────────────────────────────────────────

    /**
     * Surface temperature in °C at noon (day-time peak).
     * Actual temp = {@code base_temperature + temperature_amplitude * cos(dayPhase)}.
     */
    public double base_temperature = 20.0;

    /**
     * Half-amplitude of the day/night temperature swing in °C.
     * 0 = no cycle (vacuum, tidally-locked ocean, etc.).
     */
    public double temperature_amplitude = 0.0;

    /**
     * Atmospheric pressure in Pascals at {@link #surface_y}. 0 = vacuum; 101 325 = 1 atm.
     */
    public double surface_pressure = 101_325.0;

    /**
     * Pressure gradient in Pa per block of depth below {@link #surface_y}.
     * Positive = rises going deeper. 0 = vacuum / no atmosphere.
     */
    public double pressure_gradient = 12.0;

    /**
     * Reference Y level (sea level / surface) for pressure calculations. Default 64.
     */
    public int surface_y = 64;

    /** Child moons (empty list for moons themselves). */
    public List<CelestialBody> moons = List.of();

    public boolean hasMoons() {
        return moons != null && !moons.isEmpty();
    }

    public boolean isAvailable() {
        return dimension != null;
    }
}
