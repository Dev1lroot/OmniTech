/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.space.OrreryBlockEntity;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SolarSystemScene;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Renders a holographic 3-D solar-system miniature above the Orrery block.
 *
 * <p>The display uses the same {@link SolarSystemScene} orbital mechanics as the
 * sky renderer, so the miniature and the actual sky are always synchronized.
 * Bodies are rendered as textured cubes using the celestials atlas.
 * Orbit paths are shown as dotted rings of tiny cubes.
 *
 * <p>The whole scene slowly rotates on the Y axis and is tilted 20° toward the
 * viewer for depth cue.
 */
public class OrreryBlockEntityRenderer
        implements BlockEntityRenderer<OrreryBlockEntity, OrreryRenderState> {

    /** How far above the block surface the hologram floats (in block units). */
    private static final float DISPLAY_Y = 2.05f;
    /** Half-size of the orrery display volume (radius from center in block units). */
    private static final float DISPLAY_R = 1.90f;
    /** Size of star cube (half-extent in scene units before scaling). */
    private static final float STAR_HALF  = 14f;
    /** Size of planet cube. */
    private static final float PLANET_HALF = 6f;
    /** Size of moon cube. */
    private static final float MOON_HALF   = 3f;
    /** Dots per orbit ring. */
    private static final int   RING_DOTS   = 24;
    /** Half-size of each orbit ring dot cube. */
    private static final float DOT_HALF    = 1.5f;
    /** Rotation speed of the whole scene (degrees per tick). */
    private static final float SCENE_ROT_SPEED = 0.3f;
    /** Tilt of the scene toward the viewer (degrees). */
    private static final float SCENE_TILT = 20f;
    /** Full-bright light coords for the hologram. */
    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;

    public OrreryBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public OrreryRenderState createRenderState() { return new OrreryRenderState(); }

    @Override
    public void extractRenderState(OrreryBlockEntity entity, OrreryRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long gameTick = mc.level.getOverworldClockTime();
        state.animTime = gameTick + partialTicks;

        SpaceMap spaceMap = SpaceMapLoader.load(mc.getResourceManager());
        if (spaceMap.galaxies == null) return;

        // Use the first galaxy/system available (or the one matching the current dimension).
        StarSystem system = null;
        String dimId = mc.level.dimension().identifier().toString();
        outer:
        for (Galaxy g : spaceMap.galaxies) {
            if (g.star_systems == null) continue;
            for (StarSystem s : g.star_systems) {
                if (s.bodies == null) continue;
                // Prefer the system the player is currently in.
                if (s.containsDimension(dimId)) { system = s; break outer; }
                if (system == null) system = s; // fallback: first system
            }
        }
        if (system == null) return;

        // Find max orbital radius for scaling.
        float maxR = 1f;
        for (CelestialBody body : system.bodies) {
            if (body.orbital_radius > maxR) maxR = body.orbital_radius;
        }
        // Scale so maxR → DISPLAY_R in block units (scene units → block units).
        state.sceneScale = DISPLAY_R / maxR;

        long snapTime = gameTick; // integer ticks for position snapshot

        // Star
        Identifier starSprite = system.texture != null
                ? SpaceMapSkyboxRenderer.textureSpriteId(system.texture)
                : SpaceMapSkyboxRenderer.textureSpriteId("omnitech:textures/space/star/sun.png");
        state.bodies.add(new OrreryRenderState.BodyEntry(
                starSprite, 0f, 0f, 0f, STAR_HALF, 0xFFFFCC44));

        // Planets + moons
        for (CelestialBody planet : system.bodies) {
            Vector3f pPos = SolarSystemScene.bodyPosition(planet, snapTime);

            int planetColor = bodyColor(planet.id);
            Identifier planetSprite = planet.texture != null
                    ? SpaceMapSkyboxRenderer.textureSpriteId(planet.texture) : null;
            state.bodies.add(new OrreryRenderState.BodyEntry(
                    planetSprite, pPos.x, pPos.y, pPos.z, PLANET_HALF, planetColor));

            // Orbit ring for planet
            state.orbits.add(new OrreryRenderState.OrbitEntry(
                    planet.orbital_radius > 0 ? planet.orbital_radius : 150f,
                    planet.orbital_inclination, planet.ascending_node,
                    (planetColor & 0x00FFFFFF) | 0x55000000));

            // Moons
            if (planet.moons != null) {
                for (CelestialBody moon : planet.moons) {
                    Vector3f mOff = SolarSystemScene.moonOffset(moon, snapTime);
                    Identifier moonSprite = moon.texture != null
                            ? SpaceMapSkyboxRenderer.textureSpriteId(moon.texture) : null;
                    state.bodies.add(new OrreryRenderState.BodyEntry(
                            moonSprite,
                            pPos.x + mOff.x, pPos.y + mOff.y, pPos.z + mOff.z,
                            MOON_HALF, 0xFFAAAAAA));
                }
            }
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(OrreryRenderState state, PoseStack pose,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.bodies.isEmpty()) return;

        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        // White wool for orbit dots and fallback color
        TextureAtlasSprite white = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_wool"));
        float wu0 = white.getU0(), wu1 = white.getU1();
        float wv0 = white.getV0(), wv1 = white.getV1();
        Identifier whiteAtlas = white.atlasLocation();

        float s = state.sceneScale;

        pose.pushPose();
        // Center on top face of the block
        pose.translate(0.5f, DISPLAY_Y, 0.5f);
        // Scene auto-rotation
        pose.mulPose(Axis.YP.rotationDegrees(state.animTime * SCENE_ROT_SPEED));
        // Tilt toward viewer
        pose.mulPose(Axis.XP.rotationDegrees(SCENE_TILT));

        // ── Orbit rings ────────────────────────────────────────────────────────
        nodes.submitCustomGeometry(pose, RenderTypes.eyes(whiteAtlas), (p, buf) -> {
            for (OrreryRenderState.OrbitEntry ring : state.orbits) {
                float speed = (float)(SolarSystemScene.ORBIT_SPEED_RAD_PER_TICK); // one step per tick
                for (int d = 0; d < RING_DOTS; d++) {
                    float theta = (float)(2 * Math.PI * d / RING_DOTS);
                    Vector3f dp = SolarSystemScene.cartesian(ring.r(), theta,
                            ring.inclinationDeg(), ring.ascendingNodeDeg());
                    float bx = dp.x * s, by = dp.y * s, bz = dp.z * s;
                    float h = DOT_HALF * s;
                    box(p, buf, bx - h, by - h, bz - h, bx + h, by + h, bz + h,
                            wu0, wv0, wu1, wv1, ring.color(), LIGHT);
                }
            }
        });

        // ── Bodies ─────────────────────────────────────────────────────────────
        for (OrreryRenderState.BodyEntry body : state.bodies) {
            float bx = body.x() * s, by = body.y() * s, bz = body.z() * s;
            float h = body.halfSize() * s;

            if (body.spriteId() != null) {
                TextureAtlasSprite sp = atlas.getSprite(body.spriteId());
                float u0 = sp.getU0(), u1 = sp.getU1();
                float v0 = sp.getV0(), v1 = sp.getV1();
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(atlas.location()), (p, buf) ->
                        box(p, buf, bx - h, by - h, bz - h, bx + h, by + h, bz + h,
                                u0, v0, u1, v1, -1, LIGHT));
            } else {
                // Fallback: solid color using white wool
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(whiteAtlas), (p, buf) ->
                        box(p, buf, bx - h, by - h, bz - h, bx + h, by + h, bz + h,
                                wu0, wv0, wu1, wv1, body.color(), LIGHT));
            }
        }

        pose.popPose();
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static void box(PoseStack.Pose p, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1,
                             int color, int light) {
        quad(p, buf, x0,y1,z0, u0,v0, x0,y1,z1, u0,v1, x1,y1,z1, u1,v1, x1,y1,z0, u1,v0, color, light,  0, 1, 0);
        quad(p, buf, x1,y0,z0, u0,v0, x1,y0,z1, u0,v1, x0,y0,z1, u1,v1, x0,y0,z0, u1,v0, color, light,  0,-1, 0);
        quad(p, buf, x0,y1,z0, u0,v0, x1,y1,z0, u1,v0, x1,y0,z0, u1,v1, x0,y0,z0, u0,v1, color, light,  0, 0,-1);
        quad(p, buf, x1,y1,z1, u0,v0, x0,y1,z1, u1,v0, x0,y0,z1, u1,v1, x1,y0,z1, u0,v1, color, light,  0, 0, 1);
        quad(p, buf, x0,y1,z1, u0,v0, x0,y1,z0, u1,v0, x0,y0,z0, u1,v1, x0,y0,z1, u0,v1, color, light, -1, 0, 0);
        quad(p, buf, x1,y1,z0, u0,v0, x1,y1,z1, u1,v0, x1,y0,z1, u1,v1, x1,y0,z0, u0,v1, color, light,  1, 0, 0);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer buf,
                              float x0, float y0, float z0, float u0, float v0,
                              float x1, float y1, float z1, float u1, float v1,
                              float x2, float y2, float z2, float u2, float v2,
                              float x3, float y3, float z3, float u3, float v3,
                              int color, int light, float nx, float ny, float nz) {
        int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        buf.addVertex(p,x0,y0,z0).setColor(color).setUv(u0,v0).setOverlay(ov).setLight(light).setNormal(p,nx,ny,nz);
        buf.addVertex(p,x1,y1,z1).setColor(color).setUv(u1,v1).setOverlay(ov).setLight(light).setNormal(p,nx,ny,nz);
        buf.addVertex(p,x2,y2,z2).setColor(color).setUv(u2,v2).setOverlay(ov).setLight(light).setNormal(p,nx,ny,nz);
        buf.addVertex(p,x3,y3,z3).setColor(color).setUv(u3,v3).setOverlay(ov).setLight(light).setNormal(p,nx,ny,nz);
    }

    // ── Body color table ──────────────────────────────────────────────────────

    private static int bodyColor(String id) {
        return switch (id) {
            case "mercury"   -> ARGB.color(0xFF, 0x9A, 0x88, 0x74);
            case "venus"     -> ARGB.color(0xFF, 0xE8, 0xC8, 0x7A);
            case "earth"     -> ARGB.color(0xFF, 0x22, 0x44, 0xBB);
            case "mars"      -> ARGB.color(0xFF, 0xBB, 0x44, 0x22);
            case "jupiter"   -> ARGB.color(0xFF, 0xCC, 0x99, 0x66);
            case "saturn"    -> ARGB.color(0xFF, 0xDD, 0xB8, 0x70);
            case "uranus"    -> ARGB.color(0xFF, 0x99, 0xDD, 0xCC);
            case "neptune"   -> ARGB.color(0xFF, 0x33, 0x55, 0xAA);
            case "moon"      -> ARGB.color(0xFF, 0x88, 0x88, 0x88);
            case "io"        -> ARGB.color(0xFF, 0xFF, 0xCC, 0x33);
            case "europa"    -> ARGB.color(0xFF, 0xDD, 0xEE, 0xFF);
            case "ganymede"  -> ARGB.color(0xFF, 0x99, 0x88, 0x77);
            case "titan"     -> ARGB.color(0xFF, 0xCC, 0x99, 0x44);
            default          -> ARGB.color(0xFF, 0x44, 0x77, 0xCC);
        };
    }
}
