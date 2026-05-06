package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LogicGateRenderer
        implements BlockEntityRenderer<LogicGateBlockEntity, LogicGateRenderState> {

    private static final float Y_TEMPLATE    = 2.5f / 16f;
    private static final float TEMPLATE_SCALE = 2f;

    private final ItemModelResolver itemModelResolver;

    public LogicGateRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public LogicGateRenderState createRenderState() {
        return new LogicGateRenderState();
    }

    @Override
    public void extractRenderState(
            LogicGateBlockEntity entity,
            LogicGateRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        if (!(entity.getLevel() instanceof ClientLevel clientLevel)) {
            state.templateItemState = null;
            return;
        }

        ItemStack templateStack = entity.getTemplate();
        if (!templateStack.isEmpty()) {
            ItemStackRenderState itemState = new ItemStackRenderState();
            int seed = HashCommon.long2int(entity.getBlockPos().asLong()) ^ 1;
            itemModelResolver.updateForTopItem(
                    itemState, templateStack, ItemDisplayContext.GROUND, clientLevel, null, seed);
            state.templateItemState = itemState;
        } else {
            state.templateItemState = null;
        }

        state.lightCoords = LevelRenderer.getLightCoords(clientLevel, entity.getBlockPos());
    }

    @Override
    public void submit(LogicGateRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.templateItemState != null) {
            poseStack.pushPose();
            poseStack.translate(0.25f, Y_TEMPLATE, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.scale(TEMPLATE_SCALE, TEMPLATE_SCALE, TEMPLATE_SCALE);
            state.templateItemState.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
