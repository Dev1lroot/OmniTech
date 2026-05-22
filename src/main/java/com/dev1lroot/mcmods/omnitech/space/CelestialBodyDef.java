/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

/** Flat POJO deserialized from a single file in assets/omnitech/celestial_bodies/. */
public class CelestialBodyDef {
    /** Unique identifier used as lang key base and as a parent reference target. */
    public String uuid;
    /** UUID of the parent body, or "root" for galaxies. */
    public String parent;
    /** "galaxy" | "star" | "planet" | "moon" */
    public String type;
    public String texture;
    public String background;
    /** Nullable — null means no playable dimension yet. */
    public String dimension;
    public int orbital_radius = 120;
    public float orbital_speed = 0f;
    public float size = 0f;
    public long orbital_distance_km = 0L;
    public long parent_distance_km = 0L;
    public float orbital_inclination = 0f;
    public float ascending_node = 0f;
    public double base_temperature = 20.0;
    public double temperature_amplitude = 0.0;
    public double surface_pressure = 101_325.0;
    public double pressure_gradient = 12.0;
    public int surface_y = 64;
    /** Surface gravitational acceleration in m/s². Earth standard = 9.807. */
    public double surface_gravity = 9.807;
    /**
     * Sidereal rotation period in game ticks (24 000 ticks = 1 Earth day).
     * Negative = retrograde. 0 = no rotation rendered.
     */
    public float day_length_ticks = 0f;
}
