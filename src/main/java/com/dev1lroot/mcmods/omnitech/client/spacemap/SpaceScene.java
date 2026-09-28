/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client.spacemap;

import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.SolarSystemScene;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared 3-D star-system scene for the space map ({@link SpaceMapPipRenderer}) and the
 * orrery block, so both look the same: emissive textured boxes for the star, planets and
 * moons, line loops for orbits, belts as clouds of little stone boxes.
 *
 * <p>Units are the map's display units: planets orbit at {@code orbital_radius}, moons
 * at {@link #moonRadius} around their planet.
 */
public final class SpaceScene {

    public static final float MOON_SCALE = 0.1f, MOON_OFFSET = 5f;
    public static final int ORBIT_COLOR = 0xFF3A4A60, MOON_ORBIT_COLOR = 0xFF2A3446, SELECTED_COLOR = 0xFF40EE88;
    private static final double ORBIT_SPEED = (2 * Math.PI) / 600.0;
    private static final int RING_SEGS = 128;
    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;
    private static final Identifier WHITE = Identifier.withDefaultNamespace("block/white_wool");
    private static final Identifier STONE = Identifier.withDefaultNamespace("block/stone");

    /** Built scene: geometry plus pick data (world-space position / half size per object). */
    public static final class Scene {
        public final List<SpaceMapRenderState.Cube> cubes = new ArrayList<>();
        public final List<SpaceMapRenderState.Ring> rings = new ArrayList<>();
        public final List<SpaceMapRenderState.Plane> planes = new ArrayList<>();
        public final Map<Object, Vector3f> positions = new HashMap<>();
        public final Map<Object, Float> sizes = new HashMap<>();
        public final List<Map.Entry<CelestialBody, Vector3f>> beltPoints = new ArrayList<>();

        public void put(Object item, Vector3f pos, float half) {
            positions.put(item, pos);
            sizes.put(item, half);
        }
    }

    private SpaceScene() {}

    // ── Building ──────────────────────────────────────────────────────────────

    public static float moonRadius(CelestialBody moon) {
        return MOON_OFFSET + moon.orbital_radius * MOON_SCALE;
    }

    /** Outermost orbit (incl. belt width) of a system, in scene units. */
    public static float systemExtent(StarSystem system) {
        float max = 60f;
        if (system.bodies != null) for (CelestialBody b : system.bodies)
            max = Math.max(max, b.orbital_radius * (1 + (b.isBelt() ? b.belt_width : 0)));
        return max;
    }

    /**
     * Star at the origin, planets on their inclined orbits, moons around their planets,
     * belts as particle clouds. {@code highlight} (the map's destination) is drawn green;
     * {@code labelYaw} places each belt's label on the side facing the camera.
     */
    public static Scene buildSystem(StarSystem system, double time, @Nullable CelestialBody highlight, float labelYaw) {
        Scene s = new Scene();
        s.cubes.add(new SpaceMapRenderState.Cube(0, 0, 0, 10f, parse(system.texture), null,
                tint(system.texture, starColor(system)), SolarSystemScene.axialAngle(system, time)));
        s.put(system, new Vector3f(), 10f);
        if (system.bodies == null) return s;

        for (CelestialBody b : system.bodies) {
            s.rings.add(new SpaceMapRenderState.Ring(0, 0, 0, b.orbital_radius, b.orbital_inclination, b.ascending_node,
                    b == highlight ? SELECTED_COLOR : ORBIT_COLOR));
            if (b.isBelt()) { belt(s, b, new Vector3f(), b.orbital_radius, time, highlight, labelYaw); continue; }

            Vector3f p = SolarSystemScene.cartesian(b.orbital_radius, orbitAngle(b, time, 1f), b.orbital_inclination, b.ascending_node);
            float half = bodyHalf(b, 2.2f);
            s.cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, half, parse(b.texture), null,
                    tint(b.texture, bodyColor(b)), SolarSystemScene.axialAngle(b, time)));
            s.put(b, p, half);

            for (CelestialBody sat : b.satellites()) {
                float mr = moonRadius(sat);
                s.rings.add(new SpaceMapRenderState.Ring(p.x, p.y, p.z, mr, sat.orbital_inclination, sat.ascending_node,
                        sat == highlight ? SELECTED_COLOR : MOON_ORBIT_COLOR));
                if (sat.isBelt()) { belt(s, sat, p, mr, time, highlight, labelYaw); continue; }
                Vector3f mp = SolarSystemScene.cartesian(mr, orbitAngle(sat, time, 3f), sat.orbital_inclination, sat.ascending_node).add(p);
                float mh = bodyHalf(sat, 0.8f);
                s.cubes.add(new SpaceMapRenderState.Cube(mp.x, mp.y, mp.z, mh, parse(sat.texture), null,
                        tint(sat.texture, bodyColor(sat)), SolarSystemScene.axialAngle(sat, time)));
                s.put(sat, mp, mh);
            }
        }
        return s;
    }

    private static void belt(Scene s, CelestialBody belt, Vector3f center, float r, double time,
                             @Nullable CelestialBody highlight, float labelYaw) {
        int count = Math.max(50, belt.belt_particles);
        long seed = belt.id.hashCode() * 0x9E3779B97F4A7C15L;
        double drift = time * ORBIT_SPEED * 0.15 * (belt.orbital_speed > 0 ? belt.orbital_speed : 1.0);
        int color = belt == highlight ? 0xFF90F0B0 : 0xFFB0A898;
        float bit = Math.max(0.25f, r * 0.004f);
        for (int k = 0; k < count; k++) {
            seed = mix(seed); double ang = (seed >>> 11) * 0x1.0p-53 * 2 * Math.PI + drift;
            seed = mix(seed); double rr = r * (1 + ((seed >>> 11) * 0x1.0p-53 - 0.5) * 2 * belt.belt_width);
            seed = mix(seed); double hh = ((seed >>> 11) * 0x1.0p-53 - 0.5) * r * belt.belt_width * 0.35;
            seed = mix(seed); float size = bit * (0.6f + (float) ((seed >>> 11) * 0x1.0p-53));
            Vector3f p = SolarSystemScene.cartesian((float) rr, (float) ang, belt.orbital_inclination, belt.ascending_node).add(center);
            p.y += (float) hh;
            s.cubes.add(new SpaceMapRenderState.Cube(p.x, p.y, p.z, size, null, STONE, color, (float) ang));
            if ((k & 3) == 0) s.beltPoints.add(Map.entry(belt, p));
        }
        Vector3f labelAt = SolarSystemScene.cartesian(r, (float) Math.toRadians(90 - labelYaw),
                belt.orbital_inclination, belt.ascending_node).add(center);
        s.put(belt, labelAt, 0f);
    }

    private static float orbitAngle(CelestialBody b, double time, float fallbackSpeed) {
        float speed = b.orbital_speed > 0 ? b.orbital_speed : fallbackSpeed;
        return (float) (time * ORBIT_SPEED * speed + (Math.abs(b.id.hashCode()) % 628) / 100.0);
    }

    private static float bodyHalf(CelestialBody b, float base) {
        return base * (b.size > 0 ? (float) Math.max(0.6, Math.min(3.0, Math.sqrt(b.size) * 1.2)) : 1f);
    }

    public static @Nullable Identifier parse(@Nullable String tex) {
        return tex == null || tex.isEmpty() ? null : Identifier.parse(tex);
    }

    /** Textures render untinted; the colour is only for bodies without one. */
    public static int tint(@Nullable String texture, int fallback) {
        return texture != null && !texture.isEmpty() ? 0xFFFFFFFF : fallback;
    }

    public static int starColor(StarSystem s) {
        return switch (s.id) {
            case "tau_ceti" -> 0xFFFFEE88;
            case "alpha_centauri" -> 0xFFFFCC66;
            default -> 0xFFFFDD44;
        };
    }

    public static int bodyColor(CelestialBody b) {
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

    // ── Drawing ───────────────────────────────────────────────────────────────

    /** Submits orbit lines, flat planes and boxes in the pose's current space. */
    public static void submit(List<SpaceMapRenderState.Cube> cubes, List<SpaceMapRenderState.Ring> rings,
                              List<SpaceMapRenderState.Plane> planes, PoseStack pose, SubmitNodeCollector nodes,
                              float lineWidth) {
        if (!rings.isEmpty()) nodes.submitCustomGeometry(pose, RenderTypes.LINES, (p, buf) -> {
            for (SpaceMapRenderState.Ring ring : rings) {
                for (int seg = 0; seg < RING_SEGS; seg++) {
                    Vector3f a = SolarSystemScene.cartesian(ring.r(), (float) (2 * Math.PI * seg / RING_SEGS), ring.inclination(), ring.node());
                    Vector3f b = SolarSystemScene.cartesian(ring.r(), (float) (2 * Math.PI * (seg + 1) / RING_SEGS), ring.inclination(), ring.node());
                    a.add(ring.cx(), ring.cy(), ring.cz());
                    b.add(ring.cx(), ring.cy(), ring.cz());
                    Vector3f d = new Vector3f(b).sub(a);
                    if (d.lengthSquared() > 1e-12f) d.normalize();
                    buf.addVertex(p, a.x, a.y, a.z).setColor(ring.color()).setNormal(p, d.x, d.y, d.z).setLineWidth(lineWidth);
                    buf.addVertex(p, b.x, b.y, b.z).setColor(ring.color()).setNormal(p, d.x, d.y, d.z).setLineWidth(lineWidth);
                }
            }
        });

        for (SpaceMapRenderState.Plane plane : planes) {
            float h = plane.half(), x = plane.x(), y = plane.y(), z = plane.z();
            nodes.submitCustomGeometry(pose, RenderTypes.eyes(plane.texture()), (p, buf) -> {
                quad(p, buf, x - h, y, z - h, 0, 0, x - h, y, z + h, 0, 1, x + h, y, z + h, 1, 1, x + h, y, z - h, 1, 0, plane.color(), 0, 1, 0);
                quad(p, buf, x + h, y, z - h, 1, 0, x + h, y, z + h, 1, 1, x - h, y, z + h, 0, 1, x - h, y, z - h, 0, 0, plane.color(), 0, -1, 0);
            });
        }

        var blocks = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        // atlas-sprite boxes (belt stones, untextured bodies) share one render type: batch them
        Map<Identifier, List<SpaceMapRenderState.Cube>> spriteCubes = new HashMap<>();
        for (SpaceMapRenderState.Cube cube : cubes) {
            if (cube.texture() != null) {
                pose.pushPose();
                pose.translate(cube.x(), cube.y(), cube.z());
                if (cube.spin() != 0f) pose.rotate(Axis.YP.rotation(cube.spin()));
                float h = cube.half();
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(cube.texture()), (p, buf) -> box(p, buf, 0, 0, 0, h, 0, 0, 1, 1, cube.color()));
                pose.popPose();
            } else {
                spriteCubes.computeIfAbsent(cube.sprite() != null ? cube.sprite() : WHITE, k -> new ArrayList<>()).add(cube);
            }
        }
        for (Map.Entry<Identifier, List<SpaceMapRenderState.Cube>> e : spriteCubes.entrySet()) {
            TextureAtlasSprite sp = blocks.getSprite(e.getKey());
            float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();
            nodes.submitCustomGeometry(pose, RenderTypes.eyes(sp.atlasLocation()), (p, buf) -> {
                for (SpaceMapRenderState.Cube c : e.getValue()) box(p, buf, c.x(), c.y(), c.z(), c.half(), u0, v0, u1, v1, c.color());
            });
        }
    }

    private static void box(PoseStack.Pose p, VertexConsumer buf, float cx, float cy, float cz, float h,
                            float u0, float v0, float u1, float v1, int color) {
        float ax = cx - h, bx = cx + h, ay = cy - h, by = cy + h, az = cz - h, bz = cz + h;
        quad(p, buf, ax, by, az, u0, v0, ax, by, bz, u0, v1, bx, by, bz, u1, v1, bx, by, az, u1, v0, color, 0, 1, 0);
        quad(p, buf, bx, ay, az, u0, v0, bx, ay, bz, u0, v1, ax, ay, bz, u1, v1, ax, ay, az, u1, v0, color, 0, -1, 0);
        quad(p, buf, ax, by, az, u0, v0, bx, by, az, u1, v0, bx, ay, az, u1, v1, ax, ay, az, u0, v1, color, 0, 0, -1);
        quad(p, buf, bx, by, bz, u0, v0, ax, by, bz, u1, v0, ax, ay, bz, u1, v1, bx, ay, bz, u0, v1, color, 0, 0, 1);
        quad(p, buf, ax, by, bz, u0, v0, ax, by, az, u1, v0, ax, ay, az, u1, v1, ax, ay, bz, u0, v1, color, -1, 0, 0);
        quad(p, buf, bx, by, az, u0, v0, bx, by, bz, u1, v0, bx, ay, bz, u1, v1, bx, ay, az, u0, v1, color, 1, 0, 0);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer buf,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             int color, float nx, float ny, float nz) {
        int ov = OverlayTexture.NO_OVERLAY;
        buf.addVertex(p, x0, y0, z0).setColor(color).setUv(u0, v0).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x1, y1, z1).setColor(color).setUv(u1, v1).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x2, y2, z2).setColor(color).setUv(u2, v2).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x3, y3, z3).setColor(color).setUv(u3, v3).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
    }
}
