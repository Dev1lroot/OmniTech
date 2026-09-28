/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceMapRenderState;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.network.SpaceTravelPacket;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SolarSystemScene;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import com.dev1lroot.mcmods.omnitech.space.TravelDistanceCalculator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen 3-D space navigation map, rendered as real geometry through
 * {@link com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceMapPipRenderer} (the
 * picture-in-picture path the GUI player model uses) in the orrery's style.
 *
 * <ul>
 *   <li>UNIVERSE – galaxies as flat textured discs at fixed offsets on a plane.</li>
 *   <li>GALAXY – the galaxy's flat texture on the plane, star systems as stars placed at
 *       fixed offsets on it (they don't orbit).</li>
 *   <li>SYSTEM – star, planets on their inclined orbits, moons around their planets,
 *       belts as particle clouds.</li>
 * </ul>
 *
 * <p>Orbit controls: the camera always orbits the focused object — at start the body the
 * player is on. Drag rotates, scroll zooms; clicking any object (in the scene or the side
 * list) glides the focus onto it. Celestial bodies also become the travel destination.
 */
public class SpaceNavigationScreen extends Screen {

    private enum Level { UNIVERSE, GALAXY, SYSTEM }

    // ── Navigation state ─────────────────────────────────────────────────────
    private Level level = Level.UNIVERSE;
    private @Nullable Galaxy galaxy;
    private @Nullable StarSystem system;
    /** Orbit-control pivot: a Galaxy, StarSystem or CelestialBody (null = scene origin). */
    private @Nullable Object focus;
    private @Nullable CelestialBody destination;

    private SpaceMap spaceMap;
    private String currentDimensionId = "";

    private int cachedPlayerFuel = 0;
    private int cachedRequiredFuel = -1;
    private boolean lastLaunchEligible = false;

    // ── Camera ───────────────────────────────────────────────────────────────
    private float yaw = 35f, pitch = 25f;
    private double zoom = 1.0, targetZoom = 1.0;
    private final Vector3f focusPos = new Vector3f(), focusTarget = new Vector3f();
    private boolean dragging;
    private boolean snapFocus = true;

    /** Positions of every pickable object this frame (scene units). */
    private final Map<Object, Vector3f> positions = new HashMap<>();
    private final Map<Object, Float> sizes = new HashMap<>();
    /** Belt particle positions, pickable as the belt. */
    private final List<Map.Entry<CelestialBody, Vector3f>> beltPoints = new ArrayList<>();
    private Matrix4f lastView = new Matrix4f();
    private float lastScale = 1f;

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int TOP_H = 50, BOTTOM_H = 64, PANEL_W = 124;
    private static final double ORBIT_SPEED = (2 * Math.PI) / 600.0;
    private static final double ZOOM_MIN = 0.4, ZOOM_MAX = 60.0;
    /** Moon orbits are drawn at this fraction of their JSON orbital_radius plus an offset. */
    private static final float MOON_SCALE = 0.1f, MOON_OFFSET = 5f;

    private static final int C_BG = 0xFF020208, C_TITLE = 0xFF88DDFF, C_SUB = 0xFF556677, C_BODY = 0xFFCCDDEE,
            C_CUR = 0xFF40EE88, C_SEL = 0xFF80FFCC, C_NA = 0xFF6A7A8A, C_OK = 0xFF40EE70, C_BAD = 0xFFEE4040,
            C_BAR = 0xCC030310, C_LINE = 0xFF1A2840, C_PANEL = 0x99030310;

    public SpaceNavigationScreen() {
        super(Component.literal("Space Navigation"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (minecraft.player != null) currentDimensionId = minecraft.player.level().dimension().identifier().toString();
        spaceMap = SpaceMapLoader.load(minecraft.getResourceManager());
        if (level == Level.UNIVERSE && galaxy == null) {
            SpaceMap.Location loc = spaceMap.findLocation(currentDimensionId);
            if (loc != null) {
                galaxy = loc.galaxy();
                system = loc.system();
                level = Level.SYSTEM;
                focus = loc.body();
                targetZoom = zoom = focusZoom(loc.body());
            }
        }
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        if (level != Level.UNIVERSE) {
            addRenderableWidget(Button.builder(Component.literal("< Back"), b -> goUp()).bounds(8, 8, 55, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose()).bounds(width - 24, 8, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomBy(1.25)).bounds(width - 24, 30, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomBy(0.8)).bounds(width - 24, 48, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("⟲"), b -> { focus = null; targetZoom = 1.0; yaw = 35f; pitch = 25f; })
                .bounds(width - 24, 66, 16, 16).build());

        // side list
        int y = TOP_H + 14, rowH = 14;
        for (ListEntry e : listEntries()) {
            if (y + rowH > height - BOTTOM_H - 2) break;
            String prefix = (e.item == destination || e.item == focus ? "> " : isCurrentLocation(e.item) ? "* " : "  ");
            String label = "  ".repeat(e.indent) + prefix + displayName(e.item);
            if (font.width(label) > PANEL_W - 12) label = font.plainSubstrByWidth(label, PANEL_W - 18) + "…";
            Object item = e.item;
            addRenderableWidget(Button.builder(Component.literal(label), b -> select(item)).bounds(4, y, PANEL_W - 8, rowH - 1).build());
            y += rowH;
        }

        // enter a galaxy / system that is focused
        if ((level == Level.UNIVERSE && focus instanceof Galaxy) || (level == Level.GALAXY && focus instanceof StarSystem)) {
            String label = "Enter " + displayName(focus) + " >";
            int bw = font.width(label) + 16;
            addRenderableWidget(Button.builder(Component.literal(label), b -> enter(focus))
                    .bounds(width - bw - 8, height - BOTTOM_H + 20, bw, 22).build());
        }
        if (canLaunch()) {
            addRenderableWidget(Button.builder(Component.literal("LAUNCH"), b -> onLaunch())
                    .bounds(width - 106, height - BOTTOM_H + 20, 98, 22).build());
        }
    }

    private record ListEntry(Object item, int indent) {}

    private List<ListEntry> listEntries() {
        List<ListEntry> out = new ArrayList<>();
        switch (level) {
            case UNIVERSE -> { if (spaceMap.galaxies != null) spaceMap.galaxies.forEach(g -> out.add(new ListEntry(g, 0))); }
            case GALAXY -> { if (galaxy != null && galaxy.star_systems != null) galaxy.star_systems.forEach(s -> out.add(new ListEntry(s, 0))); }
            case SYSTEM -> {
                if (system != null && system.bodies != null) {
                    for (CelestialBody b : system.bodies) {
                        out.add(new ListEntry(b, 0));
                        for (CelestialBody sat : b.satellites()) out.add(new ListEntry(sat, 1));
                    }
                }
            }
        }
        return out;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Scene click or list click: focus the object; celestial bodies also become the destination. */
    private void select(Object item) {
        focus = item;
        targetZoom = focusZoom(item);
        if (item instanceof CelestialBody b) destination = b;
        refreshWidgets();
    }

    private void enter(@Nullable Object item) {
        if (item instanceof Galaxy g) { galaxy = g; level = Level.GALAXY; }
        else if (item instanceof StarSystem s) { system = s; level = Level.SYSTEM; }
        else return;
        focus = null; destination = null; targetZoom = zoom = 1.0; snapFocus = true;
        refreshWidgets();
    }

    private void goUp() {
        destination = null;
        switch (level) {
            case SYSTEM -> { focus = system; system = null; level = Level.GALAXY; }
            case GALAXY -> { focus = galaxy; galaxy = null; level = Level.UNIVERSE; }
            default -> {}
        }
        targetZoom = zoom = 1.0;
        snapFocus = true;
        refreshWidgets();
    }

    /** Zoom that suits looking at an object: planets with satellites get their moon system in view. */
    private double focusZoom(@Nullable Object item) {
        if (item instanceof CelestialBody b && !b.isBelt()) {
            double sats = 0;
            for (CelestialBody s : b.satellites()) sats = Math.max(sats, moonRadius(s));
            double extent = sceneExtent();
            return sats > 0 ? Math.min(ZOOM_MAX, extent / (sats * 1.6)) : Math.min(ZOOM_MAX, extent / 30.0);
        }
        return Math.max(zoom, 1.0);
    }

    private void onLaunch() {
        if (!canLaunch()) return;
        ClientPacketDistributor.sendToServer(new SpaceTravelPacket(destination.dimension, cachedRequiredFuel));
        onClose();
    }

    private boolean canLaunch() {
        return destination != null && destination.dimension != null && cachedRequiredFuel > 0 && cachedPlayerFuel >= cachedRequiredFuel;
    }

    // ── Scene building ────────────────────────────────────────────────────────

    private float sceneExtent() {
        float max = 60f;
        switch (level) {
            case UNIVERSE -> { if (spaceMap.galaxies != null) for (Galaxy g : spaceMap.galaxies) max = Math.max(max, g.orbital_radius + 60); }
            case GALAXY -> { if (galaxy != null && galaxy.star_systems != null) for (StarSystem s : galaxy.star_systems) max = Math.max(max, s.orbital_radius * 1.1f); }
            case SYSTEM -> { if (system != null && system.bodies != null) for (CelestialBody b : system.bodies) max = Math.max(max, b.orbital_radius * (1 + (b.isBelt() ? b.belt_width : 0))); }
        }
        return max;
    }

    private static float moonRadius(CelestialBody moon) {
        return MOON_OFFSET + moon.orbital_radius * MOON_SCALE;
    }

    /** Fixed position on a plane for things that don't orbit (galaxies, star systems). */
    private static Vector3f planeOffset(String id, float r) {
        double a = (Math.abs(id.hashCode()) % 6283) / 1000.0;
        return new Vector3f((float) (r * Math.cos(a)), 0f, (float) (r * Math.sin(a)));
    }

    private static float orbitAngle(CelestialBody b, double time, float fallbackSpeed) {
        float speed = b.orbital_speed > 0 ? b.orbital_speed : fallbackSpeed;
        return (float) (time * ORBIT_SPEED * speed + (Math.abs(b.id.hashCode()) % 628) / 100.0);
    }

    private SpaceMapRenderState buildScene(int x0, int y0, int x1, int y1, double time) {
        positions.clear();
        sizes.clear();
        beltPoints.clear();
        List<SpaceMapRenderState.Cube> cubes = new ArrayList<>();
        List<SpaceMapRenderState.Ring> rings = new ArrayList<>();
        List<SpaceMapRenderState.Plane> planes = new ArrayList<>();

        switch (level) {
            case UNIVERSE -> {
                if (spaceMap.galaxies != null) for (Galaxy g : spaceMap.galaxies) {
                    Vector3f p = planeOffset(g.id, g.orbital_radius);
                    float half = 45f;
                    Identifier tex = parse(g.texture);
                    if (tex != null) planes.add(new SpaceMapRenderState.Plane(p.x, p.y, p.z, half, tex, 0xFFFFFFFF));
                    else cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, 6, null, 0xFF9966FF, 0));
                    put(g, p, half * 0.7f);
                }
            }
            case GALAXY -> {
                float extent = sceneExtent();
                Identifier tex = galaxy != null ? parse(galaxy.texture) : null;
                if (tex != null) planes.add(new SpaceMapRenderState.Plane(0, 0, 0, extent * 1.1f, tex, 0xFFFFFFFF));
                if (galaxy != null && galaxy.star_systems != null) for (StarSystem s : galaxy.star_systems) {
                    Vector3f p = planeOffset(s.id, s.orbital_radius);
                    p.y = 2f;   // just above the disc
                    cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, 4f, parse(s.texture), tint(s.texture, starColor(s)), 0));
                    put(s, p, 5f);
                }
            }
            case SYSTEM -> {
                if (system == null) break;
                cubes.add(new SpaceMapRenderState.Cube(0, 0, 0, 10f, parse(system.texture), tint(system.texture, starColor(system)),
                        SolarSystemScene.axialAngle(system, time)));
                put(system, new Vector3f(), 10f);
                if (system.bodies != null) for (CelestialBody b : system.bodies) {
                    rings.add(new SpaceMapRenderState.Ring(0, 0, 0, b.orbital_radius, b.orbital_inclination, b.ascending_node,
                            b == destination ? 0xFF40EE88 : 0xFF3A4A60));
                    if (b.isBelt()) { belt(cubes, b, new Vector3f(), b.orbital_radius, time); continue; }
                    Vector3f p = SolarSystemScene.cartesian(b.orbital_radius, orbitAngle(b, time, 1f), b.orbital_inclination, b.ascending_node);
                    float half = bodyHalf(b, 2.2f);
                    cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, half, parse(b.texture), tint(b.texture, bodyColor(b)), SolarSystemScene.axialAngle(b, time)));
                    put(b, p, half);
                    for (CelestialBody s : b.satellites()) {
                        float mr = moonRadius(s);
                        rings.add(new SpaceMapRenderState.Ring(p.x, p.y, p.z, mr, s.orbital_inclination, s.ascending_node,
                                s == destination ? 0xFF40EE88 : 0xFF2A3446));
                        if (s.isBelt()) { belt(cubes, s, p, mr, time); continue; }
                        Vector3f mp = SolarSystemScene.cartesian(mr, orbitAngle(s, time, 3f), s.orbital_inclination, s.ascending_node).add(p);
                        float mh = bodyHalf(s, 0.8f);
                        cubes.add(new SpaceMapRenderState.Cube(mp.x, mp.y, mp.z, mh, parse(s.texture), tint(s.texture, bodyColor(s)), SolarSystemScene.axialAngle(s, time)));
                        put(s, mp, mh);
                    }
                }
            }
        }

        // selection marker: a ring around the destination
        if (destination != null && positions.containsKey(destination)) {
            Vector3f p = positions.get(destination);
            rings.add(new SpaceMapRenderState.Ring(p.x, p.y, p.z, sizes.get(destination) * 2.2f, 0, 0, 0xFF40EE88));
        }

        // camera: glide the pivot to the focused object
        focusTarget.set(focus != null && positions.containsKey(focus) ? positions.get(focus) : new Vector3f());
        if (snapFocus) { focusPos.set(focusTarget); snapFocus = false; }
        else focusPos.lerp(focusTarget, 0.18f);
        zoom += (targetZoom - zoom) * 0.18;

        int w = x1 - x0, h = y1 - y0;
        float scale = (float) (Math.min(w, h) * 0.46 / sceneExtent() * zoom);
        // the PIP projection clips at ±1000 px of depth: squeeze depth only (x/y — and picking — unchanged)
        float depthPx = minecraft.getWindow().getGuiScale() * scale * sceneExtent() * 2.2f;
        float zSqueeze = Math.min(1f, 900f / Math.max(depthPx, 1f));
        Matrix4f view = new Matrix4f()
                .scale(1f, 1f, zSqueeze)
                .rotateX((float) Math.toRadians(180 + pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-focusPos.x, -focusPos.y, -focusPos.z);
        lastView = view;
        lastScale = scale;
        return new SpaceMapRenderState(x0, y0, x1, y1, scale, view, cubes, rings, planes, null);
    }

    private void put(Object item, Vector3f pos, float half) {
        positions.put(item, pos);
        sizes.put(item, half);
    }

    private void belt(List<SpaceMapRenderState.Cube> cubes, CelestialBody belt, Vector3f center, float r, double time) {
        int count = Math.max(50, belt.belt_particles);
        long seed = belt.id.hashCode() * 0x9E3779B97F4A7C15L;
        double drift = time * ORBIT_SPEED * 0.15 * (belt.orbital_speed > 0 ? belt.orbital_speed : 1.0);
        int color = belt == destination ? 0xFF60F0A0 : 0xFF8C8478;
        float bit = Math.max(0.25f, r * 0.004f);
        for (int k = 0; k < count; k++) {
            seed = mix(seed); double ang = (seed >>> 11) * 0x1.0p-53 * 2 * Math.PI + drift;
            seed = mix(seed); double rr = r * (1 + ((seed >>> 11) * 0x1.0p-53 - 0.5) * 2 * belt.belt_width);
            seed = mix(seed); double hh = ((seed >>> 11) * 0x1.0p-53 - 0.5) * r * belt.belt_width * 0.35;
            Vector3f p = SolarSystemScene.cartesian((float) rr, (float) ang, belt.orbital_inclination, belt.ascending_node).add(center);
            p.y += (float) hh;
            cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, bit, null, color, 0));
            if ((k & 3) == 0) beltPoints.add(Map.entry(belt, p));
        }
        Vector3f labelAt = SolarSystemScene.cartesian(r, (float) Math.toRadians(90 - yaw), belt.orbital_inclination, belt.ascending_node).add(center);
        put(belt, labelAt, 0f);
    }

    private static float bodyHalf(CelestialBody b, float base) {
        return base * (b.size > 0 ? (float) Math.max(0.6, Math.min(3.0, Math.sqrt(b.size) * 1.2)) : 1f);
    }

    /** Scene point → GUI coordinates, the same transform the PIP renderer applies. */
    private float[] project(Vector3f p, int cx, int cy) {
        Vector3f v = lastView.transformPosition(new Vector3f(p));
        return new float[]{cx + v.x * lastScale, cy + v.y * lastScale};
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, C_BG);
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 240; i++) {
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
            int sx = (int) (((seed >>> 1) & 0xFFFFL) * width >> 16);
            int sy = (int) (((seed >>> 17) & 0xFFFFL) * height >> 16);
            int br = (int) ((seed >>> 33) & 0x7F) + 70;
            g.fill(sx, sy, sx + 1, sy + 1, 0xFF000000 | (br << 16) | (br << 8) | br);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        int x0 = PANEL_W, y0 = TOP_H, x1 = width, y1 = height - BOTTOM_H;
        double time = (minecraft.level != null ? minecraft.level.getOverworldClockTime() : 0) + a;
        g.submitPictureInPictureRenderState(buildScene(x0, y0, x1, y1, time));

        // labels over the 3-D scene
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        Object hover = pick(mx, my, cx, cy);
        for (Map.Entry<Object, Vector3f> e : positions.entrySet()) {
            Object item = e.getKey();
            if (!showLabel(item)) continue;
            float[] s = project(e.getValue(), cx, cy);
            if (s[0] < x0 || s[0] > x1 || s[1] < y0 || s[1] > y1) continue;
            float half = sizes.getOrDefault(item, 0f) * lastScale;
            String name = displayName(item);
            int col = item == destination || item == focus ? C_SEL : isCurrentLocation(item) ? C_CUR
                    : item instanceof CelestialBody cb && !cb.isAvailable() ? C_NA : C_BODY;
            if (item == hover) col = 0xFFFFFFFF;
            g.text(font, name, (int) s[0] - font.width(name) / 2, (int) (s[1] + half + 3), col);
            if (isCurrentLocation(item) && item instanceof CelestialBody cb && currentDimensionId.equals(cb.dimension))
                g.text(font, "[ HERE ]", (int) s[0] - font.width("[ HERE ]") / 2, (int) (s[1] - half - 12), C_CUR);
        }

        g.text(font, "SPACE NAVIGATION", 70, 10, C_TITLE);
        g.text(font, breadcrumb(), 70, 24, C_SUB);
        String hint = String.format("Zoom %.0f%%  •  drag: rotate  •  scroll: zoom  •  click: focus", zoom * 100);
        g.text(font, hint, width - font.width(hint) - 30, 36, C_SUB);
        g.fill(0, TOP_H, PANEL_W, height - BOTTOM_H, C_PANEL);
        g.text(font, switch (level) { case UNIVERSE -> "Galaxies"; case GALAXY -> "Star systems"; case SYSTEM -> "Bodies"; }, 6, TOP_H + 3, C_SUB);
        g.fill(0, height - BOTTOM_H, width, height, C_BAR);
        g.fill(0, height - BOTTOM_H, width, height - BOTTOM_H + 1, C_LINE);
        bottomBar(g);

        super.extractRenderState(g, mx, my, a);
    }

    /** Moon labels only once zoomed in on their planet, to keep the system view readable. */
    private boolean showLabel(Object item) {
        if (!(item instanceof CelestialBody b) || level != Level.SYSTEM) return true;
        boolean isSatellite = system != null && system.bodies != null && !system.bodies.contains(b);
        return !isSatellite || zoom > 3 || item == destination || item == focus;
    }

    private void bottomBar(GuiGraphicsExtractor g) {
        int y = height - BOTTOM_H + 8;
        if (destination == null) {
            cachedPlayerFuel = 0;
            cachedRequiredFuel = -1;
            g.text(font, level == Level.SYSTEM ? "Click a body in the list or the map to choose a destination."
                    : "Click to focus, then enter it.", 12, y + 8, C_SUB);
            return;
        }
        cachedPlayerFuel = minecraft.player != null && minecraft.player.getVehicle() instanceof RocketEntity r ? r.getFuelAmount() : 0;
        cachedRequiredFuel = TravelDistanceCalculator.fuelCostMb(currentDimensionId, destination, spaceMap);
        if (cachedRequiredFuel < 0) cachedRequiredFuel = destination.fuel_cost;

        g.text(font, "Destination:  " + displayName(destination) + (destination.isBelt() ? "  (belt)" : ""), 12, y, C_TITLE);
        boolean enough = cachedRequiredFuel >= 0 && cachedPlayerFuel >= cachedRequiredFuel;
        g.text(font, String.format("Fuel: %,d / %,d mB hydrazine required", cachedPlayerFuel, cachedRequiredFuel), 12, y + 12, enough ? C_OK : C_BAD);
        long km = TravelDistanceCalculator.travelDistanceKm(currentDimensionId, destination, spaceMap);
        g.text(font, "Distance: " + (km > 0 ? formatKm(km) : "Unknown"), 12, y + 24, C_SUB);
        if (destination.dimension == null) g.text(font, "No dimension exists for this body yet.", 12, y + 36, 0xFFDD7733);
        else if (!(minecraft.player != null && minecraft.player.getVehicle() instanceof RocketEntity))
            g.text(font, "Board a rocket in orbit to travel.", 12, y + 36, 0xFFDD7733);

        boolean eligible = canLaunch();
        if (eligible != lastLaunchEligible) { lastLaunchEligible = eligible; refreshWidgets(); }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private @Nullable Object pick(double mx, double my, int cx, int cy) {
        Object best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<Object, Vector3f> e : positions.entrySet()) {
            if (e.getKey() instanceof CelestialBody b && b.isBelt()) continue;
            float[] s = project(e.getValue(), cx, cy);
            double r = Math.max(7, sizes.getOrDefault(e.getKey(), 0f) * lastScale * 1.4 + 2);
            double d = (mx - s[0]) * (mx - s[0]) + (my - s[1]) * (my - s[1]);
            if (d <= r * r && d < bestD) { best = e.getKey(); bestD = d; }
        }
        if (best != null) return best;
        for (Map.Entry<CelestialBody, Vector3f> e : beltPoints) {
            float[] s = project(e.getValue(), cx, cy);
            double d = (mx - s[0]) * (mx - s[0]) + (my - s[1]) * (my - s[1]);
            if (d <= 25 && d < bestD) { best = e.getKey(); bestD = d; }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0 || event.x() < PANEL_W || event.y() < TOP_H || event.y() > height - BOTTOM_H) return false;
        int cx = (PANEL_W + width) / 2, cy = (TOP_H + height - BOTTOM_H) / 2;
        Object hit = pick(event.x(), event.y(), cx, cy);
        if (hit != null) {
            if (doubleClick && (hit instanceof Galaxy || hit instanceof StarSystem) && level != Level.SYSTEM) enter(hit);
            else select(hit);
            return true;
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            yaw = (yaw + (float) dx * 0.6f) % 360f;
            pitch = Math.max(-89f, Math.min(89f, pitch + (float) dy * 0.6f));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        zoomBy(dy > 0 ? 1.15 : 1 / 1.15);
        return true;
    }

    private void zoomBy(double factor) {
        targetZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, targetZoom * factor));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static @Nullable Identifier parse(@Nullable String tex) {
        return tex == null || tex.isEmpty() ? null : Identifier.parse(tex);
    }

    private static String displayName(@Nullable Object data) {
        if (data instanceof Galaxy g) return I18n.get("omnitech.space.galaxy." + g.id);
        if (data instanceof StarSystem s) return I18n.get("omnitech.space.system." + s.id);
        if (data instanceof CelestialBody b) return I18n.get("omnitech.space.body." + b.id);
        return "";
    }

    private boolean isCurrentLocation(Object item) {
        if (item instanceof CelestialBody b) {
            if (currentDimensionId.equals(b.dimension)) return true;
            for (CelestialBody s : b.satellites()) if (currentDimensionId.equals(s.dimension)) return true;
            return false;
        }
        if (item instanceof StarSystem s) return s.containsDimension(currentDimensionId);
        if (item instanceof Galaxy g) return g.findSystemForDimension(currentDimensionId) != null;
        return false;
    }

    private String breadcrumb() {
        StringBuilder sb = new StringBuilder("Universe");
        if (galaxy != null) sb.append("  >  ").append(displayName(galaxy));
        if (system != null) sb.append("  >  ").append(displayName(system));
        return sb.toString();
    }

    /** Textures render untinted; the colour is only for bodies without one. */
    private static int tint(@Nullable String texture, int fallback) {
        return texture != null && !texture.isEmpty() ? 0xFFFFFFFF : fallback;
    }

    private static int starColor(StarSystem s) {
        return switch (s.id) {
            case "tau_ceti" -> 0xFFFFEE88;
            case "alpha_centauri" -> 0xFFFFCC66;
            default -> 0xFFFFDD44;
        };
    }

    private static int bodyColor(CelestialBody b) {
        return switch (b.id) {
            case "mercury" -> 0xFF9A8874; case "venus" -> 0xFFE8C87A; case "earth" -> 0xFF2244BB;
            case "mars" -> 0xFFBB4422; case "jupiter" -> 0xFFCC9966; case "saturn" -> 0xFFDDB870;
            case "uranus" -> 0xFF99DDCC; case "neptune" -> 0xFF3355AA; case "moon" -> 0xFF888888;
            case "io" -> 0xFFFFCC33; case "europa" -> 0xFFDDEEFF; case "ganymede" -> 0xFF998877;
            case "titan" -> 0xFFCC9944;
            default -> b.dimension != null ? 0xFF4477CC : 0xFF556677;
        };
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String formatKm(long km) {
        if (km < 1_000_000L) return String.format("%,d km", km);
        if (km < 1_000_000_000L) return String.format("%.2f M km", km / 1_000_000.0);
        return String.format("%.3f B km", km / 1_000_000_000.0);
    }
}
