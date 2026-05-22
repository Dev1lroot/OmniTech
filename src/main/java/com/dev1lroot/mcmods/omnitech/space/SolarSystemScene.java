/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

import org.joml.Vector3f;

/**
 * Computes 3D Keplerian (circular-orbit) positions for the solar system.
 *
 * <p>Used by both {@link com.dev1lroot.mcmods.omnitech.client.SpaceMapSkyboxRenderer}
 * (sky projection from inside a planet) and the Orrery block entity renderer
 * (3-D miniature display). Both share identical orbital mechanics so the miniature
 * and the actual sky are always in sync.
 *
 * <p>Coordinate system: heliocentric, Y-up. The star sits at the origin. The
 * reference (ecliptic) plane is XZ. Inclinations tilt orbits toward ±Y using
 * the Rodrigues rotation formula around the ascending-node axis.
 *
 * <p><b>Scale model</b>: 3D scene radii are derived from real km distances via a
 * power-0.6 compression law that preserves proportional ordering while keeping all
 * bodies visually distinguishable. Neptune (4 498 252 000 km) maps to 500 scene units.
 * Moon orbits get a 5× boost so they remain visible at solar-system scale while still
 * appearing clearly close to their parent planet.
 * The legacy {@code orbital_radius} field is still used by the 2D SpaceNavigationScreen
 * but is <em>not</em> used here for 3D rendering.
 */
public final class SolarSystemScene {

    /** Matches SpaceNavigationScreen.ORBIT_SPEED: one full orbit per 600 ticks at speed 1.0. */
    public static final double ORBIT_SPEED_RAD_PER_TICK = (2 * Math.PI) / 600.0;

    // ── Scale constants ────────────────────────────────────────────────────────

    /** Power applied to km distances before scaling — compresses 78:1 range to ~14:1. */
    private static final double KM_EXPONENT = 0.6;

    /**
     * Scale factor: Neptune's semi-major axis (4 498 252 000 km) → 500 scene units.
     * All planet-from-star distances use this constant.
     */
    public static final double PLANET_K =
            500.0 / Math.pow(4_498_252_000.0, KM_EXPONENT);

    /**
     * Moon orbits are ~0.26% the size of Earth's orbit — at solar-system scale they
     * would be sub-pixel. A 5× boost keeps them visually attached to their parent
     * while preserving correct relative ordering among moons of the same planet.
     */
    private static final double MOON_BOOST = 5.0;
    private static final double MOON_K     = PLANET_K * MOON_BOOST;

    private SolarSystemScene() {}

    // ── Scene-radius helpers ───────────────────────────────────────────────────

    /**
     * Scene-space orbital radius of a planet/body around its star.
     * Derived from {@link CelestialBody#orbital_distance_km} via the power-0.6 law.
     * Falls back to {@link CelestialBody#orbital_radius} only when km data is absent.
     */
    public static float planetSceneRadius(CelestialBody body) {
        if (body.orbital_distance_km > 0)
            return (float)(Math.pow(body.orbital_distance_km, KM_EXPONENT) * PLANET_K);
        return body.orbital_radius > 0 ? body.orbital_radius : 150f;
    }

    /**
     * Scene-space orbital radius of a moon around its parent planet.
     * Derived from {@link CelestialBody#parent_distance_km} with a 5× boost.
     * Falls back to {@link CelestialBody#orbital_radius} only when km data is absent.
     */
    public static float moonSceneRadius(CelestialBody moon) {
        if (moon.parent_distance_km > 0)
            return (float)(Math.pow(moon.parent_distance_km, KM_EXPONENT) * MOON_K);
        return moon.orbital_radius > 0 ? moon.orbital_radius : 40f;
    }

    // ── Public position queries ────────────────────────────────────────────────

    /** Heliocentric 3-D position of a planet at {@code clockTime} (use {@code level.getOverworldClockTime()}). */
    public static Vector3f bodyPosition(CelestialBody body, long clockTime) {
        float r     = planetSceneRadius(body);
        float speed = body.orbital_speed  > 0f ? body.orbital_speed : 1.0f;
        float phase = phaseOffset(body.id);
        float theta = (float)(clockTime * speed * ORBIT_SPEED_RAD_PER_TICK) + phase;
        return cartesian(r, theta, body.orbital_inclination, body.ascending_node);
    }

    /**
     * Parent-centric offset of a moon at {@code clockTime}.
     * Add to the parent planet's heliocentric position to get the moon's heliocentric position.
     */
    public static Vector3f moonOffset(CelestialBody moon, long clockTime) {
        float r     = moonSceneRadius(moon);
        float speed = moon.orbital_speed  > 0f ? moon.orbital_speed : 2.0f;
        float phase = phaseOffset(moon.id);
        float theta = (float)(clockTime * speed * ORBIT_SPEED_RAD_PER_TICK) + phase;
        return cartesian(r, theta, moon.orbital_inclination, moon.ascending_node);
    }

    /**
     * Deterministic phase offset in radians derived from the body's ID string.
     * Ensures bodies with the same orbital speed start at different positions
     * (avoids a "planet parade" where everything lines up at angle 0).
     */
    private static float phaseOffset(String id) {
        if (id == null || id.isEmpty()) return 0f;
        return (Math.abs(id.hashCode()) % 628) / 100f; // 0 … ~2π
    }

    // ── Core orbital-elements → Cartesian ─────────────────────────────────────

    /**
     * Converts a simple circular orbit to a 3-D heliocentric position.
     *
     * <p>Algorithm (Rodrigues rotation):
     * <ol>
     *   <li>Place the body in the un-inclined ecliptic plane: {@code (r·cos θ, 0, r·sin θ)}.</li>
     *   <li>Rotate that vector by inclination {@code i} around the ascending-node axis
     *       {@code k = (cos Ω, 0, sin Ω)}. This tilts the orbit out of the ecliptic.</li>
     * </ol>
     *
     * @param r              orbital radius (scene units)
     * @param theta          true anomaly in radians (current orbital angle)
     * @param inclinationDeg orbital inclination (°) — 0 = in the ecliptic
     * @param ascendingNodeDeg longitude of ascending node (°) — sets the tilt direction
     */
    public static Vector3f cartesian(float r, float theta,
                                     float inclinationDeg, float ascendingNodeDeg) {
        float ci = (float) Math.cos(Math.toRadians(inclinationDeg));
        float si = (float) Math.sin(Math.toRadians(inclinationDeg));
        float co = (float) Math.cos(Math.toRadians(ascendingNodeDeg));
        float so = (float) Math.sin(Math.toRadians(ascendingNodeDeg));

        float x0 = r * (float) Math.cos(theta);
        float z0 = r * (float) Math.sin(theta);

        // Rodrigues: rotate (x0, 0, z0) by angle i around k = (co, 0, so)
        float kDotV  = co * x0 + so * z0;         // k · v
        float crossY = so * x0 - co * z0;          // (k × v).y

        float fx = x0 * ci + co * kDotV * (1f - ci);
        float fy =           crossY      *         si;
        float fz = z0 * ci + so * kDotV * (1f - ci);

        return new Vector3f(fx, fy, fz);
    }
}
