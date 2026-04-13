package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a galaxy — deserialized directly from space_map.json. */
public class Galaxy {
    public String id;
    public String name;
    public String texture;
    /** Orbit radius in scene units at zoom=1.0 when shown in the galaxy selection view. */
    public int orbital_radius = 220;
    public List<StarSystem> star_systems = List.of();

    /** Optional background texture shown when navigating inside this galaxy's system list. */
    public String background;

    /** Returns the star system containing the given dimension, or null. */
    public StarSystem findSystemForDimension(String dimensionId) {
        if (star_systems == null) return null;
        for (StarSystem sys : star_systems) {
            if (sys.containsDimension(dimensionId)) return sys;
        }
        return null;
    }
}
