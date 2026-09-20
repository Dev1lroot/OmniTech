/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.ValveBlock;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.ValveBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the spinning wheel overlay on top of the valve's base model.
 *
 * <p>The wheel ghost block ({@code valve_wheel}) is rendered with a combined
 * transform:
 * <ol>
 *   <li>Yaw around Y to align with FACING.</li>
 *   <li>Spin around the forward (Z) axis by {@code LEVEL × 45°}.</li>
 * </ol>
 */
public class ValveRenderer implements BlockEntityRenderer<ValveBlockEntity, ValveRenderState>
{
    public ValveRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public ValveRenderState createRenderState() {
        return new ValveRenderState();
    }

    @Override
    public void extractRenderState(
            ValveBlockEntity entity, ValveRenderState state,
            float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);

        BlockState blockState = entity.getBlockState();
        state.facing   = blockState.getValue(ValveBlock.FACING);
        state.vertical = blockState.getValue(ValveBlock.VERTICAL);
        state.wheelAngle = blockState.getValue(ValveBlock.LEVEL) * 45f;

        if (entity.getLevel() instanceof ClientLevel cl) {
            BlockState wheelState = OmniTechBlocks.VALVE_WHEEL.get().defaultBlockState()
                    .setValue(ValveBlock.FACING,   state.facing)
                    .setValue(ValveBlock.VERTICAL, state.vertical)
                    .setValue(ValveBlock.LEVEL,    blockState.getValue(ValveBlock.LEVEL));

            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos = entity.getBlockPos();
            model.blockPos      = entity.getBlockPos();
            model.blockState    = wheelState;
            model.biome         = cl.getBiome(entity.getBlockPos());
            model.cardinalLighting = cl.cardinalLighting();
            model.lightEngine   = cl.getLightEngine();
            state.wheelModel = model;
        } else {
            state.wheelModel = null;
        }
    }

    @Override
    public void submit(
            ValveRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (state.wheelModel == null) return;

        // Yaw to align the wheel with FACING.
        float yaw = facingYaw(state.facing);

        poseStack.pushPose();
        // Pivot around block center
        poseStack.translate(0.5, 0.5, 0.5);
        // Rotate body so "front" faces the correct direction
        poseStack.rotate(Axis.YP.rotationDegrees(yaw));
        // Spin around the forward (−Z) axis
        poseStack.rotate(Axis.ZP.rotationDegrees(state.wheelAngle));
        poseStack.translate(-0.5, -0.5, -0.5);

        submitNodeCollector.submitMovingBlock(poseStack, state.wheelModel, 0);
        poseStack.popPose();
    }

    /** Returns the Y rotation (degrees) needed to face the given direction. */
    private static float facingYaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case EAST  -> -90f;
            case WEST  -> 90f;
            default    -> 0f;
        };
    }
}
