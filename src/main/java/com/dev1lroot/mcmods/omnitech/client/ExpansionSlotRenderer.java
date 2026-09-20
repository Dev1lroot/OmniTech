/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ExpansionSlotRenderer implements BlockEntityRenderer<ExpansionSlotBlockEntity, ExpansionSlotRenderState>
{
    private final ItemModelResolver itemModelResolver;

    public ExpansionSlotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ExpansionSlotRenderState createRenderState() {
        return new ExpansionSlotRenderState();
    }

    @Override
    public void extractRenderState(
            ExpansionSlotBlockEntity entity,
            ExpansionSlotRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState( entity, state, partialTicks, cameraPosition, breakProgress);

        if (!(entity.getLevel() instanceof ClientLevel clientLevel)) {
            state.item01a = null;
            state.item01b = null;
            state.item02a = null;
            state.item02b = null;
            state.item03a = null;
            state.item03b = null;
            state.item04a = null;
            state.item04b = null;
            return;
        }

        state.facing = entity.getBlockState().getValue(LogicGateBlock.FACING);

        NonNullList<ItemStack> stacks = entity.getItemList();

        for (int i = 0; i < stacks.size(); i++)
        {
            ItemStack stack = stacks.get(i);
            if(!stack.isEmpty())
            {
                ItemStackRenderState itemState = new ItemStackRenderState();
                int seed = HashCommon.long2int(entity.getBlockPos().asLong()) ^ 1;
                itemModelResolver.updateForTopItem( itemState, stack, ItemDisplayContext.GROUND, clientLevel, null, seed);
                if(i == 0) { state.item01a = itemState; }
                if(i == 1) { state.item02a = itemState; }
                if(i == 2) { state.item03a = itemState; }
                if(i == 3) { state.item04a = itemState; }
                if(i == 4) { state.item01b = itemState; }
                if(i == 5) { state.item02b = itemState; }
                if(i == 6) { state.item03b = itemState; }
                if(i == 7) { state.item04b = itemState; }
            }
            else
            {
                if(i == 0) { state.item01a = null; }
                if(i == 1) { state.item02a = null; }
                if(i == 2) { state.item03a = null; }
                if(i == 3) { state.item04a = null; }
                if(i == 4) { state.item01b = null; }
                if(i == 5) { state.item02b = null; }
                if(i == 6) { state.item03b = null; }
                if(i == 7) { state.item04b = null; }
            }
        }

        state.lightCoords = LightCoordsUtil.getLightCoords(clientLevel, entity.getBlockPos());
    }

    @Override
    public void submit(ExpansionSlotRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera)
    {
        float yRot = facingToYRot(state.facing);

        if (state.item01a != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.55f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, -0.3f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item01a.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item02a != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.55f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, -0.1f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item02a.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item03a != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.55f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, 0.1f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item03a.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item04a != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.55f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, 0.3f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item04a.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.item01b != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.1f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, -0.3f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item01b.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item02b != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.1f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, -0.1f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item02b.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item03b != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.1f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, 0.1f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item03b.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.item04b != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.1f, 0.5f);
            poseStack.rotate(Axis.YP.rotationDegrees(yRot));
            poseStack.translate(0.0f, 0.0f, 0.3f);
            poseStack.scale(1.5f, 1.5f, 1.5f);
            state.item04b.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private static float facingToYRot(Direction facing) {
        return switch (facing) {
            case NORTH ->   90f;
            case EAST  ->  180f;
            case SOUTH ->   -90f;
            case WEST  ->   0f;
            default    ->   0f;
        };
    }
}
