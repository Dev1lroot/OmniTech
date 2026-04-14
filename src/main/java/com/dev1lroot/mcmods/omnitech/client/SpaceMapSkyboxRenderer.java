package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
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
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Unified sky renderer for all OmniTech space dimensions.
 *
 * <p>For each dimension registered in {@code space_map.json}:
 * <ul>
 *   <li>Renders the star (sun) with a scale derived from the body's orbital distance —
 *       further from the star = smaller sun disc.</li>
 *   <li>If the dimension is a moon, renders the parent planet prominently.</li>
 *   <li>Renders all other planets of the same star system as small distant bodies
 *       moving along the ecliptic at their own orbital speeds.</li>
 * </ul>
 *
 * <p>Register as {@code omnitech:space_sky} and reference from dimension type JSON via
 * {@code "neoforge:custom_skybox": "omnitech:space_sky"}.
 */
public class SpaceMapSkyboxRenderer implements CustomSkyboxRenderer {

    /**
     * Pipeline for all sky body quads (parent planet, distant bodies, custom sun).
     * TRANSLUCENT blend so bodies properly occlude stars instead of additively blending.
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
    /** Earth's orbital_radius in space_map.json — reference distance for scale math. */
    private static final float EARTH_ORBIT_R     = 155f;
    /**
     * Sol's size in Earth-diameter units.  All star sizes in space_map.json use the
     * same unit (Earth = 1.0), so Sol = 109.0.
     */
    private static final float SOL_SIZE          = 109f;
    /**
     * Vanilla sun renders at this scale when viewed from {@link #EARTH_ORBIT_R}.
     * Our custom star disc uses the same baseline, then adjusts for distance + star size.
     */
    private static final float VANILLA_SUN_SCALE = 30f;
    /**
     * Reference orbital radius for moons (Earth's Moon at 90 px in the UI).
     * Moons closer to their parent see a proportionally larger planet.
     */
    private static final float MOON_REF_ORBIT    = 90f;
    /** Base scale for a size-1.0 parent planet seen from {@link #MOON_REF_ORBIT}. */
    private static final float BASE_PARENT_SCALE = 40f;
    /** Base scale for a size-1.0 distant body one {@link #EARTH_ORBIT_R} unit away. */
    private static final float BASE_DISTANT_SCALE = 3f;

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
    // Space-map lookup
    // -------------------------------------------------------------------------

