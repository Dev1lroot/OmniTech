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
 * <p>
 * Reads {@code assets/omnitech/space_map.json} to locate the current dimension,
 * then renders the appropriate parent body (planet from a moon, etc.) using the
 * textures and orbital speeds defined in the space map.
 * <p>
 * Register as {@code omnitech:space_sky} and reference from dimension type JSON via
 * {@code "neoforge:custom_skybox": "omnitech:space_sky"}.
 */
public class SpaceMapSkyboxRenderer implements CustomSkyboxRenderer {

    /**
     * Pipeline for sky body quads — TRANSLUCENT blend so bodies properly occlude
     * sun/stars rather than additively blending over them.
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

    /** Set to true while renderSky is executing so the mixin can skip the vanilla moon disc. */
    private static boolean suppressVanillaMoon = false;

    /** Cached GPU quad buffers keyed by atlas sprite ID. */
    private final Map<Identifier, GpuBuffer> bodyBuffers = new HashMap<>();

    private final RenderSystem.AutoStorageIndexBuffer quadIndices =
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

    public static boolean isSuppressingVanillaMoon() {
        return suppressVanillaMoon;
    }

    // -------------------------------------------------------------------------
    // Space-map lookup
    // -------------------------------------------------------------------------

    /**
     * Minimal record returned by {@link #findCurrentLocation}: the body corresponding
     * to the current dimension, plus its parent planet if we are on a moon.
     */
    private record BodyLocation(CelestialBody body, CelestialBody parentPlanet) {}

    /**
     * Walk the space map and find the entry whose {@code dimension} matches the
     * current level's registry key.  Returns {@code null} if not found.
     */
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
                        return new BodyLocation(planet, null);
                    }
                    if (planet.moons != null) {
                        for (CelestialBody moon : planet.moons) {
                            if (dimId.equals(moon.dimension)) {
                                return new BodyLocation(moon, planet);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Atlas sprite helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a space_map texture path like {@code "omnitech:textures/space/planet/earth.png"}
     * to the corresponding celestials-atlas sprite ID {@code "omnitech:space/planet/earth"}.
     * <p>
     * The atlas source in {@code atlases/celestials.json} is configured with
     * {@code "source": "space", "prefix": "space/"}, so sprites land at
     * {@code namespace:space/<sub-path>}.
     */
    private static Identifier textureSpriteId(String texturePath) {
        int colon = texturePath.indexOf(':');
        String ns = colon >= 0 ? texturePath.substring(0, colon) : OmniTech.MODID;
        String path = colon >= 0 ? texturePath.substring(colon + 1) : texturePath;
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    // -------------------------------------------------------------------------
    // GPU buffer management
    // -------------------------------------------------------------------------

    /** Return (or lazily create) the quad GPU buffer for the given sprite. */
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
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Render a single celestial body quad.
     *
     * @param spriteId  atlas sprite ID (see {@link #textureSpriteId})
     * @param angle     orbital angle in radians, applied around the X axis
     * @param scale     quad half-size in world units (e.g. 45f for Earth from the Moon)
     * @param brightness rain/fade brightness passed to dynamic transforms
     * @param poseStack pose stack already oriented (Y-axis tilt applied by caller)
     */
    private void renderBody(Identifier spriteId, float angle, float scale,
                            float brightness, PoseStack poseStack) {
        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotation(angle));

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0f, 100.0f, 0.0f);
        modelViewStack.scale(scale, 1.0f, scale);

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack,
                        new Vector4f(1.0f, 1.0f, 1.0f, brightness),
                        new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer bodyBuffer = getOrCreateBuffer(spriteId);
        GpuBuffer indexBuffer = quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky body [" + spriteId + "]",
                        color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(SKY_BODY_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            renderPass.setVertexBuffer(0, bodyBuffer);
            renderPass.setIndexBuffer(indexBuffer, quadIndices.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        modelViewStack.popMatrix();
        poseStack.popPose();
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
                              Matrix4fc modelViewMatrix, Runnable setupFog) {
        SkyRenderer skyRenderer = getSkyRenderer();
        if (skyRenderer == null) return false;

        BodyLocation location = findCurrentLocation();

        setupFog.run();

        // Black sky base
        skyRenderer.renderSkyDisc(0xFF000000);

        PoseStack poseStack = new PoseStack();

        // Render vanilla sun + stars; suppress vanilla moon disc via mixin
        suppressVanillaMoon = true;
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
        }

        // Render parent planet when we are on a moon
        if (location != null && location.parentPlanet() != null) {
            CelestialBody moon   = location.body();
            CelestialBody parent = location.parentPlanet();

            Identifier spriteId = textureSpriteId(parent.texture);

            // Use this moon's orbital_speed so the parent drifts at the moon's own period.
            // Fall back to 2.0f (roughly Earth-like) if not set in space_map.
            float orbitalSpeed = moon.orbital_speed > 0f ? moon.orbital_speed : 2.0f;
            // Slow factor keeps the parent from zipping across the sky too fast
            float angle = skyRenderState.sunAngle * orbitalSpeed * 0.4f
                    + (float) Math.toRadians(130.0);

            // Gas giants (multi-moon planets, excluding Earth) dominate the sky
            boolean isGasGiant = parent.hasMoons() && parent.moons.size() > 1;
            float scale = isGasGiant ? 65f : 45f;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            renderBody(spriteId, angle, scale, skyRenderState.rainBrightness, poseStack);
            poseStack.popPose();
        }

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

    /**
     * Called by {@link com.dev1lroot.mcmods.omnitech.mixin.SkyRendererMixin} to forward
     * the vanilla {@code renderMoon} call when the mixin is not suppressing it.
     */
    public static void invokeMoonRender(SkyRenderer instance, MoonPhase moonPhase,
                                         float rainBrightness, PoseStack poseStack) {
        try {
            Method m = SkyRenderer.class.getDeclaredMethod("renderMoon",
                    MoonPhase.class, float.class, PoseStack.class);
            m.setAccessible(true);
            m.invoke(instance, moonPhase, rainBrightness, poseStack);
        } catch (Exception e) {
            // silently skip if reflection fails
        }
    }
}
