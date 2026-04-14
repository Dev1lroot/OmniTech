package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a star system — deserialized directly from space_map.json. */
public class StarSystem {
    public String id;
    public String texture;
    /** Orbit radius in scene units at zoom=1.0 when shown inside a galaxy view. */
    public int orbital_radius = 180;
    /**
     * Angular-speed multiplier relative to {@code ORBIT_SPEED} in the GUI.
     * 0 (default) means the screen falls back to the Kepler formula.
     */
    public float orbital_speed = 0f;
    /**
     * Physical diameter of the star relative to Earth (Earth = 1.0, Sol ≈ 109).
     * Used by the sky renderer to scale the star's apparent disc size.
     * 0 means "use Sol as default".
     */
    public float size = 0f;
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
