package com.dev1lroot.mcmods.omnitech.space;

import java.util.List;

/** POJO representing a galaxy — deserialized directly from space_map.json. */
public class Galaxy {
    public String id;
    public String name;
    public String texture;
    public List<StarSystem> star_systems = List.of();

    /** Returns the star system containing the given dimension, or null. */
    public StarSystem findSystemForDimension(String dimensionId) {
        if (star_systems == null) return null;
        for (StarSystem sys : star_systems) {
            if (sys.containsDimension(dimensionId)) return sys;
        }
        return null;
    }
}
