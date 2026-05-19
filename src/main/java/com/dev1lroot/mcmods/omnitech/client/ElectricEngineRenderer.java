/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ElectricEngineRenderer
        implements BlockEntityRenderer<ElectricEngineBlockEntity, ElectricEngineRenderState> {

    private static final float ROTATION_SPEED = 12f; // degrees per tick

    public ElectricEngineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public ElectricEngineRenderState createRenderState() {
        return new ElectricEngineRenderState();
    }

    @Override
    public void extractRenderState(
            ElectricEngineBlockEntity entity,
            ElectricEngineRenderState state,
            float partialTicks,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, crumbling);

        if (!entity.getBlockState().getValue(ElectricEngineBlock.LIT)) {
            state.statorModel = null;
            return;
        }

        state.facing = entity.getBlockState().getValue(ElectricEngineBlock.FACING);

        if (entity.getLevel() instanceof ClientLevel cl) {
            state.statorAngle = ((cl.getGameTime() + partialTicks) * ROTATION_SPEED) % 360f;

            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos = entity.getBlockPos();
            model.blockPos = entity.getBlockPos();
            model.blockState = OmniTechBlocks.ELECTRIC_ENGINE_STATOR.get().defaultBlockState();
            model.biome = cl.getBiome(entity.getBlockPos());
            model.cardinalLighting = cl.cardinalLighting();
            model.lightEngine = cl.getLightEngine();
            state.statorModel = model;
        } else {
            state.statorModel = null;
        }
    }

    @Override
    public void submit(
            ElectricEngineRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {

        if (state.statorModel == null) return;

        poseStack.pushPose();
        // Pivot at block center (shaft center is at 8,8,8 in model space = 0.5,0.5,0.5)
        poseStack.translate(0.5, 0.5, 0.5);
        // Align the model's +Z shaft axis to match the actual FACING direction
        switch (state.facing) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90f));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90f));
            case UP    -> poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            default    -> {} // NORTH: +Z already aligns with facing
        }
        // Spin around local +Z (which is the shaft axis after alignment)
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.statorAngle));
        poseStack.translate(-0.5, -0.5, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.statorModel);
        poseStack.popPose();
    }
}
