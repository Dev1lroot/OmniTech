/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SolarSystemScene;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Unified sky renderer for all OmniTech space dimensions.
 *
 * <p>Architecture: the entire star system is simulated in heliocentric 3-D space
 * via {@link SolarSystemScene}. For each frame the renderer:
 * <ol>
 *   <li>Computes each body's 3-D heliocentric position at the current game time.</li>
 *   <li>Derives the direction vector from the viewer's planet to every other body.</li>
 *   <li>Applies the planet's diurnal rotation (day/night = planet spin) so that
 *       all bodies rise and set correctly, exactly as the vanilla sun does.</li>
 *   <li>Orients each billboard quad toward that direction via a quaternion rotation,
 *       then translates to a fixed sky-sphere radius and scales by apparent angular size.</li>
 * </ol>
 *
 * <p>This eliminates all 2-D projection artefacts: bodies never "disappear" because
 * a direction vector is always well-defined, and every body participates in the
 * diurnal cycle automatically.
 */
public class SpaceMapSkyboxRenderer implements CustomSkyboxRenderer {

    /** Pipeline for all sky body quads — TRANSLUCENT blend for correct occlusion. */
    public static final RenderPipeline SKY_BODY_PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath(OmniTech.MODID, "pipeline/sky_body"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    // ---- Scale constants -------------------------------------------------------

    /**
     * Converts a physical size (Earth-radius units) to scene-space units —
     * matches the orrery renderer exactly.
     */
    private static final float SIZE_TO_SCENE  = (float)(6_371.0 * SolarSystemScene.PLANET_K);
    /** Sky-sphere radius (units). Bodies are translated to this distance from the camera. */
    private static final float SKY_SPHERE_R   = 100f;
    /** Angular size multiplier applied to the star. */
    private static final float STAR_SIZE_MULT  = 20f;
    /** Angular size multiplier applied to planets and moons. */
    private static final float PLANET_SIZE_MULT = 400f;

    // --- Mixin suppression flags (read by SkyRendererMixin) ---
    private static boolean suppressVanillaMoon = false;
    private static boolean suppressVanillaSun  = false;

    /** Cached GPU quad buffers keyed by atlas sprite ID. */
    private final Map<Identifier, GpuBuffer> bodyBuffers = new HashMap<>();
    private final RenderSystem.AutoStorageIndexBuffer quadIndices =
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

    public static boolean isSuppressingVanillaMoon() { return suppressVanillaMoon; }
    public static boolean isSuppressingVanillaSun()  { return suppressVanillaSun;  }

    // -------------------------------------------------------------------------
    // Render task record
    // -------------------------------------------------------------------------

    /**
     * A single body to draw in the sky.
     *
     * @param spriteId      celestials-atlas sprite
     * @param direction     unit vector from the viewer toward this body, in sky space
     *                      (already has the planet's diurnal rotation applied)
     * @param scale         half-width of the billboard quad in model units
     * @param brightness    alpha multiplier
     * @param viewerDistKm  viewer → body distance for z-sorting (farthest first)
     */
    private record SkyBodyRenderTask(
            Identifier spriteId,
            Vector3f   direction,
            float      scale,
            float      brightness,
            long       viewerDistKm,
            float      axialAngle
    ) {}

    // -------------------------------------------------------------------------
    // Space-map lookup
    // -------------------------------------------------------------------------

    private record BodyLocation(CelestialBody body, CelestialBody parentPlanet, StarSystem starSystem) {}

    private BodyLocation findCurrentLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        String dimId = mc.level.dimension().identifier().toString();

        SpaceMap spaceMap = SpaceMapLoader.load(mc.getResourceManager());
        if (spaceMap.galaxies == null) return null;

        for (Galaxy galaxy : spaceMap.galaxies) {
            if (galaxy.star_systems == null) continue;
            for (StarSystem system : galaxy.star_systems) {
                if (system.bodies == null) continue;
                for (CelestialBody planet : system.bodies) {
                    if (dimId.equals(planet.dimension))
                        return new BodyLocation(planet, null, system);
                    if (planet.moons != null) {
                        for (CelestialBody moon : planet.moons) {
                            if (dimId.equals(moon.dimension))
                                return new BodyLocation(moon, planet, system);
                        }
                    }
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Atlas sprite helper
    // -------------------------------------------------------------------------

    static Identifier textureSpriteId(String texturePath) {
        int colon = texturePath.indexOf(':');
        String ns   = colon >= 0 ? texturePath.substring(0, colon) : OmniTech.MODID;
        String path = colon >= 0 ? texturePath.substring(colon + 1) : texturePath;
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png"))         path = path.substring(0, path.length() - ".png".length());
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    // -------------------------------------------------------------------------
    // GPU buffer management
    // -------------------------------------------------------------------------

    private GpuBuffer getOrCreateBuffer(Identifier spriteId) {
        return bodyBuffers.computeIfAbsent(spriteId, id -> {
            TextureAtlas atlas = Minecraft.getInstance()
                    .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
            TextureAtlasSprite sprite = atlas.getSprite(id);
            float u0 = sprite.getU0(), u1 = sprite.getU1();
            float v0 = sprite.getV0(), v1 = sprite.getV1();

            try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(
                    24 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
                BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.QUADS,
                        DefaultVertexFormat.POSITION_TEX);
                // +Y
                buf.addVertex(-1f,+1f,-1f).setUv(u0,v0);
                buf.addVertex(+1f,+1f,-1f).setUv(u1,v0);
                buf.addVertex(+1f,+1f,+1f).setUv(u1,v1);
                buf.addVertex(-1f,+1f,+1f).setUv(u0,v1);
                // -Y
                buf.addVertex(-1f,-1f,+1f).setUv(u0,v0);
                buf.addVertex(+1f,-1f,+1f).setUv(u1,v0);
                buf.addVertex(+1f,-1f,-1f).setUv(u1,v1);
                buf.addVertex(-1f,-1f,-1f).setUv(u0,v1);
                // +Z
                buf.addVertex(+1f,-1f,+1f).setUv(u0,v0);
                buf.addVertex(-1f,-1f,+1f).setUv(u1,v0);
                buf.addVertex(-1f,+1f,+1f).setUv(u1,v1);
                buf.addVertex(+1f,+1f,+1f).setUv(u0,v1);
                // -Z
                buf.addVertex(-1f,-1f,-1f).setUv(u0,v0);
                buf.addVertex(+1f,-1f,-1f).setUv(u1,v0);
                buf.addVertex(+1f,+1f,-1f).setUv(u1,v1);
                buf.addVertex(-1f,+1f,-1f).setUv(u0,v1);
                // +X
                buf.addVertex(+1f,-1f,-1f).setUv(u0,v0);
                buf.addVertex(+1f,-1f,+1f).setUv(u1,v0);
                buf.addVertex(+1f,+1f,+1f).setUv(u1,v1);
                buf.addVertex(+1f,+1f,-1f).setUv(u0,v1);
                // -X
                buf.addVertex(-1f,-1f,+1f).setUv(u0,v0);
                buf.addVertex(-1f,-1f,-1f).setUv(u1,v0);
                buf.addVertex(-1f,+1f,-1f).setUv(u1,v1);
                buf.addVertex(-1f,+1f,+1f).setUv(u0,v1);
                try (MeshData mesh = buf.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(
                            () -> "Sky body cube [" + id + "]", 32, mesh.vertexBuffer());
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Direction helpers
    // -------------------------------------------------------------------------

    /**
     * Normalizes {@code raw} and applies the planet's diurnal rotation around Z.
     *
     * <p>Minecraft's diurnal arc runs east (+X) → zenith (+Y) → west (-X), which is
     * rotation in the XY plane, i.e. around the Z axis.  Rotating every sky direction
     * by {@code -sunAngle} around Z achieves this: bodies rise in the east, transit
     * overhead, set in the west, exactly as the vanilla sun does.
     *
     * @param raw    direction vector from viewer to body in inertial (heliocentric) space
     * @param sinRot {@code sin(-sunAngle)} — pre-computed to avoid repeated trig
     * @param cosRot {@code cos(-sunAngle)}
     */
    private static Vector3f skyDir(Vector3f raw, float sinRot, float cosRot) {
        if (raw.lengthSquared() < 1e-10f) return new Vector3f(0f, 1f, 0f);
        raw.normalize();
        // Orbits lie in the XZ plane, so rotate X and Z (not X and Y) to produce
        // elevation change; heliocentric Y (inclination tilt) stays as sky Z.
        float x2 = raw.x * cosRot - raw.z * sinRot;
        float y2 = raw.x * sinRot + raw.z * cosRot;
        return new Vector3f(x2, y2, raw.y);
    }

    // -------------------------------------------------------------------------
    // Core render primitive — direction-based billboard
    // -------------------------------------------------------------------------

    /**
     * Renders a single textured billboard quad oriented so its +Y axis points
     * toward {@code direction}. The quad is translated to the canonical sky-sphere
     * radius (100 units) and scaled by {@code scale}.
     */
    private void renderBodyDir(Identifier spriteId, Vector3f direction, float scale,
                                float brightness, float axialAngle, PoseStack poseStack) {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();

        // Spin around world Y first (planet's polar axis in inertial space).
        // This must happen before the direction quaternion so it is a world-space
        // rotation (texture scrolls horizontally), not a local Roll.
        poseStack.mulPose(Axis.YP.rotation(axialAngle));

        Vector3f up = new Vector3f(0f, 1f, 0f);
        float dot = direction.dot(up);
        if (dot < 0.9999f && dot > -0.9999f) {
            Quaternionf q = new Quaternionf().rotationTo(up, direction);
            poseStack.mulPose(q);
        } else if (dot < 0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180f));
        }

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(poseStack.last().pose());
        mv.translate(0.0f, SKY_SPHERE_R, 0.0f);
        mv.scale(scale, 1.0f, scale);

        GpuBufferSlice dyn = RenderSystem.getDynamicUniforms()
                .writeTransform(mv, new Vector4f(1.0f, 1.0f, 1.0f, brightness),
                        new Vector3f(), new Matrix4f());
        RenderTarget mainRenderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTextureView color = mainRenderTarget.getColorTextureView();
        GpuTextureView depth = mainRenderTarget.getDepthTextureView();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Sky body [" + spriteId + "]",
                        color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(SKY_BODY_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, getOrCreateBuffer(spriteId).slice());
            pass.setIndexBuffer(quadIndices.getBuffer(36), quadIndices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }

        mv.popMatrix();
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // renderSky
    // -------------------------------------------------------------------------

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
                              Matrix4fc modelViewMatrix, Runnable setupFog) {
        SkyRenderer skyRenderer = getSkyRenderer();
        if (skyRenderer == null) return false;

        BodyLocation loc = findCurrentLocation();

        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getOverworldClockTime() : 0;

        setupFog.run();
        skyRenderer.renderSkyDisc(0xFF000000);

        PoseStack poseStack = new PoseStack();

        // Pass through vanilla stars; suppress vanilla sun and moon (we draw our own).
        suppressVanillaMoon = true;
        suppressVanillaSun  = true;
        try {
            skyRenderer.renderSunMoonAndStars(
                    poseStack,
                    skyRenderState.sunAngle,
                    skyRenderState.moonAngle,
                    skyRenderState.starAngle,
                    skyRenderState.moonPhase,
                    skyRenderState.rainBrightness,
                    1.0f);
        } finally {
            suppressVanillaMoon = false;
            suppressVanillaSun  = false;
        }

        if (loc == null) return true; // unknown dimension — nothing else to draw

        // ── Viewer's heliocentric position ────────────────────────────────────
        CelestialBody viewerPlanet = loc.parentPlanet() != null ? loc.parentPlanet() : loc.body();
        Vector3f viewerPos = SolarSystemScene.bodyPosition(viewerPlanet, gameTime);
        if (loc.parentPlanet() != null) {
            // On a moon: offset from the parent planet
            viewerPos.add(SolarSystemScene.moonOffset(loc.body(), gameTime));
        }

        // Diurnal rotation: sunAngle=0 at noon (sun at zenith), π/2 at sunset, π at midnight.
        // skyDir rotates in the XY plane (around Z) by angle (SA - π/2), matching vanilla's
        // Ry(-90)×Rx(SA) transform: cosRot=sin(SA), sinRot=-cos(SA).
        float sinRot = (float) -Math.cos(skyRenderState.sunAngle);
        float cosRot = (float)  Math.sin(skyRenderState.sunAngle);

        // ── Star apparent scale ───────────────────────────────────────────────
        float starSize = loc.starSystem() != null && loc.starSystem().size > 0
                ? loc.starSystem().size : 109f;
        float starPhysHalf = starSize * SIZE_TO_SCENE;
        float starSceneDist = viewerPos.length(); // viewer to origin (star)
        float sunScale = clamp(
                (starPhysHalf / Math.max(starSceneDist, 1e-3f)) * SKY_SPHERE_R * STAR_SIZE_MULT,
                2f, 70f);

        // ── Collect render tasks ──────────────────────────────────────────────
        List<SkyBodyRenderTask> tasks = new ArrayList<>();

        // Star: direction = from viewer toward origin = normalize(-viewerPos)
        Identifier starSprite = (loc.starSystem() != null && loc.starSystem().texture != null)
                ? textureSpriteId(loc.starSystem().texture)
                : textureSpriteId("omnitech:textures/space/star/sun.png");
        Vector3f starDir = skyDir(new Vector3f(-viewerPos.x, -viewerPos.y, -viewerPos.z),
                sinRot, cosRot);
        long starDistKm = (long)(starSceneDist / SolarSystemScene.PLANET_K);
        tasks.add(new SkyBodyRenderTask(starSprite, starDir, sunScale, 1.0f, starDistKm,
                SolarSystemScene.axialAngle(loc.starSystem(), gameTime)));

        // Parent planet (when standing on a moon)
        if (loc.parentPlanet() != null) {
            CelestialBody parent = loc.parentPlanet();
            Vector3f parentPos = SolarSystemScene.bodyPosition(parent, gameTime);
            Vector3f parentDir = skyDir(new Vector3f(parentPos).sub(viewerPos), sinRot, cosRot);

            float parentPhysHalf = (parent.size > 0f ? parent.size : 1.0f) * SIZE_TO_SCENE;
            long  parentDistKm   = loc.body().parent_distance_km > 0
                    ? loc.body().parent_distance_km : 384_400L;
            float parentSceneDist = (float)(parentDistKm * SolarSystemScene.PLANET_K);
            float parentScale = clamp(
                    (parentPhysHalf / Math.max(parentSceneDist, 1e-3f)) * SKY_SPHERE_R * PLANET_SIZE_MULT,
                    10f, 120f);

            tasks.add(new SkyBodyRenderTask(
                    textureSpriteId(parent.texture), parentDir, parentScale,
                    skyRenderState.rainBrightness, parentDistKm,
                    SolarSystemScene.axialAngle(parent, gameTime)));
        }

        // Distant planets
        if (loc.starSystem() != null) {
            String homePlanetId = loc.parentPlanet() != null
                    ? loc.parentPlanet().id : loc.body().id;

            for (CelestialBody planet : loc.starSystem().bodies) {
                if (planet.texture == null || planet.id.equals(homePlanetId)) continue;

                Vector3f planetPos = SolarSystemScene.bodyPosition(planet, gameTime);
                Vector3f toPlanet  = new Vector3f(planetPos).sub(viewerPos);
                float    sceneDistUnits = toPlanet.length();
                Vector3f planetDir = skyDir(toPlanet, sinRot, cosRot);

                float physHalf = (planet.size > 0f ? planet.size : 1.0f) * SIZE_TO_SCENE;
                float distantScale = clamp(
                        (physHalf / Math.max(sceneDistUnits, 1e-3f)) * SKY_SPHERE_R * PLANET_SIZE_MULT,
                        0.5f, 20f);
                long viewerDistKm = (long)(sceneDistUnits / SolarSystemScene.PLANET_K);

                tasks.add(new SkyBodyRenderTask(
                        textureSpriteId(planet.texture), planetDir, distantScale, 1.0f, viewerDistKm,
                        SolarSystemScene.axialAngle(planet, gameTime)));
            }
        }

        // Sort farthest first (painter's algorithm — closer bodies paint over farther ones)
        tasks.sort((a, b) -> Long.compare(b.viewerDistKm(), a.viewerDistKm()));

        for (SkyBodyRenderTask task : tasks) {
            renderBodyDir(task.spriteId(), task.direction(), task.scale(),
                    task.brightness(), task.axialAngle(), poseStack);
        }

        if (skyRenderState.shouldRenderDarkDisc) skyRenderer.renderDarkDisc();
        return true;
    }

    // -------------------------------------------------------------------------
    // Reflection helpers (also used by SkyRendererMixin)
    // -------------------------------------------------------------------------

    private static SkyRenderer getSkyRenderer() {
        try {
            Field f = net.minecraft.client.renderer.LevelRenderer.class
                    .getDeclaredField("skyRenderer");
            f.setAccessible(true);
            return (SkyRenderer) f.get(Minecraft.getInstance().levelRenderer);
        } catch (Exception e) {
            return null;
        }
    }

    public static void invokeMoonRender(SkyRenderer instance, MoonPhase moonPhase,
                                         float rainBrightness, PoseStack poseStack) {
        invokePrivate(instance, "renderMoon",
                new Class[]{MoonPhase.class, float.class, PoseStack.class},
                new Object[]{moonPhase, rainBrightness, poseStack});
    }

    public static void invokeSunRender(SkyRenderer instance, float rainBrightness, PoseStack poseStack) {
        invokePrivate(instance, "renderSun",
                new Class[]{float.class, PoseStack.class},
                new Object[]{rainBrightness, poseStack});
    }

    private static void invokePrivate(Object target, String name, Class<?>[] params, Object[] args) {
        try {
            Method m = SkyRenderer.class.getDeclaredMethod(name, params);
            m.setAccessible(true);
            m.invoke(target, args);
        } catch (Exception e) {
            // silently skip
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
