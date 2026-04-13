package com.dev1lroot.mcmods.omnitech.client;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class MoonSkyboxRenderer implements CustomSkyboxRenderer {

    private static final Identifier EARTH_SPRITE = Identifier.fromNamespaceAndPath("omnitech", "earth");

    /**
     * Custom pipeline for the Earth quad: uses TRANSLUCENT blend so Earth properly
     * occludes the sun and stars behind it, rather than additively blending with them
     * (which is what the default CELESTIAL pipeline's OVERLAY blend does).
     */
    public static final RenderPipeline EARTH_PIPELINE = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withLocation(Identifier.fromNamespaceAndPath("omnitech", "pipeline/earth"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .build();

    /** Set to true while renderSky is executing so the mixin can skip vanilla moon. */
    private static boolean skipMoon = false;

    // Lazily-built GPU buffer for the Earth quad and index buffer accessor
    private GpuBuffer earthBuffer;
    private final RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

    public static boolean isSkippingMoon() {
        return skipMoon;
    }

    /** Initialize / rebuild the Earth quad GPU buffer from the celestials atlas. */
    private void ensureEarthBuffer() {
        if (earthBuffer != null) return;

        TextureAtlas celestialsAtlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        TextureAtlasSprite sprite = celestialsAtlas.getSprite(EARTH_SPRITE);

        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder buf = new BufferBuilder(bb, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buf.addVertex(-1.0f, 0.0f, -1.0f).setUv(sprite.getU0(), sprite.getV0());
            buf.addVertex( 1.0f, 0.0f, -1.0f).setUv(sprite.getU1(), sprite.getV0());
            buf.addVertex( 1.0f, 0.0f,  1.0f).setUv(sprite.getU1(), sprite.getV1());
            buf.addVertex(-1.0f, 0.0f,  1.0f).setUv(sprite.getU0(), sprite.getV1());
            try (MeshData mesh = buf.buildOrThrow()) {
                earthBuffer = RenderSystem.getDevice().createBuffer(() -> "Moon Earth quad", 32, mesh.vertexBuffer());
            }
        }
    }

    /** Render the Earth quad at the given sky angle (in radians). */
    private void renderEarth(float earthAngle, float brightness, PoseStack poseStack) {
        TextureAtlas celestialsAtlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotation(earthAngle));

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0f, 100.0f, 0.0f);
        modelViewStack.scale(45.0f, 1.0f, 45.0f);   // Earth is 1.5× the size of the sun (30f)

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0f, 1.0f, 1.0f, brightness), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Moon Earth", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(EARTH_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", celestialsAtlas.getTextureView(), celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, earthBuffer);
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

        setupFog.run();
        ensureEarthBuffer();

        // Black sky disc
        skyRenderer.renderSkyDisc(0xFF000000);

        PoseStack poseStack = new PoseStack();

        // Stars always at full brightness, sun rendered, moon suppressed via mixin
        skipMoon = true;
        try {
            skyRenderer.renderSunMoonAndStars(
                    poseStack,
                    skyRenderState.sunAngle,
                    skyRenderState.moonAngle,
                    skyRenderState.starAngle,
                    skyRenderState.moonPhase,
                    skyRenderState.rainBrightness,
                    1.0f   // stars always fully bright
            );
        } finally {
            skipMoon = false;
        }

        // Earth at a different orbital angle than the sun (offset by ~100° on a tilted axis)
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        // Earth moves slower: half the angular speed, perpendicular plane from sun
        float earthAngle = skyRenderState.sunAngle * 2.0f + (float) Math.toRadians(130.0);
        renderEarth(earthAngle, skyRenderState.rainBrightness, poseStack);
        poseStack.popPose();

        if (skyRenderState.shouldRenderDarkDisc) {
            skyRenderer.renderDarkDisc();
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private static SkyRenderer getSkyRenderer() {
        try {
            Field f = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredField("skyRenderer");
            f.setAccessible(true);
            return (SkyRenderer) f.get(Minecraft.getInstance().levelRenderer);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Called by {@link com.dev1lroot.mcmods.omnitech.mixin.SkyRendererMixin} to invoke the
     * private {@code renderMoon} method on the given {@link SkyRenderer} instance.
     */
    public static void invokeMoonRender(SkyRenderer instance, net.minecraft.world.level.MoonPhase moonPhase,
                                         float rainBrightness, PoseStack poseStack) {
        try {
            Method m = SkyRenderer.class.getDeclaredMethod("renderMoon",
                    net.minecraft.world.level.MoonPhase.class, float.class, PoseStack.class);
            m.setAccessible(true);
            m.invoke(instance, moonPhase, rainBrightness, poseStack);
        } catch (Exception e) {
            // silently skip if reflection fails
        }
    }
}
