package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlockEntity;
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
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the {@code fluid_pipe_trim} blockstate overlay on top of the base pipe model.
 *
 * <p>The trim blockstate uses the same multipart conditions as {@code fluid_pipe.json}
 * but references {@code *_trim} model variants that have {@code "tintindex": 0} on every
 * face, so the registered {@link net.minecraft.client.color.block.BlockTintSource} can
 * apply dye color to them.
 *
 * <p>COLOR 0 = uncolored (no overlay). COLOR 1–16 maps to {@link DyeColor#values()} 0–15.
 */

public class FluidPipeRenderer
        implements BlockEntityRenderer<FluidPipeBlockEntity, FluidPipeRenderState> {

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
        state.colorIndex = pipeState.getValue(FluidPipeBlock.COLOR);
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

        if (state.colorIndex != 0) {
            // Build a matching trim BlockState from the pipe's connection properties + color.
            BlockState trimState = OmniTechBlocks.FLUID_PIPE_TRIM.get().defaultBlockState()
                    .setValue(FluidPipeBlock.NORTH, state.north)
                    .setValue(FluidPipeBlock.SOUTH, state.south)
                    .setValue(FluidPipeBlock.EAST,  state.east)
                    .setValue(FluidPipeBlock.WEST,  state.west)
                    .setValue(FluidPipeBlock.UP,    state.up)
                    .setValue(FluidPipeBlock.DOWN,  state.down)
                    .setValue(FluidPipeBlock.COLOR, state.colorIndex);

            ModelManager modelManager = Minecraft.getInstance().getModelManager();
            modelManager.getBlockModelSet().get(trimState)
                    .update(state.trimRenderState, trimState, DISPLAY_CTX, 42L);
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(
            FluidPipeRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (state.colorIndex == 0 || state.trimRenderState.isEmpty()) return;

        state.trimRenderState.submitMultiLayer(
                poseStack, submitNodeCollector,
                state.lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0);
    }
}
