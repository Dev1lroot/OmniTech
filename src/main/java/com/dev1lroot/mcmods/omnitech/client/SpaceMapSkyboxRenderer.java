/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
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
 * <p>For each dimension registered in {@code space_map.json}:
 * <ul>
 *   <li>Renders the star with distance-scaled apparent size.</li>
 *   <li>If standing on a moon, renders the parent planet prominently.</li>
 *   <li>Renders all other system planets as distant bodies.</li>
 *   <li>Bodies are sorted by viewer distance (farthest first — painter's algorithm),
 *       so closer bodies correctly occlude farther ones (e.g. Jupiter can cover the
 *       Sun from Europa; Earth covers the Sun from the Moon).</li>
 *   <li>Every body's orbital plane is tilted by its real orbital inclination (i) and
 *       ascending node (Ω), matching the known values from our solar system.</li>
 * </ul>
 */
public class SpaceMapSkyboxRenderer implements CustomSkyboxRenderer {

    /**
     * Pipeline for all sky body quads.
     * TRANSLUCENT blend so closer bodies correctly occlude farther ones.
     */
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
    /** Base scale for a parent planet with Earth's diameter at Earth-Moon distance (384,400 km). */
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
    // Render task record — one per visible sky body
    // -------------------------------------------------------------------------

    /**
     * Describes a single body to draw in the sky.
     * Bodies are collected into a list, sorted by {@code viewerDistKm} descending
     * (farthest first), then rendered in order (painter's algorithm).
     *
     * @param spriteId       atlas sprite for this body
     * @param angle          orbital angle in radians (position in its orbit)
     * @param scale          half-width of the quad in model units
     * @param brightness     alpha multiplier (1.0 = fully opaque)
     * @param inclinationDeg orbital inclination (°) relative to ecliptic
     * @param ascendingNodeDeg longitude of ascending node (°) — compass direction of the tilt
     * @param viewerDistKm   viewer's distance from this body in km, used for z-sorting
     */
    private record SkyBodyRenderTask(
            Identifier spriteId,
            float angle,
            float scale,
            float brightness,
            float inclinationDeg,
            float ascendingNodeDeg,
            long viewerDistKm
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
                    if (dimId.equals(planet.dimension)) {
                        return new BodyLocation(planet, null, system);
                    }
                    if (planet.moons != null) {
                        for (CelestialBody moon : planet.moons) {
                            if (dimId.equals(moon.dimension)) {
                                return new BodyLocation(moon, planet, system);
                            }
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

    /**
     * Converts a space_map texture path such as {@code "omnitech:textures/space/planet/earth.png"}
     * to the celestials-atlas sprite ID {@code "omnitech:space/planet/earth"}.
     */
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
    // Core render primitive
    // -------------------------------------------------------------------------

    /**
     * Draw a single textured quad in sky-space.
     *
     * <p>The ecliptic plane must already be oriented on {@code poseStack} before
     * this call (caller applies {@code Axis.YP.rotationDegrees(-90°)}).
     * This method then:
     * <ol>
     *   <li>Rotates by the ascending node (Ω) around the ecliptic pole (Y),
     *       determining the compass direction of the orbital tilt.</li>
     *   <li>Tilts the orbital plane by the inclination (i) around the Z axis.</li>
     *   <li>Rotates by the orbital angle around X to place the body in its orbit.</li>
     *   <li>Translates to the canonical sky distance and scales the quad.</li>
     * </ol>
     *
     * @param spriteId        celestials-atlas sprite ID
     * @param angle           orbital position in radians
     * @param scale           half-size of the quad in world units
     * @param brightness      alpha multiplier (1.0 = fully opaque)
     * @param inclinationDeg  orbital inclination in degrees
     * @param ascendingNodeDeg longitude of ascending node in degrees
     * @param poseStack       pre-oriented stack (ecliptic already set up)
     */
    private void renderBody(Identifier spriteId, float angle, float scale,
                            float brightness, float inclinationDeg, float ascendingNodeDeg,
                            PoseStack poseStack) {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();

        // 1. Ascending node: rotate in the ecliptic plane to the node longitude.
        if (ascendingNodeDeg != 0f) {
            poseStack.mulPose(Axis.YP.rotationDegrees(ascendingNodeDeg));
        }
        // 2. Inclination: tilt the orbital plane by i degrees.
        if (inclinationDeg != 0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(inclinationDeg));
        }
        // 3. Orbital position.
        poseStack.mulPose(Axis.XP.rotation(angle));

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

        // --- Viewer's real km distance from the star ---
        long viewerStarDistKm = TravelDistanceCalculator.SOL_EARTH_DIST_KM;
        if (loc != null) {
            CelestialBody home = loc.parentPlanet() != null ? loc.parentPlanet() : loc.body();
            if (home.orbital_distance_km > 0) viewerStarDistKm = home.orbital_distance_km;
        }

        // --- Star apparent scale ---
        float starSize = (loc != null && loc.starSystem() != null && loc.starSystem().size > 0)
                ? loc.starSystem().size : SOL_SIZE;
        float sunScale = clamp(
                VANILLA_SUN_SCALE * (starSize / SOL_SIZE)
                        * (float) TravelDistanceCalculator.SOL_EARTH_DIST_KM / viewerStarDistKm,
                4f, 70f);

        setupFog.run();
        skyRenderer.renderSkyDisc(0xFF000000);

        PoseStack poseStack = new PoseStack();

        // Pass through vanilla stars only — sun and moon are suppressed.
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
                    1.0f
            );
        } finally {
            suppressVanillaMoon = false;
            suppressVanillaSun  = false;
        }

        // All custom bodies share the vanilla ecliptic plane orientation.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));

        // Collect all render tasks, then sort farthest→closest (painter's algorithm).
        List<SkyBodyRenderTask> tasks = new ArrayList<>();

        // --- Star disc ---
        Identifier starSprite = (loc != null && loc.starSystem() != null
                && loc.starSystem().texture != null)
                ? textureSpriteId(loc.starSystem().texture)
                : textureSpriteId("omnitech:textures/space/star/sun.png");
        // The star has no inclination relative to itself; inclination = 0, node = 0.
        tasks.add(new SkyBodyRenderTask(
                starSprite,
                skyRenderState.sunAngle,
                sunScale,
                1.0f,
                0f, 0f,
                viewerStarDistKm
        ));

        // --- Parent planet (moon viewer only) ---
        if (loc != null && loc.parentPlanet() != null) {
            CelestialBody moon   = loc.body();
            CelestialBody parent = loc.parentPlanet();

            float parentSize = parent.size > 0f ? parent.size : 1.0f;
            float parentScale;
            if (moon.parent_distance_km > 0) {
                parentScale = clamp(
                        BASE_PARENT_SCALE * parentSize
                                * (float) TravelDistanceCalculator.EARTH_MOON_DIST_KM
                                / moon.parent_distance_km,
                        18f, 120f);
            } else {
                float moonOrbit = moon.orbital_radius > 0 ? moon.orbital_radius : 90f;
                parentScale = clamp(
                        BASE_PARENT_SCALE * (float) Math.sqrt(parentSize) * (90f / moonOrbit),
                        18f, 120f);
            }

            float orbitalSpeed = moon.orbital_speed > 0f ? moon.orbital_speed : 2.0f;
            float parentAngle  = skyRenderState.sunAngle * orbitalSpeed * 0.4f
                    + (float) Math.toRadians(130.0);

            long parentDistKm = moon.parent_distance_km > 0 ? moon.parent_distance_km : 384_400L;
            tasks.add(new SkyBodyRenderTask(
                    textureSpriteId(parent.texture),
                    parentAngle,
                    parentScale,
                    skyRenderState.rainBrightness,
                    parent.orbital_inclination,
                    parent.ascending_node,
                    parentDistKm
            ));
        }

        // --- Distant bodies: all other planets in the same star system ---
        if (loc != null && loc.starSystem() != null) {
            String homePlanetId = loc.parentPlanet() != null
                    ? loc.parentPlanet().id : loc.body().id;

            for (CelestialBody planet : loc.starSystem().bodies) {
                if (planet.texture == null) continue;
                if (planet.id.equals(homePlanetId)) continue;

                float bodySize = planet.size > 0f ? planet.size : 1.0f;
                float distantScale;
                long  viewerDistKm;

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
                    float uiDist = Math.max(20f,
                            Math.abs(planet.orbital_radius - (loc.parentPlanet() != null
                                    ? loc.parentPlanet().orbital_radius
                                    : loc.body().orbital_radius)));
                    distantScale = clamp(BASE_DISTANT_SCALE * bodySize * (155f / uiDist), 1f, 15f);
                    viewerDistKm = (long)(uiDist * 1_000_000L); // UI fallback as relative proxy
                }

                float phaseOffset  = (Math.abs(planet.id.hashCode()) % 628) / 100f;
                float speed        = planet.orbital_speed > 0f ? planet.orbital_speed : 1.0f;
                float distantAngle = skyRenderState.sunAngle * speed * 0.4f + phaseOffset;

                tasks.add(new SkyBodyRenderTask(
                        textureSpriteId(planet.texture),
                        distantAngle,
                        distantScale,
                        1.0f,
                        planet.orbital_inclination,
                        planet.ascending_node,
                        viewerDistKm
                ));
            }
        }

        // Sort farthest first so closer bodies paint over farther ones.
        tasks.sort((a, b) -> Long.compare(b.viewerDistKm(), a.viewerDistKm()));

        for (SkyBodyRenderTask task : tasks) {
            renderBody(task.spriteId(), task.angle(), task.scale(), task.brightness(),
                    task.inclinationDeg(), task.ascendingNodeDeg(), poseStack);
        }

        poseStack.popPose();

        if (skyRenderState.shouldRenderDarkDisc) {
            skyRenderer.renderDarkDisc();
        }

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

    /** Called by the mixin to forward vanilla moon rendering when not suppressed. */
    public static void invokeMoonRender(SkyRenderer instance, MoonPhase moonPhase,
                                         float rainBrightness, PoseStack poseStack) {
        invokePrivate(instance, "renderMoon",
                new Class[]{MoonPhase.class, float.class, PoseStack.class},
                new Object[]{moonPhase, rainBrightness, poseStack});
    }

    /** Called by the mixin to forward vanilla sun rendering when not suppressed. */
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
            // silently skip — failing is better than crashing
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
