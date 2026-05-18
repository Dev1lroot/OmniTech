/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

/**
 * Calculates travel distances (in km) and fuel costs between celestial bodies
 * using the real distances stored in {@code space_map.json}.
 *
 * <h3>Distance model</h3>
 * <ul>
 *   <li>Travel between two planets = {@code |R_from - R_to|} (conjunction approximation)</li>
 *   <li>Escaping a moon adds {@code moon.parent_distance_km} to the trip</li>
 *   <li>Landing on a moon adds {@code target_moon.parent_distance_km}</li>
 * </ul>
 *
 * <h3>Fuel formula</h3>
 * Tuned so that:
 * <ul>
 *   <li>Earth → Moon  (384,400 km) ≈ 600 mB</li>
 *   <li>Earth → Europa (≈ 629.4 M km) ≈ 2150 mB</li>
 * </ul>
 * The sqrt-based formula gives diminishing marginal cost for extreme distances,
 * which keeps interstellar trips within reason while still rewarding proximity.
 */
public final class TravelDistanceCalculator {

    /** Earth–Moon distance — reference for both fuel formula and sky scaling. */
    public static final long EARTH_MOON_DIST_KM = 384_400L;

    /** Sol–Earth distance (1 AU) — reference for sun apparent scale. */
    public static final long SOL_EARTH_DIST_KM  = 149_597_871L;

    // Fuel constants derived from Earth→Moon = 600 mB, Earth→Europa ≈ 2150 mB.
    private static final double FUEL_BASE       = 560.0;
    private static final double FUEL_SQRT_RATE  = 39.0;

    private TravelDistanceCalculator() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Compute the travel distance in km between the player's current dimension
     * and a target {@link CelestialBody}.
     *
     * @param fromDimId  the current dimension ID (e.g. {@code "omnitech:moon"})
     * @param target     the destination body
     * @param spaceMap   loaded space map
     * @return estimated km, or {@code -1} if the source or target is unmapped
     */
    public static long travelDistanceKm(String fromDimId, CelestialBody target, SpaceMap spaceMap) {
        BodyEntry from = findByDimension(fromDimId, spaceMap);
        if (from == null) return -1L;

        BodyEntry to = findById(target.id, spaceMap);
        if (to == null) return -1L;

        // Viewer's distance from the star (moons inherit the parent planet's value)
        long fromStarDist = from.parentPlanet != null
                ? from.parentPlanet.orbital_distance_km
                : from.body.orbital_distance_km;

        // Cost to escape current moon orbit (0 if standing on a planet)
        long escapeCost = from.parentPlanet != null ? from.body.parent_distance_km : 0L;

        // Target's distance from the star
        long toStarDist = to.parentPlanet != null
                ? to.parentPlanet.orbital_distance_km
                : to.body.orbital_distance_km;

        // Cost to enter target moon orbit (0 if target is a planet)
        long enterCost = to.parentPlanet != null ? to.body.parent_distance_km : 0L;

        // Guard: if distances are unknown (0), we can't compute
        if (fromStarDist == 0 || toStarDist == 0) return -1L;

        long interplanetary = Math.abs(fromStarDist - toStarDist);
        return escapeCost + interplanetary + enterCost;
    }

    /**
     * Convert a travel distance in km to a fuel cost in mB.
     * Returns a fallback of 600 mB for unknown/zero distances.
     */
    public static int fuelCostMb(long distanceKm) {
        if (distanceKm <= 0) return 600;
        double ratio = (double) distanceKm / EARTH_MOON_DIST_KM;
        return (int) (FUEL_BASE + FUEL_SQRT_RATE * Math.sqrt(ratio));
    }

    /**
     * Convenience: compute fuel cost directly from a dimension ID + target body.
     * Returns {@code -1} if distances are unmapped (caller should fall back to
     * {@link CelestialBody#fuel_cost}).
     */
    public static int fuelCostMb(String fromDimId, CelestialBody target, SpaceMap spaceMap) {
        long dist = travelDistanceKm(fromDimId, target, spaceMap);
        return dist < 0 ? -1 : fuelCostMb(dist);
    }

    /**
     * Viewer's distance from the star in km, or {@code -1} if unmapped.
     * For moons, returns the parent planet's star distance.
     */
    public static long viewerStarDistanceKm(String fromDimId, SpaceMap spaceMap) {
        BodyEntry entry = findByDimension(fromDimId, spaceMap);
        if (entry == null) return -1L;
        long dist = entry.parentPlanet != null
                ? entry.parentPlanet.orbital_distance_km
                : entry.body.orbital_distance_km;
        return dist > 0 ? dist : -1L;
    }

    /**
     * Distance in km between two bodies' orbital positions around the same star.
     * Uses the conjunction approximation {@code |R_A − R_B|}.
     * Returns {@code -1} if either body has no distance data.
     */
    public static long orbitDeltaKm(CelestialBody a, CelestialBody b) {
        if (a.orbital_distance_km <= 0 || b.orbital_distance_km <= 0) return -1L;
        return Math.abs(a.orbital_distance_km - b.orbital_distance_km);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Holds a body and its parent planet (null if the body is itself a planet). */
    public record BodyEntry(CelestialBody body, CelestialBody parentPlanet) {}

    /** Find the body that owns the given dimension ID. */
    public static BodyEntry findByDimension(String dimId, SpaceMap spaceMap) {
        if (spaceMap.galaxies == null) return null;
        for (Galaxy g : spaceMap.galaxies) {
            if (g.star_systems == null) continue;
            for (StarSystem sys : g.star_systems) {
                if (sys.bodies == null) continue;
                for (CelestialBody planet : sys.bodies) {
                    if (dimId.equals(planet.dimension)) return new BodyEntry(planet, null);
                    if (planet.moons != null) {
                        for (CelestialBody moon : planet.moons) {
                            if (dimId.equals(moon.dimension)) return new BodyEntry(moon, planet);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Find a body by its map ID (not dimension). */
    public static BodyEntry findById(String bodyId, SpaceMap spaceMap) {
        if (spaceMap.galaxies == null) return null;
        for (Galaxy g : spaceMap.galaxies) {
            if (g.star_systems == null) continue;
            for (StarSystem sys : g.star_systems) {
                if (sys.bodies == null) continue;
                for (CelestialBody planet : sys.bodies) {
                    if (bodyId.equals(planet.id)) return new BodyEntry(planet, null);
                    if (planet.moons != null) {
                        for (CelestialBody moon : planet.moons) {
                            if (bodyId.equals(moon.id)) return new BodyEntry(moon, planet);
                        }
                    }
                }
            }
        }
        return null;
    }
}
