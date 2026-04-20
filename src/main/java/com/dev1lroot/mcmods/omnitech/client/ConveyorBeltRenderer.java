package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logistic.conveyor_belt.ConveyorBeltBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.conveyor_belt.ConveyorBeltBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.HashCommon;
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
    private static final float ITEM_SCALE = 0.9f;

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

        // animProgress is driven by the server-authoritative PROGRESS blockstate.
        // When powered, partialTicks provides sub-tick smoothing.
        // When unpowered, the item stays frozen at the last PROGRESS value.
        //
        // Anti-shiver: partialTicks is not perfectly stable between frames (it
        // reflects real elapsed time and can fluctuate ±frame).  If we naively
        // use (PROGRESS + partialTicks) each frame the item can drift backward
        // on frames where partialTicks is slightly lower than the previous frame.
        // Fix: within the same PROGRESS tick we only allow animProgress to
        // increase (Math.max).  When PROGRESS itself changes we snap to the new
        // computed value so we never lag behind the server.
        if (!entity.getHeldItem().isEmpty()) {
            int progress = entity.getBlockState().getValue(ConveyorBeltBlock.PROGRESS);

            // Вычисляем текущий кандидат на прогресс
            float candidate = Math.min(1.0f,
                    (state.powered ? (progress + partialTicks) : (float) progress)
                            / ConveyorBeltBlockEntity.TRANSFER_INTERVAL);

            // Если это новый тик или новый предмет — сбрасываем "предохранитель"
            if (progress != entity.lastRenderTick) {
                entity.lastRenderProgress = candidate;
                entity.lastRenderTick = progress;
            } else {
                // В рамках одного тика разрешаем только движение вперед
                entity.lastRenderProgress = Math.max(entity.lastRenderProgress, candidate);
            }

            state.animProgress = entity.lastRenderProgress;
        } else {
            // Очистка состояния, если предмета нет
            entity.lastRenderTick = -1;
            entity.lastRenderProgress = 0f;
            state.animProgress = 0f;
        }

        // ── Belt block model ───────────────────────────────────────────────────
        if (entity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
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
        // Push/pop so submitMovingBlock cannot leave residual matrix state that
        // would offset the item translation below.
        poseStack.pushPose();
        submitNodeCollector.submitMovingBlock(poseStack, state.beltModel);
        poseStack.popPose();

        // ── Floating item ──────────────────────────────────────────────────────
        if (state.heldItemState == null) return;

        // Slide item from the exact front face (t=0) to the exact back face (t=1).
        // Formula: centre = 0.5 + facing.step * (0.5 - t)
        //   t=0 → centre at front face edge (no gap)
        //   t=1 → centre at back face edge (no gap)
        Direction front = state.facing;
        float t = state.animProgress;
        float itemX = 0.5f + front.getStepX() * (0.5f - t);
        float itemZ = 0.5f + front.getStepZ() * (0.5f - t);

        poseStack.pushPose();
        poseStack.translate(itemX, ITEM_Y, itemZ);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        state.heldItemState.submit(
                poseStack, submitNodeCollector,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
