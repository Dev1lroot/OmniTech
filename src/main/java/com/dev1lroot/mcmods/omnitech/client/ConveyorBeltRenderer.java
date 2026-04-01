package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.ConveyorBeltBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ConveyorBeltBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the Conveyor Belt block model and a hovering item that slides
 * from the input (front) face to the output (back) face while powered.
 *
 * <p>Uses the same {@link ItemModelResolver} / {@link ItemStackRenderState}
 * pattern as vanilla's {@code ShelfRenderer}.
 */
public class ConveyorBeltRenderer
        implements BlockEntityRenderer<ConveyorBeltBlockEntity, ConveyorBeltRenderState> {

    /** Height above the belt surface (4 px = 0.25 + small clearance). */
    private static final float ITEM_Y = 0.30f;
    /** Item display scale relative to a full block. */
    private static final float ITEM_SCALE = 0.3f;

    private final ItemModelResolver itemModelResolver;

    public ConveyorBeltRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

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

        // Smooth animation driven by the global game clock — no explicit server sync needed.
        if (state.powered && entity.getLevel() instanceof ClientLevel cl) {
            float rawT = (cl.getGameTime() + partialTicks)
                    % ConveyorBeltBlockEntity.TRANSFER_INTERVAL;
            state.animProgress = rawT / ConveyorBeltBlockEntity.TRANSFER_INTERVAL;
        } else {
            state.animProgress = 0f;
        }

        // ── Belt block model ───────────────────────────────────────────────────
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

        // ── Held item render state ─────────────────────────────────────────────
        ItemStack held = entity.getHeldItem();
        if (!held.isEmpty()) {
            ItemStackRenderState itemState = new ItemStackRenderState();
            int seed = HashCommon.long2int(entity.getBlockPos().asLong());
            itemModelResolver.updateForTopItem(
                    itemState, held, ItemDisplayContext.GROUND,
                    entity.getLevel(), null, seed);
            state.heldItemState = itemState;
        } else {
            state.heldItemState = null;
        }
    }

    @Override
    public void submit(ConveyorBeltRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.beltModel == null) return;

        // ── Belt block model ───────────────────────────────────────────────────
        submitNodeCollector.submitMovingBlock(poseStack, state.beltModel);

        // ── Floating item ──────────────────────────────────────────────────────
        if (state.heldItemState == null) return;

        // Slide from front-face centre toward back-face centre.
        // animProgress=0 → near front face; animProgress=1 → near back face.
        Direction front = state.facing;
        float t = state.animProgress;
        float itemX = 0.5f + front.getStepX() * (0.4f - t * 0.8f);
        float itemZ = 0.5f + front.getStepZ() * (0.4f - t * 0.8f);

        poseStack.pushPose();
        poseStack.translate(itemX, ITEM_Y, itemZ);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.heldItemState.submit(
                poseStack, submitNodeCollector,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
