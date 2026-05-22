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
import com.dev1lroot.mcmods.omnitech.space.TravelDistanceCalculator;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withLocation(Identifier.fromNamespaceAndPath(OmniTech.MODID, "pipeline/sky_body"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build();

    // ---- Scale constants -------------------------------------------------------

    /** Sol's size in Earth-diameter units. */
    private static final float SOL_SIZE           = 109f;
    /** Vanilla sun renders at scale 30f when viewed from 1 AU. */
    private static final float VANILLA_SUN_SCALE  = 30f;
    /** Base scale for a parent planet with Earth's diameter at Earth-Moon distance. */
    private static final float BASE_PARENT_SCALE  = 40f;
    /** Base scale for distant planets at DISTANT_REF_KM with Earth's diameter. */
    private static final float BASE_DISTANT_SCALE = 3f;
    /** Reference distance for distant-body scale normalization (100 M km). */
    private static final long  DISTANT_REF_KM     = 100_000_000L;

    // --- Mixin suppression flags (read by SkyRendererMixin) ---
    private static boolean suppressVanillaMoon = false;
    private static boolean suppressVanillaSun  = false;

    /** Cached GPU quad buffers keyed by atlas sprite ID. */
    private final Map<Identifier, GpuBuffer> bodyBuffers = new HashMap<>();
    private final RenderSystem.AutoStorageIndexBuffer quadIndices =
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

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
            long       viewerDistKm
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

            try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(
                    4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
                BufferBuilder buf = new BufferBuilder(bb, VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_TEX);
                buf.addVertex(-1.0f, 0.0f, -1.0f).setUv(sprite.getU0(), sprite.getV0());
                buf.addVertex( 1.0f, 0.0f, -1.0f).setUv(sprite.getU1(), sprite.getV0());
                buf.addVertex( 1.0f, 0.0f,  1.0f).setUv(sprite.getU1(), sprite.getV1());
                buf.addVertex(-1.0f, 0.0f,  1.0f).setUv(sprite.getU0(), sprite.getV1());
                try (MeshData mesh = buf.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(
                            () -> "Sky body quad [" + id + "]", 32, mesh.vertexBuffer());
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Direction helpers
    // -------------------------------------------------------------------------

    /**
     * Normalizes {@code raw} and applies the planet's diurnal rotation around Y.
     *
     * <p>The planet spins, so in the planet-fixed frame all celestial bodies appear to
     * rotate in the opposite direction. Rotating every sky direction by {@code -sunAngle}
     * around Y achieves this: bodies rise in the east, transit overhead, set in the west,
     * exactly as the vanilla sun does with the same {@code sunAngle} value.
     *
     * @param raw    direction vector from viewer to body in inertial (heliocentric) space
     * @param sinRot {@code sin(-sunAngle)} — pre-computed to avoid repeated trig
     * @param cosRot {@code cos(-sunAngle)}
     */
    private static Vector3f skyDir(Vector3f raw, float sinRot, float cosRot) {
        if (raw.lengthSquared() < 1e-10f) return new Vector3f(0f, 1f, 0f);
        raw.normalize();
        float x2 =  raw.x * cosRot + raw.z * sinRot;
        float z2 = -raw.x * sinRot + raw.z * cosRot;
        return new Vector3f(x2, raw.y, z2);
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
                                float brightness, PoseStack poseStack) {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();

        Vector3f up = new Vector3f(0f, 1f, 0f);
        float dot = direction.dot(up);
        if (dot < 0.9999f && dot > -0.9999f) {
            // General case: shortest-arc quaternion from +Y to direction.
            Quaternionf q = new Quaternionf().rotationTo(up, direction);
            poseStack.mulPose(q);
        } else if (dot < 0f) {
            // Exactly -Y: 180° around X to avoid degenerate rotationTo.
            poseStack.mulPose(Axis.XP.rotationDegrees(180f));
        }
        // dot ≈ +1 → direction ≈ +Y → identity (no rotation needed)

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.mul(poseStack.last().pose());
        mv.translate(0.0f, 100.0f, 0.0f);
        mv.scale(scale, 1.0f, scale);

        GpuBufferSlice dyn = RenderSystem.getDynamicUniforms()
                .writeTransform(mv, new Vector4f(1.0f, 1.0f, 1.0f, brightness),
                        new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Sky body [" + spriteId + "]",
                        color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(SKY_BODY_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, getOrCreateBuffer(spriteId));
            pass.setIndexBuffer(quadIndices.getBuffer(6), quadIndices.type());
            pass.drawIndexed(0, 0, 6, 1);
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
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0;

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

        // Pre-compute the diurnal rotation (planet spin = -sunAngle around Y).
        float sinRot = (float) Math.sin(-skyRenderState.sunAngle);
        float cosRot = (float) Math.cos(-skyRenderState.sunAngle);

        // ── Star apparent scale ───────────────────────────────────────────────
        long viewerStarDistKm = viewerPlanet.orbital_distance_km > 0
                ? viewerPlanet.orbital_distance_km : TravelDistanceCalculator.SOL_EARTH_DIST_KM;
        float starSize = loc.starSystem() != null && loc.starSystem().size > 0
                ? loc.starSystem().size : SOL_SIZE;
        float sunScale = clamp(
                VANILLA_SUN_SCALE * (starSize / SOL_SIZE)
                        * (float) TravelDistanceCalculator.SOL_EARTH_DIST_KM / viewerStarDistKm,
                4f, 70f);

        // ── Collect render tasks ──────────────────────────────────────────────
        List<SkyBodyRenderTask> tasks = new ArrayList<>();

        // Star: direction = from viewer toward origin = normalize(-viewerPos)
        Identifier starSprite = (loc.starSystem() != null && loc.starSystem().texture != null)
                ? textureSpriteId(loc.starSystem().texture)
                : textureSpriteId("omnitech:textures/space/star/sun.png");
        Vector3f starDir = skyDir(new Vector3f(-viewerPos.x, -viewerPos.y, -viewerPos.z),
                sinRot, cosRot);
        tasks.add(new SkyBodyRenderTask(starSprite, starDir, sunScale, 1.0f, viewerStarDistKm));

        // Parent planet (when standing on a moon)
        if (loc.parentPlanet() != null) {
            CelestialBody parent = loc.parentPlanet();
            Vector3f parentPos = SolarSystemScene.bodyPosition(parent, gameTime);
            Vector3f parentDir = skyDir(new Vector3f(parentPos).sub(viewerPos), sinRot, cosRot);

            float parentSize  = parent.size > 0f ? parent.size : 1.0f;
            long  parentDistKm = loc.body().parent_distance_km > 0
                    ? loc.body().parent_distance_km : 384_400L;
            float parentScale = clamp(
                    BASE_PARENT_SCALE * parentSize
                            * (float) TravelDistanceCalculator.EARTH_MOON_DIST_KM / parentDistKm,
                    18f, 120f);

            tasks.add(new SkyBodyRenderTask(
                    textureSpriteId(parent.texture), parentDir, parentScale,
                    skyRenderState.rainBrightness, parentDistKm));
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

                float bodySize = planet.size > 0f ? planet.size : 1.0f;
                long  viewerDistKm;
                float distantScale;

                if (planet.orbital_distance_km > 0 && viewerStarDistKm > 0) {
                    long orbitDeltaKm = Math.max(1_000_000L,
                            Math.abs(planet.orbital_distance_km - viewerStarDistKm));
                    distantScale = clamp(
                            BASE_DISTANT_SCALE
                                    * (float) Math.pow(bodySize, 0.7)
                                    * (float) Math.pow((double) DISTANT_REF_KM / orbitDeltaKm, 0.4),
                            1.0f, 15f);
                    viewerDistKm = orbitDeltaKm;
                } else {
                    distantScale = clamp(
                            BASE_DISTANT_SCALE * bodySize * (20f / Math.max(1f, sceneDistUnits)),
                            1.0f, 15f);
                    viewerDistKm = (long)(sceneDistUnits * 1_000_000L);
                }

                tasks.add(new SkyBodyRenderTask(
                        textureSpriteId(planet.texture), planetDir, distantScale, 1.0f, viewerDistKm));
            }
        }

        // Sort farthest first (painter's algorithm — closer bodies paint over farther ones)
        tasks.sort((a, b) -> Long.compare(b.viewerDistKm(), a.viewerDistKm()));

        for (SkyBodyRenderTask task : tasks) {
            renderBodyDir(task.spriteId(), task.direction(), task.scale(),
                    task.brightness(), poseStack);
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
