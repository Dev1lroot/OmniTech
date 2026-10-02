/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticShaftBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticShaftBlockEntity;
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

/**
 * Renders the Kinetic Shaft with a spinning animation while POWERED=true.
 * The model rotates around the shaft's axis at a speed proportional to the network's
 * total KF level, interpolated smoothly using partial ticks.
 */
public class KineticShaftRenderer
        implements BlockEntityRenderer<KineticShaftBlockEntity, KineticShaftRenderState> {

    public KineticShaftRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public KineticShaftRenderState createRenderState() {
        return new KineticShaftRenderState();
    }

    @Override
    public void extractRenderState(
            KineticShaftBlockEntity entity,
            KineticShaftRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        boolean powered = entity.getBlockState().getValue(KineticShaftBlock.POWERED);
        state.powered = powered;
        state.axis    = entity.getBlockState().getValue(KineticShaftBlock.AXIS);

        if (powered && entity.getLevel() instanceof ClientLevel cl) {
            // Drive all shafts from the same global clock so they stay in sync.
            // Speed scales with the network's total KF level.
            float speed = entity.getRotationSpeed();
            state.rotationAngle = speed > 0f
                    ? ((cl.getGameTime() + partialTicks) * speed) % 360f
                    : 0f;
        } else {
            state.rotationAngle = 0f;
        }

        if (entity.getLevel() instanceof ClientLevel clientLevel) {
            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos    = entity.getBlockPos();
            model.blockPos         = entity.getBlockPos();
            model.blockState       = entity.getBlockState();
            model.biome            = clientLevel.getBiome(entity.getBlockPos());
            model.cardinalLighting = clientLevel.cardinalLighting();
            model.lightEngine      = clientLevel.getLightEngine();
            state.pipeModel = model;
        } else {
            state.pipeModel = null;
        }
    }

    @Override
    public void submit(KineticShaftRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.pipeModel == null) return;

        poseStack.pushPose();
        // Rotate around block centre
        poseStack.translate(0.5, 0.5, 0.5);
        if (state.powered) {
            Axis rotAxis = switch (state.axis) {
                case X -> Axis.XP;
                case Y -> Axis.YP;
                case Z -> Axis.ZP;
            };
            poseStack.rotate(rotAxis.rotationDegrees(state.rotationAngle));
        }
        poseStack.translate(-0.5, -0.5, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.pipeModel, 0);
        poseStack.popPose();
    }
}
