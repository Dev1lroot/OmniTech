/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge.ManualCentrifugeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge.ManualCentrifugeBlockEntity;
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

public class ManualCentrifugeRenderer implements BlockEntityRenderer<ManualCentrifugeBlockEntity, ManualCentrifugeRenderState> {

    private static final float ROTATION_SPEED = 15f; // degrees per tick

    public ManualCentrifugeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public ManualCentrifugeRenderState createRenderState() {
        return new ManualCentrifugeRenderState();
    }

    @Override
    public void extractRenderState(
            ManualCentrifugeBlockEntity entity,
            ManualCentrifugeRenderState state,
            float partialTicks,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, crumbling);

        if (!entity.getBlockState().getValue(ManualCentrifugeBlock.LIT)) {
            state.rotorModel = null;
            return;
        }

        if (entity.getLevel() instanceof ClientLevel cl) {
            state.rotorAngle = ((cl.getGameTime() + partialTicks) * ROTATION_SPEED) % 360f;

            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos = entity.getBlockPos();
            model.blockPos = entity.getBlockPos();
            model.blockState = OmniTechBlocks.MANUAL_CENTRIFUGE_ROTOR.get().defaultBlockState();
            model.biome = cl.getBiome(entity.getBlockPos());
            model.cardinalLighting = cl.cardinalLighting();
            model.lightEngine = cl.getLightEngine();
            state.rotorModel = model;
        } else {
            state.rotorModel = null;
        }
    }

    @Override
    public void submit(
            ManualCentrifugeRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {

        if (state.rotorModel == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotorAngle));
        poseStack.translate(-0.5, 0.0, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.rotorModel);
        poseStack.popPose();
    }
}
