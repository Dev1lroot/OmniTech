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
 * Orbit paths are smooth ribbon curves lying in each orbit's inclined plane.
 *
 * <p>The whole scene slowly rotates on the Y axis and is tilted 20° toward the
 * viewer for depth cue.
 */
public class OrreryBlockEntityRenderer
        implements BlockEntityRenderer<OrreryBlockEntity, OrreryRenderState> {

    /** How far above the block surface the hologram floats (in block units). */
    private static final float DISPLAY_Y = 5.0f;
    /** Half-size of the orrery display volume (radius from center in block units). */
    private static final float DISPLAY_R = 500.0f;
    /**
     * Converts a physical size (Earth-relative diameter) to scene-space half-extent
     * using the SAME linear scale factor as orbital distances.
     * Earth radius = 6 371 km; PLANET_K maps Neptune's orbit (4 498 252 000 km) → 500 su.
     * Sun half-size ≈ 0.077 su, Mercury orbit ≈ 6.44 su → Sun is ~1.2 % of Mercury's orbital
     * radius, exactly as in reality.
     */
    private static final float SIZE_TO_SCENE = (float)(6_371.0 * SolarSystemScene.PLANET_K);
    /** Segments per orbit circle — higher = smoother. */
    private static final int RING_SEGS = 128;
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

        // Find max orbital radius for scaling (use km-derived scene radii, same as bodyPosition).
        float maxR = 1f;
        for (CelestialBody body : system.bodies) {
            float r = SolarSystemScene.planetSceneRadius(body);
            if (r > maxR) maxR = r;
        }
        // Scale so maxR → DISPLAY_R in block units (scene units → block units).
        state.sceneScale = DISPLAY_R / maxR;

        long snapTime = gameTick; // integer ticks for position snapshot

        // Star — physical half-radius = starSize × Earth_radius × PLANET_K
        Identifier starSprite = system.texture != null
                ? SpaceMapSkyboxRenderer.textureSpriteId(system.texture)
                : SpaceMapSkyboxRenderer.textureSpriteId("omnitech:textures/space/star/sun.png");
        float starHalf = (system.size > 0f ? system.size : 109f) * SIZE_TO_SCENE;
        state.bodies.add(new OrreryRenderState.BodyEntry(
                starSprite, 0f, 0f, 0f, starHalf, 0xFFFFCC44));

        // Planets + moons
        for (CelestialBody planet : system.bodies) {
            Vector3f pPos = SolarSystemScene.bodyPosition(planet, snapTime);

            int planetColor = bodyColor(planet.id);
            Identifier planetSprite = planet.texture != null
                    ? SpaceMapSkyboxRenderer.textureSpriteId(planet.texture) : null;
            float planetHalf = (planet.size > 0f ? planet.size : 1.0f) * SIZE_TO_SCENE;
            state.bodies.add(new OrreryRenderState.BodyEntry(
                    planetSprite, pPos.x, pPos.y, pPos.z, planetHalf, planetColor));

            // Orbit ring for planet — radius matches bodyPosition exactly
            state.orbits.add(new OrreryRenderState.OrbitEntry(
                    SolarSystemScene.planetSceneRadius(planet),
                    planet.orbital_inclination, planet.ascending_node,
                    (planetColor & 0x00FFFFFF) | 0x55000000));

            // Moons
            if (planet.moons != null) {
                for (CelestialBody moon : planet.moons) {
                    Vector3f mOff = SolarSystemScene.moonOffset(moon, snapTime);
                    Identifier moonSprite = moon.texture != null
                            ? SpaceMapSkyboxRenderer.textureSpriteId(moon.texture) : null;
                    float moonHalf = (moon.size > 0f ? moon.size : 0.27f) * SIZE_TO_SCENE;
                    state.bodies.add(new OrreryRenderState.BodyEntry(
                            moonSprite,
                            pPos.x + mOff.x, pPos.y + mOff.y, pPos.z + mOff.z,
                            moonHalf, 0xFFAAAAAA));
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

        // ── Orbit lines ────────────────────────────────────────────────────────
        nodes.submitCustomGeometry(pose, RenderTypes.LINES, (p, buf) -> {
            for (OrreryRenderState.OrbitEntry ring : state.orbits) {
                for (int seg = 0; seg < RING_SEGS; seg++) {
                    float t1 = (float)(2 * Math.PI *  seg      / RING_SEGS);
                    float t2 = (float)(2 * Math.PI * (seg + 1) / RING_SEGS);
                    Vector3f q1 = SolarSystemScene.cartesian(ring.r(), t1,
                            ring.inclinationDeg(), ring.ascendingNodeDeg());
                    Vector3f q2 = SolarSystemScene.cartesian(ring.r(), t2,
                            ring.inclinationDeg(), ring.ascendingNodeDeg());
                    float x1 = q1.x * s, y1 = q1.y * s, z1 = q1.z * s;
                    float x2 = q2.x * s, y2 = q2.y * s, z2 = q2.z * s;
                    float dx = x2-x1, dy = y2-y1, dz = z2-z1;
                    float len = Math.max(1e-6f, (float)Math.sqrt(dx*dx + dy*dy + dz*dz));
                    float nx = dx/len, ny = dy/len, nz = dz/len;
                    buf.addVertex(p, x1, y1, z1).setColor(0xFFFFFFFF).setNormal(p, nx, ny, nz).setLineWidth(2.0f);
                    buf.addVertex(p, x2, y2, z2).setColor(0xFFFFFFFF).setNormal(p, nx, ny, nz).setLineWidth(2.0f);
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
