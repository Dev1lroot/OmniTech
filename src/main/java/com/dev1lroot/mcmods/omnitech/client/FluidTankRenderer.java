/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.*;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the inner fluid volume inside a {@link FluidTankBlockEntity}.
 *
 * <p>Sprite and fill behaviour are driven by the fluid's computed {@link FluidPhase}
 * (from {@link FluidPhaseUtil} at the stored T/P), overriding density-based heuristics:
 *
 * <ul>
 *   <li><b>LIQUID / SOLID</b> – fills from the bottom; SOLID uses full alpha.</li>
 *   <li><b>VAPOUR / GAS / SUPERCRITICAL / PLASMA</b> – fills the full inner height;
 *       alpha scales with fill-level.</li>
 * </ul>
 *
 * <p>When a fluid has no phase diagram the per-fluid model sprite is used and
 * LIQUID rendering behaviour is applied.
 */
public class FluidTankRenderer
        implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderState> {

    // Inner cube boundaries – 1 px inset from each block face (1/16 = 0.0625)
    private static final float X0 = 0.02f / 16f, X1 = 15.97f / 16f;
    private static final float Z0 = 0.02f / 16f, Z1 = 15.97f / 16f;
    private static final float Y_BOT = 0.002f;
    private static final float Y_TOP = 1f - 0.002f;

    private static final int GAS_ALPHA_MAX  = 204; // ≈ 80 % at full tank
    private static final int LIQUID_TRANSLUCENT_ALPHA = 191; // ≈ 75 %

    /** Phase texture atlas sprite identifier template — filled by {@link FluidPhase#phaseKey()}. */
    private static final String PHASE_STILL_PREFIX = "block/fluid/phase/";
    private static final String PHASE_STILL_SUFFIX = "_still";

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
            state.fillLevel  = 0f;
            state.phase      = null;
            state.stillSprite = null;
            return;
        }

        state.fillLevel = (float) fluid.getAmount() / FluidTankBlockEntity.CAPACITY;

        // ── Phase from T/P ────────────────────────────────────────────────────
        FluidPhysicsRegistry.FluidPhysics physics = FluidPhysicsRegistry.get(fluid.getFluid());
        Integer tempBox     = fluid.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        Integer pressureBox = fluid.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        int tempC       = tempBox     != null ? tempBox     : 20;
        int pressureKPa = pressureBox != null ? pressureBox : 101;

        FluidPhase phase = FluidPhaseUtil.getPhase(tempC, pressureKPa, physics.phaseDiagram());
        state.phase = phase;

        // ── Sprite selection ──────────────────────────────────────────────────
        if (phase != null) {
            // Phase-specific shared texture (stitched via atlases/blocks.json)
            Identifier phaseId = Identifier.fromNamespaceAndPath(OmniTech.MODID,
                    PHASE_STILL_PREFIX + phase.phaseKey() + PHASE_STILL_SUFFIX);
            state.stillSprite = ((TextureAtlas) Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS))
                    .getSprite(phaseId);
        } else {
            // No phase diagram — fall back to the per-fluid registered model sprite
            FluidStateModelSet modelSet = Minecraft.getInstance()
                    .getModelManager()
                    .getFluidStateModelSet();
            FluidModel fluidModel = modelSet.get(fluid.getFluid().defaultFluidState());
            state.stillSprite = fluidModel.stillMaterial().sprite();
            state.isTranslucent = fluidModel.layer() == ChunkSectionLayer.TRANSLUCENT;
        }

        // ── Tint colour ───────────────────────────────────────────────────────
        FluidStateModelSet modelSet = Minecraft.getInstance()
                .getModelManager()
                .getFluidStateModelSet();
        FluidModel fluidModel = modelSet.get(fluid.getFluid().defaultFluidState());
        if (fluidModel.fluidTintSource() != null) {
            state.tintARGB = fluidModel.fluidTintSource().colorAsStack(fluid);
        } else {
            state.tintARGB = -1;
        }

        // ── Light ─────────────────────────────────────────────────────────────
        if (fluid.getFluid().getFluidType().getLightLevel() > 0
                || phase == FluidPhase.PLASMA) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        } else if (entity.getLevel() instanceof ClientLevel clientLevel) {
            state.lightCoords = LightCoordsUtil.getLightCoords(clientLevel, entity.getBlockPos());
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

        // ── Geometry extent ───────────────────────────────────────────────────
        final boolean gasLike = isGasLike(state.phase);
        final boolean solid   = state.phase == FluidPhase.SOLID;

        final float yBot = Y_BOT;
        final float yTop;
        if (gasLike) {
            yTop = Y_TOP; // dispersed phases fill the full inner height
        } else {
            yTop = Math.min(Y_BOT + state.fillLevel * (Y_TOP - Y_BOT), Y_TOP);
            if (yTop <= yBot) return;
        }

        // ── Alpha ─────────────────────────────────────────────────────────────
        final int r = ARGB.red(state.tintARGB);
        final int g = ARGB.green(state.tintARGB);
        final int b = ARGB.blue(state.tintARGB);
        final int alpha;
        if (solid) {
            alpha = 0xFF; // frozen — fully opaque regardless of fill level
        } else if (gasLike) {
            alpha = Math.max(1, (int)(state.fillLevel * GAS_ALPHA_MAX));
        } else if (state.isTranslucent) {
            alpha = LIQUID_TRANSLUCENT_ALPHA;
        } else {
            alpha = 0xFF;
        }
        final int color = ARGB.color(alpha, r, g, b);
        final int light = state.lightCoords;

        // ── Render type ───────────────────────────────────────────────────────
        var renderType = (gasLike || state.isTranslucent)
                ? RenderTypes.entityTranslucent(sprite.atlasLocation())
                : RenderTypes.entitySolid(sprite.atlasLocation());

        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buf) -> {
            quad(pose, buf, X0, yTop, Z0,  u0, v0,  X0, yTop, Z1,  u0, v1,  X1, yTop, Z1,  u1, v1,  X1, yTop, Z0,  u1, v0,  color, light,  0,  1,  0);
            quad(pose, buf, X1, yBot, Z0,  u0, v0,  X1, yBot, Z1,  u0, v1,  X0, yBot, Z1,  u1, v1,  X0, yBot, Z0,  u1, v0,  color, light,  0, -1,  0);
            quad(pose, buf, X0, yTop, Z0,  u0, v0,  X1, yTop, Z0,  u1, v0,  X1, yBot, Z0,  u1, v1,  X0, yBot, Z0,  u0, v1,  color, light,  0,  0, -1);
            quad(pose, buf, X1, yTop, Z1,  u0, v0,  X0, yTop, Z1,  u1, v0,  X0, yBot, Z1,  u1, v1,  X1, yBot, Z1,  u0, v1,  color, light,  0,  0,  1);
            quad(pose, buf, X0, yTop, Z1,  u0, v0,  X0, yTop, Z0,  u1, v0,  X0, yBot, Z0,  u1, v1,  X0, yBot, Z1,  u0, v1,  color, light, -1,  0,  0);
            quad(pose, buf, X1, yTop, Z0,  u0, v0,  X1, yTop, Z1,  u1, v0,  X1, yBot, Z1,  u1, v1,  X1, yBot, Z0,  u0, v1,  color, light,  1,  0,  0);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True for phases that disperse through the container volume (gas-like fill + alpha). */
    private static boolean isGasLike(@Nullable FluidPhase phase) {
        return phase == FluidPhase.VAPOUR
                || phase == FluidPhase.GAS
                || phase == FluidPhase.SUPERCRITICAL
                || phase == FluidPhase.PLASMA;
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float u0a, float v0a,
            float x1, float y1, float z1, float u1a, float v1a,
            float x2, float y2, float z2, float u2a, float v2a,
            float x3, float y3, float z3, float u3a, float v3a,
            int color, int light, float nx, float ny, float nz) {

        buf.addVertex(pose, x0, y0, z0).setColor(color).setUv(u0a, v0a).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(color).setUv(u1a, v1a).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(color).setUv(u2a, v2a).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(color).setUv(u3a, v3a).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
