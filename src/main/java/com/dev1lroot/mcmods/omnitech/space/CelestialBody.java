package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a planet or moon — deserialized directly from space_map.json. */
public class CelestialBody {
    public String id;
    public String name;
    /** "planet" or "moon" */
    public String type;
    /** Nullable — null means the dimension has not been implemented yet. */
    public String dimension;
    public String texture;
    /** Fuel cost in mB to travel here from the current location. */
    public int fuel_cost;
    /** Orbit radius in scene units at zoom=1.0 (pixels). Default 120 if omitted. */
    public int orbital_radius = 120;
    /** Child moons (empty list for moons themselves). */
    public List<CelestialBody> moons = List.of();

    public boolean hasMoons() {
        return moons != null && !moons.isEmpty();
    }

    public boolean isAvailable() {
        return dimension != null;
    }
}
