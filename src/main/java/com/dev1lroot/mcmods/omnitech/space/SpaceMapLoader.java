/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.space;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * Loads celestial body definitions from assets/omnitech/celestial_bodies/*.json
 * via the client ResourceManager. Each file defines one body with a uuid and parent
 * reference. The loader reconstructs the Galaxy → StarSystem → CelestialBody hierarchy.
 * The result is cached until invalidate() is called (e.g. on resource reload).
 */
public class SpaceMapLoader {

    private static final String DIRECTORY = "celestial_bodies";
    private static final Gson GSON = new GsonBuilder().create();
    private static SpaceMap cached;

    public static SpaceMap load(ResourceManager rm) {
        if (cached != null) return cached;

        Map<Identifier, Resource> resources = rm.listResources(
            DIRECTORY,
            loc -> loc.getNamespace().equals(OmniTech.MODID) && loc.getPath().endsWith(".json")
        );

        if (resources.isEmpty()) {
            OmniTech.LOGGER.error("[OmniTech] No celestial body definitions found in assets/{}/celestial_bodies/", OmniTech.MODID);
            cached = empty();
            return cached;
        }

        Map<String, CelestialBodyDef> byUuid = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                CelestialBodyDef def = GSON.fromJson(reader, CelestialBodyDef.class);
                if (def != null && def.uuid != null) {
                    byUuid.put(def.uuid, def);
                }
            } catch (IOException e) {
                OmniTech.LOGGER.error("[OmniTech] Failed to load {}", entry.getKey(), e);
            }
        }

        cached = buildHierarchy(byUuid);
        return cached;
    }

    private static SpaceMap buildHierarchy(Map<String, CelestialBodyDef> byUuid) {
        List<Galaxy> galaxies = new ArrayList<>();

        for (CelestialBodyDef galaxyDef : byUuid.values()) {
            if (!"galaxy".equals(galaxyDef.type)) continue;
            Galaxy galaxy = toGalaxy(galaxyDef);

            List<StarSystem> systems = new ArrayList<>();
            for (CelestialBodyDef starDef : byUuid.values()) {
                if (!"star".equals(starDef.type) || !galaxyDef.uuid.equals(starDef.parent)) continue;
                StarSystem system = toStarSystem(starDef);

                List<CelestialBody> planets = new ArrayList<>();
                for (CelestialBodyDef planetDef : byUuid.values()) {
                    String t = planetDef.type;
                    if ((!"planet".equals(t) && !"belt".equals(t)) || !starDef.uuid.equals(planetDef.parent)) continue;
                    CelestialBody planet = toCelestialBody(planetDef);

                    List<CelestialBody> moons = new ArrayList<>();
                    for (CelestialBodyDef moonDef : byUuid.values()) {
                        if (!"moon".equals(moonDef.type) || !planetDef.uuid.equals(moonDef.parent)) continue;
                        moons.add(toCelestialBody(moonDef));
                    }
                    moons.sort(Comparator.comparingLong(m -> m.parent_distance_km));
                    planet.moons = moons;
                    planets.add(planet);
                }
                planets.sort(Comparator.comparingLong(p -> p.orbital_distance_km));
                system.bodies = planets;
                systems.add(system);
            }
            systems.sort(Comparator.comparingInt(s -> s.orbital_radius));
            galaxy.star_systems = systems;
            galaxies.add(galaxy);
        }

        galaxies.sort(Comparator.comparingInt(g -> g.orbital_radius));

        SpaceMap map = new SpaceMap();
        map.galaxies = galaxies;
        return map;
    }

    private static Galaxy toGalaxy(CelestialBodyDef def) {
        Galaxy g = new Galaxy();
        g.id = def.uuid;
        g.texture = def.texture;
        g.background = def.background;
        g.orbital_radius = def.orbital_radius;
        g.orbital_speed = def.orbital_speed;
        g.star_systems = new ArrayList<>();
        return g;
    }

    private static StarSystem toStarSystem(CelestialBodyDef def) {
        StarSystem s = new StarSystem();
        s.id = def.uuid;
        s.texture = def.texture;
        s.background = def.background;
        s.orbital_radius = def.orbital_radius;
        s.orbital_speed = def.orbital_speed;
        s.size = def.size;
        s.day_length_ticks = def.day_length_ticks;
        s.bodies = new ArrayList<>();
        return s;
    }

    private static CelestialBody toCelestialBody(CelestialBodyDef def) {
        CelestialBody b = new CelestialBody();
        b.id = def.uuid;
        b.type = def.type;
        b.dimension = def.dimension;
        b.texture = def.texture;
        b.background = def.background;
        b.orbital_radius = def.orbital_radius;
        b.orbital_speed = def.orbital_speed;
        b.size = def.size;
        b.orbital_distance_km = def.orbital_distance_km;
        b.parent_distance_km = def.parent_distance_km;
        b.orbital_inclination = def.orbital_inclination;
        b.ascending_node = def.ascending_node;
        b.base_temperature = def.base_temperature;
        b.temperature_amplitude = def.temperature_amplitude;
        b.surface_pressure = def.surface_pressure;
        b.pressure_gradient = def.pressure_gradient;
        b.surface_y = def.surface_y;
        b.surface_gravity = def.surface_gravity;
        b.day_length_ticks = def.day_length_ticks;
        b.moons = new ArrayList<>();
        return b;
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
