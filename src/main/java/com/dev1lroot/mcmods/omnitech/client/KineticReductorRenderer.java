/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Renders three copies of the axis gear assembly (kf_reductor_axis model), each spinning
 * around a different spatial axis (X, Y, Z) simultaneously.  This makes the reductor
 * visually convey that it transfers KF from/to any direction.
 *
 * <p>The casing is rendered by the standard block pipeline (getRenderShape=MODEL on the block).
 * This BER only adds the spinning inner axes on top.
 */
public class KineticReductorRenderer
        implements BlockEntityRenderer<KineticReductorBlockEntity, KineticReductorRenderState> {

    public KineticReductorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public KineticReductorRenderState createRenderState() {
        return new KineticReductorRenderState();
    }

    @Override
    public void extractRenderState(
            KineticReductorBlockEntity entity,
            KineticReductorRenderState state,
            float partialTicks,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, crumbling);

        boolean powered = entity.getBlockState().getValue(KineticReductorBlock.POWERED);
        state.powered = powered;

        if (powered && entity.getLevel() instanceof ClientLevel cl) {
            float speed = entity.getRotationSpeed();
            state.rotationAngle = speed > 0f
                    ? ((cl.getGameTime() + partialTicks) * speed) % 360f
                    : 0f;

            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos    = entity.getBlockPos();
            model.blockPos         = entity.getBlockPos();
            model.blockState       = OmniTechBlocks.KF_REDUCTOR_AXIS.get().defaultBlockState();
            model.biome            = cl.getBiome(entity.getBlockPos());
            model.cardinalLighting = cl.cardinalLighting();
            model.lightEngine      = cl.getLightEngine();
            state.axisModel = model;
        } else {
            state.rotationAngle = 0f;
            state.axisModel     = null;
        }
    }

    @Override
    public void submit(
            KineticReductorRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {

        if (state.axisModel == null) return;

        // Z-axis spin (model's natural orientation)
        renderAxis(poseStack, submitNodeCollector, state.axisModel, state.rotationAngle,
                null, Axis.ZP);

        // X-axis spin — YP.+90 maps model Z→X; spin outermost so it acts on world X
        renderAxis(poseStack, submitNodeCollector, state.axisModel, state.rotationAngle,
                Axis.YP.rotationDegrees(90f), Axis.XP);

        // Y-axis spin — XP.-90 maps model Z→Y; spin outermost so it acts on world Y
        renderAxis(poseStack, submitNodeCollector, state.axisModel, state.rotationAngle,
                Axis.XP.rotationDegrees(-90f), Axis.YP);
    }

    private static void renderAxis(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            MovingBlockRenderState model,
            float angle,
            @Nullable Quaternionf preRotation,
            Axis spinAxis) {

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // Spin must be outermost (first in code) so it acts on the world axis, not the
        // pre-rotated local frame.  Pre-rotation orients the model so the shaft aligns
        // with spinAxis before the spin is applied to the vertex.
        poseStack.mulPose(spinAxis.rotationDegrees(angle));
        if (preRotation != null) {
            poseStack.mulPose(preRotation);
        }
        poseStack.translate(-0.5, -0.5, -0.5);
        collector.submitMovingBlock(poseStack, model);
        poseStack.popPose();
    }
}
