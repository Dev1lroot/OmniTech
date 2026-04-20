package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the inner fluid volume inside a {@link FluidTankBlock}.
 *
 * <p>Uses the NeoForge {@link FluidStateModelSet} to look up each fluid's real
 * still-texture sprite and tint colour so the tank shows proper water/lava
 * textures instead of placeholder coloured blocks.
 *
 * <p>Two rendering modes depending on fluid density:
 * <ul>
 *   <li><b>Liquid</b> (density {@literal >} 0): fills from the bottom up to
 *       {@code fillLevel}. Alpha is fixed (75 % for translucent fluids such as
 *       water, 100 % for opaque ones such as lava).</li>
 *   <li><b>Gas</b> (density {@literal ≤} 0, {@link FluidType#isLighterThanAir()}):
 *       fills the full inner height; alpha scales with {@code fillLevel} so an
 *       almost-empty tank looks nearly transparent.</li>
 * </ul>
 */
public class FluidTankRenderer
        implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderState> {

    // Inner cube boundaries – 1 px inset from each block face (1/16 = 0.0625)
    private static final float X0 = 0.02f / 16f, X1 = 15.97f / 16f;
    private static final float Z0 = 0.02f / 16f, Z1 = 15.97f / 16f;
    private static final float Y_BOT = 0.002f;           // lift off the bottom face slightly
    private static final float Y_TOP = 1f - 0.002f;      // stay clear of the top face

    // Max alpha for gas at full tank (≈ 80 %)
    private static final int GAS_ALPHA_MAX = 204;
    // Alpha for translucent liquids (≈ 75 %)
    private static final int LIQUID_TRANSLUCENT_ALPHA = 191;

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public FluidTankRenderState createRenderState() {
        return new FluidTankRenderState();
    }

    @Override
    public void extractRenderState(
            FluidTankBlockEntity entity,
            FluidTankRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        FluidStack fluid = entity.getFluid();
        if (fluid.isEmpty()) {
            state.fillLevel = 0f;
            state.stillSprite = null;
            return;
        }

        state.fillLevel = (float) fluid.getAmount() / FluidTankBlockEntity.CAPACITY;

        // Look up the fluid's baked model from the model manager
        FluidStateModelSet modelSet = Minecraft.getInstance()
                .getModelManager()
                .getFluidStateModelSet();
        FluidModel fluidModel = modelSet.get(fluid.getFluid().defaultFluidState());

        state.stillSprite = fluidModel.stillMaterial().sprite();
        state.isTranslucent = fluidModel.layer() == ChunkSectionLayer.TRANSLUCENT;
        state.isGas = fluid.getFluid().getFluidType().isLighterThanAir();

        // Tint colour from the fluid type (e.g. biome-aware water blue)
        if (fluidModel.fluidTintSource() != null) {
            state.tintARGB = fluidModel.fluidTintSource().colorAsStack(fluid);
        } else {
            state.tintARGB = -1; // white – no tint
        }

        // Light: full-bright for self-illuminating fluids (lava etc.), otherwise from level
        if (fluid.getFluid().getFluidType().getLightLevel() > 0) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        } else if (entity.getLevel() instanceof ClientLevel clientLevel) {
            state.lightCoords = LevelRenderer.getLightCoords(clientLevel, entity.getBlockPos());
        } else {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(FluidTankRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (state.stillSprite == null || state.fillLevel <= 0f) return;

        final TextureAtlasSprite sprite = state.stillSprite;
        final float u0 = sprite.getU0(), u1 = sprite.getU1();
        final float v0 = sprite.getV0(), v1 = sprite.getV1();

        // Vertical extent of the rendered volume
        final float yBot = Y_BOT;
        final float yTop;
        if (state.isGas) {
            yTop = Y_TOP;   // gas always fills the full inner height
        } else {
            yTop = Math.min(Y_BOT + state.fillLevel * (Y_TOP - Y_BOT), Y_TOP);
            if (yTop <= yBot) return;
        }

        // ARGB colour to tint each vertex with
        final int r = ARGB.red(state.tintARGB);
        final int g = ARGB.green(state.tintARGB);
        final int b = ARGB.blue(state.tintARGB);
        final int alpha;
        if (state.isGas) {
            alpha = Math.max(1, (int)(state.fillLevel * GAS_ALPHA_MAX));
        } else if (state.isTranslucent) {
            alpha = LIQUID_TRANSLUCENT_ALPHA;
        } else {
            alpha = 0xFF;
        }
        final int color = ARGB.color(alpha, r, g, b);
        final int light = state.lightCoords;

        // Render type: translucent for gas/water, solid for lava etc.
        var renderType = (state.isGas || state.isTranslucent)
                ? RenderTypes.entityTranslucent(sprite.atlasLocation())
                : RenderTypes.entitySolid(sprite.atlasLocation());

        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buf) -> {
            // Top face (+Y normal) – still texture flat on the surface
            quad(pose, buf,
                    X0, yTop, Z0,   u0, v0,
                    X0, yTop, Z1,   u0, v1,
                    X1, yTop, Z1,   u1, v1,
                    X1, yTop, Z0,   u1, v0,
                    color, light, 0, 1, 0);

            // Bottom face (-Y normal)
            quad(pose, buf,
                    X1, yBot, Z0,   u0, v0,
                    X1, yBot, Z1,   u0, v1,
                    X0, yBot, Z1,   u1, v1,
                    X0, yBot, Z0,   u1, v0,
                    color, light, 0, -1, 0);

            // North face (-Z normal) – viewed from -Z
            quad(pose, buf,
                    X0, yTop, Z0,   u0, v0,
                    X1, yTop, Z0,   u1, v0,
                    X1, yBot, Z0,   u1, v1,
                    X0, yBot, Z0,   u0, v1,
                    color, light, 0, 0, -1);

            // South face (+Z normal) – viewed from +Z
            quad(pose, buf,
                    X1, yTop, Z1,   u0, v0,
                    X0, yTop, Z1,   u1, v0,
                    X0, yBot, Z1,   u1, v1,
                    X1, yBot, Z1,   u0, v1,
                    color, light, 0, 0, 1);

            // West face (-X normal) – viewed from -X
            quad(pose, buf,
                    X0, yTop, Z1,   u0, v0,
                    X0, yTop, Z0,   u1, v0,
                    X0, yBot, Z0,   u1, v1,
                    X0, yBot, Z1,   u0, v1,
                    color, light, -1, 0, 0);

            // East face (+X normal) – viewed from +X
            quad(pose, buf,
                    X1, yTop, Z0,   u0, v0,
                    X1, yTop, Z1,   u1, v0,
                    X1, yBot, Z1,   u1, v1,
                    X1, yBot, Z0,   u0, v1,
                    color, light, 1, 0, 0);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Emits one quad (4 vertices, CCW from outside) into the given buffer.
     *
     * <p>UV corners are mapped: v0→(u0,v0), v1→(u0→uX), v2→..., v3→... per the
     * per-vertex UV parameters passed in.
     */
    private static void quad(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float u0a, float v0a,
            float x1, float y1, float z1, float u1a, float v1a,
            float x2, float y2, float z2, float u2a, float v2a,
            float x3, float y3, float z3, float u3a, float v3a,
            int color, int light, float nx, float ny, float nz) {

        buf.addVertex(pose, x0, y0, z0).setColor(color)
                .setUv(u0a, v0a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(color)
                .setUv(u1a, v1a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(color)
                .setUv(u2a, v2a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(color)
                .setUv(u3a, v3a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }
}
