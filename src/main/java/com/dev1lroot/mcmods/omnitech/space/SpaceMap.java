package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** Root object deserialized from assets/omnitech/space_map.json. */
public class SpaceMap {
    public List<Galaxy> galaxies = List.of();

    /**
     * Finds the galaxy and system that contain the given dimension ID.
     * Returns null if no match is found (unknown/unmapped dimension).
     */
    public Location findLocation(String dimensionId) {
        if (galaxies == null) return null;
        for (Galaxy galaxy : galaxies) {
            StarSystem system = galaxy.findSystemForDimension(dimensionId);
            if (system != null) {
                // Find the specific body (planet or moon)
                CelestialBody found = findBodyInSystem(system, dimensionId);
                return new Location(galaxy, system, found);
            }
        }
        return null;
    }

    private CelestialBody findBodyInSystem(StarSystem system, String dimensionId) {
        for (CelestialBody body : system.bodies) {
            if (dimensionId.equals(body.dimension)) return body;
            if (body.moons != null) {
                for (CelestialBody moon : body.moons) {
                    if (dimensionId.equals(moon.dimension)) return moon;
                }
            }
        }
        return null;
    }

    /**
     * Result of a location lookup — the galaxy, system, and specific body
     * that corresponds to a given dimension ID.
     */
    public record Location(Galaxy galaxy, StarSystem system, CelestialBody body) {}
}
