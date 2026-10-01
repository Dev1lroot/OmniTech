/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.util.DisplayUnits;
import com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceMapRenderState;
import com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceScene;
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
    /**
     * Orbit-control gesture: a left press anywhere in the scene starts it; dragging more
     * than {@link #DRAG_SLOP} px rotates, releasing without moving selects what's under the
     * cursor. (26.x numbers mouse buttons from 1: LEFT = InputConstants.MOUSE_BUTTON_LEFT.)
     */
    private boolean pressed, rotating;
    private double pressX, pressY, lastX, lastY;
    private long lastSelectTime;
    private @Nullable Object lastSelected;
    private static final double DRAG_SLOP = 3.0;
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
            for (CelestialBody s : b.satellites()) sats = Math.max(sats, SpaceScene.moonRadius(s));
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
            case SYSTEM -> { if (system != null) max = SpaceScene.systemExtent(system); }
        }
        return max;
    }


    /** Fixed position on a plane for things that don't orbit (galaxies, star systems). */
    private static Vector3f planeOffset(String id, float r) {
        double a = (Math.abs(id.hashCode()) % 6283) / 1000.0;
        return new Vector3f((float) (r * Math.cos(a)), 0f, (float) (r * Math.sin(a)));
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
                    else cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, 6, null, null, 0xFF9966FF, 0));
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
                    cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, 4f, parse(s.texture), null, SpaceScene.tint(s.texture, SpaceScene.starColor(s)), 0));
                    put(s, p, 5f);
                }
            }
            case SYSTEM -> {
                if (system == null) break;
                SpaceScene.Scene scene = SpaceScene.buildSystem(system, time, destination, yaw);
                cubes.addAll(scene.cubes);
                rings.addAll(scene.rings);
                positions.putAll(scene.positions);
                sizes.putAll(scene.sizes);
                beltPoints.addAll(scene.beltPoints);
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
        g.text(font, "Fuel: " + DisplayUnits.volumeOf(cachedPlayerFuel, cachedRequiredFuel) + " hydrazine required", 12, y + 12, enough ? C_OK : C_BAD);
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
        if (super.mouseClicked(event, doubleClick)) return true;   // side list, buttons
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || !inScene(event.x(), event.y())) return false;
        pressed = true;
        rotating = false;
        pressX = lastX = event.x();
        pressY = lastY = event.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) finishPress(event.x(), event.y());
        return super.mouseReleased(event);
    }

    private boolean inScene(double x, double y) {
        return x >= PANEL_W && y >= TOP_H && y <= height - BOTTOM_H;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressed) return super.mouseDragged(event, dx, dy);
        if (!rotating && Math.hypot(event.x() - pressX, event.y() - pressY) > DRAG_SLOP) rotating = true;
        if (rotating) {
            yaw = (yaw + (float) (event.x() - lastX) * 0.6f) % 360f;
            pitch = Math.max(-89f, Math.min(89f, pitch + (float) (event.y() - lastY) * 0.6f));
        }
        lastX = event.x();
        lastY = event.y();
        return true;
    }

    private void finishPress(double x, double y) {
        pressed = false;
        if (rotating) { rotating = false; return; }
        int cx = (PANEL_W + width) / 2, cy = (TOP_H + height - BOTTOM_H) / 2;
        Object hit = pick(pressX, pressY, cx, cy);
        if (hit == null) return;
        long now = System.currentTimeMillis();
        boolean doubleClick = hit == lastSelected && now - lastSelectTime < 400;
        lastSelected = hit;
        lastSelectTime = now;
        if (doubleClick && (hit instanceof Galaxy || hit instanceof StarSystem) && level != Level.SYSTEM) enter(hit);
        else select(hit);
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





    private static String formatKm(long km) {
        if (km < 1_000_000L) return String.format("%,d km", km);
        if (km < 1_000_000_000L) return String.format("%.2f M km", km / 1_000_000.0);
        return String.format("%.3f B km", km / 1_000_000_000.0);
    }
}
