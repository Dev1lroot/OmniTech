package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.CrankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CrankBlockEntityRenderer implements BlockEntityRenderer<CrankBlockEntity, CrankRenderState> {

    public CrankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public CrankRenderState createRenderState() {
        return new CrankRenderState();
    }

    @Override
    public void extractRenderState(
            CrankBlockEntity entity,
            CrankRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);

        float prev = entity.prevSpinCooldown;
        float cur = entity.getSpinCooldown();
        float interpCooldown = Mth.lerp(partialTicks, prev, cur);
        state.spinAngle = (CrankBlockEntity.SPIN_DURATION - interpCooldown) / CrankBlockEntity.SPIN_DURATION * 360.0f;

        if (entity.getLevel() instanceof ClientLevel clientLevel) {
            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos = entity.getBlockPos();
            model.blockPos = entity.getBlockPos();
            model.blockState = entity.getBlockState();
            model.biome = clientLevel.getBiome(entity.getBlockPos());
            model.cardinalLighting = clientLevel.cardinalLighting();
            model.lightEngine = clientLevel.getLightEngine();
            state.crankModel = model;
        } else {
            state.crankModel = null;
        }
    }

    @Override
    public void submit(CrankRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.crankModel == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spinAngle));
        poseStack.translate(-0.5, 0.0, -0.5);
        submitNodeCollector.submitMovingBlock(poseStack, state.crankModel);
        poseStack.popPose();
    }
}
