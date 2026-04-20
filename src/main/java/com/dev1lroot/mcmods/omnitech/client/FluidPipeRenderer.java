package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidPipeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the {@code fluid_pipe_trim} blockstate overlay on top of the base pipe model.
 *
 * <p>The trim models have {@code "tintindex": 0} on every face. Rather than registering
 * a {@code BlockTintSource}, the tint color is computed directly here from the pipe's
 * {@link FluidPipeBlock#COLOR} block-state integer and pushed into
 * {@link net.minecraft.client.renderer.block.BlockModelRenderState#tintLayers()} after
 * the model parts are collected.
 *
 * <p>COLOR 0 = uncolored (no overlay). COLOR 1–16 maps to {@link DyeColor#values()} 0–15.
 */

public class FluidPipeRenderer implements BlockEntityRenderer<FluidPipeBlockEntity, FluidPipeRenderState>
{
    private static final BlockDisplayContext DISPLAY_CTX = BlockDisplayContext.create();

    public FluidPipeRenderer(BlockEntityRendererProvider.Context ctx) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public FluidPipeRenderState createRenderState() {
        return new FluidPipeRenderState();
    }

    @Override
    public void extractRenderState(
            FluidPipeBlockEntity entity, FluidPipeRenderState state,
            float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);

        BlockState pipeState = entity.getBlockState();
        state.color = pipeState.getValue(FluidPipeBlock.COLOR);
        state.north = pipeState.getValue(FluidPipeBlock.NORTH);
        state.south = pipeState.getValue(FluidPipeBlock.SOUTH);
        state.east  = pipeState.getValue(FluidPipeBlock.EAST);
        state.west  = pipeState.getValue(FluidPipeBlock.WEST);
        state.up    = pipeState.getValue(FluidPipeBlock.UP);
        state.down  = pipeState.getValue(FluidPipeBlock.DOWN);

        if (entity.getLevel() instanceof ClientLevel cl) {
            state.lightCoords = LevelRenderer.getLightCoords(cl, entity.getBlockPos());
        } else {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }

        state.trimRenderState.clear();

        if (state.color != 0) {
            // Build trim BlockState mirroring the pipe's connection properties.
            // COLOR is set but ignored by the blockstate's multipart conditions.
            BlockState trimState = OmniTechBlocks.FLUID_PIPE_TRIM.get().defaultBlockState()
                    .setValue(FluidPipeBlock.NORTH, state.north)
                    .setValue(FluidPipeBlock.SOUTH, state.south)
                    .setValue(FluidPipeBlock.EAST,  state.east)
                    .setValue(FluidPipeBlock.WEST,  state.west)
                    .setValue(FluidPipeBlock.UP,    state.up)
                    .setValue(FluidPipeBlock.DOWN,  state.down)
                    .setValue(FluidPipeBlock.COLOR, state.color);

            // Populate model parts. No BlockTintSource is registered for this block,
            // so update() leaves tintLayers empty — we fill it manually below.
            ModelManager modelManager = Minecraft.getInstance().getModelManager();
            modelManager.getBlockModelSet().get(trimState)
                    .update(state.trimRenderState, trimState, DISPLAY_CTX, 42L);

            // Directly convert the COLOR index to a DyeColor ARGB and inject it as
            // tint layer 0, which maps to "tintindex": 0 on every trim model face.
            int dyeIdx = Math.clamp(state.color - 1, 0, DyeColor.values().length - 1);
            int argb   = DyeColor.values()[dyeIdx].getTextureDiffuseColor();
            state.trimRenderState.tintLayers().add(argb);
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(
            FluidPipeRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (state.color == 0 || state.trimRenderState.isEmpty()) return;

        state.trimRenderState.submitMultiLayer(
                poseStack, submitNodeCollector,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0);
    }
}
