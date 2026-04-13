package com.dev1lroot.mcmods.omnitech.space;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

/**
 * Loads space_map.json from assets/omnitech/space_map.json via the client
 * ResourceManager.  The result is cached until invalidate() is called
 * (e.g. on resource reload).
 */
public class SpaceMapLoader {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "space_map.json");

    private static final Gson GSON = new GsonBuilder().create();

    private static SpaceMap cached;

    public static SpaceMap load(ResourceManager rm) {
        if (cached != null) return cached;
        try {
            Optional<Resource> opt = rm.getResource(LOCATION);
            if (opt.isEmpty()) {
                OmniTech.LOGGER.error("[OmniTech] space_map.json not found in assets");
                cached = empty();
                return cached;
            }
            try (Reader reader = opt.get().openAsReader()) {
                cached = GSON.fromJson(reader, SpaceMap.class);
                if (cached == null || cached.galaxies == null) {
                    cached = empty();
                }
            }
        } catch (IOException e) {
            OmniTech.LOGGER.error("[OmniTech] Failed to load space_map.json", e);
            cached = empty();
        }
        return cached;
    }

    /** Call this on resource reload so the map is re-parsed. */
    public static void invalidate() {
        cached = null;
    }

    private static SpaceMap empty() {
        SpaceMap m = new SpaceMap();
        m.galaxies = List.of();
        return m;
    }
}
