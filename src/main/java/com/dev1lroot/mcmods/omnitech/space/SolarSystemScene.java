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
 */
public final class SolarSystemScene {

    /** Matches SpaceNavigationScreen.ORBIT_SPEED: one full orbit per 600 ticks at speed 1.0. */
    public static final double ORBIT_SPEED_RAD_PER_TICK = (2 * Math.PI) / 600.0;

    private SolarSystemScene() {}

    // ── Public position queries ────────────────────────────────────────────────

    /** Heliocentric 3-D position of a planet at {@code gameTime}. */
    public static Vector3f bodyPosition(CelestialBody body, long gameTime) {
        float r     = body.orbital_radius > 0 ? body.orbital_radius : 150f;
        float speed = body.orbital_speed  > 0f ? body.orbital_speed : 1.0f;
        float theta = (float)(gameTime * speed * ORBIT_SPEED_RAD_PER_TICK);
        return cartesian(r, theta, body.orbital_inclination, body.ascending_node);
    }

    /**
     * Parent-centric offset of a moon at {@code gameTime}.
     * Add to the parent planet's heliocentric position to get the moon's heliocentric position.
     */
    public static Vector3f moonOffset(CelestialBody moon, long gameTime) {
        float r     = moon.orbital_radius > 0 ? moon.orbital_radius : 40f;
        float speed = moon.orbital_speed  > 0f ? moon.orbital_speed : 2.0f;
        float theta = (float)(gameTime * speed * ORBIT_SPEED_RAD_PER_TICK);
        return cartesian(r, theta, moon.orbital_inclination, moon.ascending_node);
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