    /**
     * Full context for the current dimension: the body itself, its parent planet
     * (if on a moon, otherwise null), and the star system it belongs to.
     */
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
     * Convert a space_map texture path such as {@code "omnitech:textures/space/planet/earth.png"}
     * to the atlas sprite ID {@code "omnitech:space/planet/earth"}.
     *
     * <p>The atlas source in {@code atlases/celestials.json} uses
     * {@code "source": "space", "prefix": "space/"}, so all files under
     * {@code textures/space/} are registered with the {@code space/} prefix.
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
     * <p>The caller is responsible for orienting the ecliptic plane on {@code poseStack}
     * (typically a {@code -90° Y} rotation matching vanilla).  This method then applies
     * the body's orbital angle around the X axis and positions it at the canonical sky
     * distance (Y = 100 in model-view space).
     *
     * @param spriteId  celestials-atlas sprite ID
     * @param angle     orbital position in radians (X-axis rotation inside the ecliptic plane)
     * @param scale     half-size of the quad in world units
     * @param brightness alpha/brightness multiplier (1.0 = fully opaque)
     * @param poseStack pre-oriented stack (ecliptic plane already set up)
     */
    private void renderBody(Identifier spriteId, float angle, float scale,
                            float brightness, PoseStack poseStack) {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();
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

        // --- Resolve current solar distance ---
        // For moons, we share the parent planet's orbital radius (same solar distance).
        float currentOrbitalRadius = EARTH_ORBIT_R;
        if (loc != null) {
            currentOrbitalRadius = loc.parentPlanet() != null
                    ? loc.parentPlanet().orbital_radius
                    : loc.body().orbital_radius;
            if (currentOrbitalRadius <= 0) currentOrbitalRadius = EARTH_ORBIT_R;
        }

        // --- Star (sun) apparent scale ---
        // Scales with star physical size and inversely with distance.
        // Baseline: Sol (size=109) from Earth (R=155) → vanilla scale 30f.
        float starSize = (loc != null && loc.starSystem() != null && loc.starSystem().size > 0)
                ? loc.starSystem().size : SOL_SIZE;
        float sunScale = clamp(
                VANILLA_SUN_SCALE * (starSize / SOL_SIZE) * EARTH_ORBIT_R / currentOrbitalRadius,
                6f, 70f);

        setupFog.run();
        skyRenderer.renderSkyDisc(0xFF000000);

        PoseStack poseStack = new PoseStack();

        // Pass through only stars — vanilla sun and moon are suppressed.
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
                    1.0f   // stars always fully bright in space
            );
        } finally {
            suppressVanillaMoon = false;
            suppressVanillaSun  = false;
        }

        // All custom bodies share the vanilla ecliptic plane orientation.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));

        // --- Star disc (replaces vanilla sun) ---
        Identifier starSprite = (loc != null && loc.starSystem() != null
                && loc.starSystem().texture != null)
                ? textureSpriteId(loc.starSystem().texture)
                : textureSpriteId("omnitech:textures/space/star/sun.png");
        renderBody(starSprite, skyRenderState.sunAngle, sunScale, 1.0f, poseStack);

        // --- Parent planet (prominent — we are standing on one of its moons) ---
        if (loc != null && loc.parentPlanet() != null) {
            CelestialBody moon   = loc.body();
            CelestialBody parent = loc.parentPlanet();

            float parentSize  = parent.size > 0f ? parent.size : 1.0f;
            float moonOrbit   = moon.orbital_radius > 0 ? moon.orbital_radius : MOON_REF_ORBIT;

            // Apparent scale = base × sqrt(physicalSize) × (referenceOrbit / moonOrbit).
            // sqrt dampens extreme gas-giant sizes to keep Jupiter dramatic but not absurd.
            float parentScale = clamp(
                    BASE_PARENT_SCALE * (float) Math.sqrt(parentSize) * (MOON_REF_ORBIT / moonOrbit),
                    18f, 120f);

            float orbitalSpeed = moon.orbital_speed > 0f ? moon.orbital_speed : 2.0f;
            float parentAngle  = skyRenderState.sunAngle * orbitalSpeed * 0.4f
                    + (float) Math.toRadians(130.0);

            renderBody(textureSpriteId(parent.texture),
                    parentAngle, parentScale, skyRenderState.rainBrightness, poseStack);
        }

        // --- Distant bodies: all other planets in the same star system ---
        if (loc != null && loc.starSystem() != null) {
            String homePlanetId = loc.parentPlanet() != null
                    ? loc.parentPlanet().id : loc.body().id;

            for (CelestialBody planet : loc.starSystem().bodies) {
                if (planet.texture == null) continue;
                if (planet.id.equals(homePlanetId)) continue;

                float bodySize = planet.size > 0f ? planet.size : 1.0f;
                // Distance proxy: difference of orbital radii in the UI space.
                // Planets close in orbital radius appear larger.
                float dist = Math.max(20f, Math.abs(planet.orbital_radius - currentOrbitalRadius));
                float distantScale = clamp(
                        BASE_DISTANT_SCALE * bodySize * (EARTH_ORBIT_R / dist),
                        1.5f, 15f);

                float phaseOffset  = (Math.abs(planet.id.hashCode()) % 628) / 100f;
                float speed        = planet.orbital_speed > 0f ? planet.orbital_speed : 1.0f;
                float distantAngle = skyRenderState.sunAngle * speed * 0.4f + phaseOffset;

                renderBody(textureSpriteId(planet.texture),
                        distantAngle, distantScale, 1.0f, poseStack);
            }
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
