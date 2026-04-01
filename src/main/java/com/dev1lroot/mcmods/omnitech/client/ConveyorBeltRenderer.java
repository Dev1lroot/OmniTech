package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.ConveyorBeltBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ConveyorBeltBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * Renders the Conveyor Belt block model.
 *
 * <p>The belt is submitted as a {@link MovingBlockRenderState} so the
 * POWERED blockstate variant (with the animated .mcmeta belt texture) is
 * selected automatically.  The render state also tracks the held item and
 * animation progress for future item floating integration.
 */
public class ConveyorBeltRenderer
        implements BlockEntityRenderer<ConveyorBeltBlockEntity, ConveyorBeltRenderState> {

    public ConveyorBeltRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public ConveyorBeltRenderState createRenderState() {
        return new ConveyorBeltRenderState();
    }

    @Override
    public void extractRenderState(
            ConveyorBeltBlockEntity entity,
            ConveyorBeltRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        state.powered = entity.getBlockState().getValue(ConveyorBeltBlock.POWERED);
        state.facing  = entity.getBlockState().getValue(ConveyorBeltBlock.FACING);
        state.heldItem = entity.getHeldItem().copy();

        // Derive animProgress from the global game clock so it is always
        // smooth on the client without any explicit server sync.
        if (state.powered && entity.getLevel() instanceof ClientLevel cl) {
            float rawT = (cl.getGameTime() + partialTicks)
                    % ConveyorBeltBlockEntity.TRANSFER_INTERVAL;
            state.animProgress = rawT / ConveyorBeltBlockEntity.TRANSFER_INTERVAL;
        } else {
            state.animProgress = 0f;
        }

        if (entity.getLevel() instanceof ClientLevel cl) {
            MovingBlockRenderState model = new MovingBlockRenderState();
            model.randomSeedPos    = entity.getBlockPos();
            model.blockPos         = entity.getBlockPos();
            model.blockState       = entity.getBlockState();
            model.biome            = cl.getBiome(entity.getBlockPos());
            model.cardinalLighting = cl.cardinalLighting();
            model.lightEngine      = cl.getLightEngine();
            state.beltModel = model;
        } else {
            state.beltModel = null;
        }
    }

    @Override
    public void submit(ConveyorBeltRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.beltModel == null) return;

        // ── Belt block model ───────────────────────────────────────────────────
        submitNodeCollector.submitMovingBlock(poseStack, state.beltModel);

        // Item floating visual — requires item render API (pending integration)
    }
}
