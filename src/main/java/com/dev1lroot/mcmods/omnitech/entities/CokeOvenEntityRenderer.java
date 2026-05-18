/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.CokeBrickBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CokeOvenEntityRenderer extends EntityRenderer<CokeOvenEntity, CokeOvenRenderState> {

    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;
    private final BlockState displayState;

    public CokeOvenEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
        this.blockModelResolver = context.getBlockModelResolver();
        // FORMED=true,DISPLAY=true → coke_oven_formed model; never set on world blocks
        this.displayState = OmniTechBlocks.COKE_BRICK.get().defaultBlockState()
                .setValue(CokeBrickBlock.FORMED, true)
                .setValue(CokeBrickBlock.DISPLAY, true);
    }

    @Override
    public CokeOvenRenderState createRenderState() {
        return new CokeOvenRenderState();
    }

    @Override
    public void extractRenderState(CokeOvenEntity entity, CokeOvenRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.lit = entity.isLit();
        state.progressFraction = entity.getProgressFraction();
        state.facing = entity.getFacing();
        blockModelResolver.update(state.displayModel, displayState, DISPLAY_CONTEXT);
    }

    @Override
    public void submit(CokeOvenRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (state.displayModel.isEmpty()) return;

        poseStack.pushPose();
        // Rotation must come before the centering translation in code so that, in vertex-transform
        // order, the centering offset is applied first (pre-rotation).  This keeps the model's XZ
        // centre on the entity axis regardless of facing, preventing the "swinging" artefact.
        //
        // The model spans -1..+2 blocks in X/Z (centre at +0.5) and -1..+2 in Y.
        // translate(-0.5, 1.0, -0.5) shifts that centre to (0, 1.5, 0) in entity space,
        // which is the geometric centre of the 3×3×3 structure.
        poseStack.mulPose(Axis.YP.rotationDegrees(facingToYRot(state.facing)));
        poseStack.translate(-0.5, 1.0, -0.5);
        state.displayModel.submitMultiLayer(poseStack, submitNodeCollector,
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    /** Converts a horizontal facing to a Y-rotation so the model's default orientation (south) rotates correctly. */
    private static float facingToYRot(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> 270f;
            default    -> 0f;
        };
    }
}
