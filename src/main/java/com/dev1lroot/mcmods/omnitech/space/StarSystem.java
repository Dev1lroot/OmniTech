package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a star system — deserialized directly from space_map.json. */
public class StarSystem {
    public String id;
    public String name;
    public String texture;
    /** Orbit radius in scene units at zoom=1.0 when shown inside a galaxy view. */
    public int orbital_radius = 180;
    public List<CelestialBody> bodies = List.of();

    /** Optional background texture shown when browsing this system's planets. */
    public String background;

    /** True if any body (planet or its moons) in this system matches dimensionId. */
    public boolean containsDimension(String dimensionId) {
        if (bodies == null) return false;
        for (CelestialBody body : bodies) {
            if (dimensionId.equals(body.dimension)) return true;
            if (body.moons != null) {
                for (CelestialBody moon : body.moons) {
                    if (dimensionId.equals(moon.dimension)) return true;
                }
            }
        }
        return false;
    }
}
