package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the inner fluid-level cube inside the glass {@link FluidTankBlock}.
 *
 * <p>The outer glass shell is rendered normally by the block model system
 * ({@code RenderShape.MODEL}).  This renderer adds only the inner volume:
 * a 14×fillHeight×14 px cube (in pixel units out of 16) using a colored
 * stained-glass block as a stand-in for the stored fluid.
 */
public class FluidTankRenderer
        implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderState> {

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public FluidTankRenderState createRenderState() {
        return new FluidTankRenderState();
    }

    @Override
    public void extractRenderState(
            FluidTankBlockEntity entity,
            FluidTankRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        FluidStack fluid = entity.getFluid();
        if (fluid.isEmpty()) {
            state.fillLevel = 0f;
            state.fluidModel = null;
            return;
        }

        state.fillLevel = (float) fluid.getAmount() / FluidTankBlockEntity.CAPACITY;

        if (!(entity.getLevel() instanceof ClientLevel clientLevel)) {
            state.fluidModel = null;
            return;
        }

        // Pick a colored block to represent the fluid visually
        BlockState fluidBlockState = pickFluidBlock(fluid);

        MovingBlockRenderState model = new MovingBlockRenderState();
        model.randomSeedPos    = entity.getBlockPos();
        model.blockPos         = entity.getBlockPos();
        model.blockState       = fluidBlockState;
        model.biome            = clientLevel.getBiome(entity.getBlockPos());
        model.cardinalLighting = clientLevel.cardinalLighting();
        model.lightEngine      = clientLevel.getLightEngine();
        state.fluidModel = model;
    }

    @Override
    public void submit(FluidTankRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.fluidModel == null || state.fillLevel <= 0f) return;

        poseStack.pushPose();
        // Position inner cube: 1 px inward on X and Z, starting at bottom (Y=0)
        poseStack.translate(1 / 16.0, 0.0, 1 / 16.0);
        // Scale to 14 px wide × fillLevel tall × 14 px deep
        poseStack.scale(14 / 16.0f, state.fillLevel, 14 / 16.0f);
        submitNodeCollector.submitMovingBlock(poseStack, state.fluidModel);
        poseStack.popPose();
    }

    /** Returns the block used to represent the stored fluid visually. */
    /** Returns the block used to represent the stored fluid visually. */
    private static net.minecraft.world.level.block.state.BlockState pickFluidBlock(FluidStack fluid) {
        // Получаем имя типа жидкости для сравнения
        String fluidName = fluid.getFluid().getFluidType().toString().toLowerCase();

        if (fluidName.contains("water")) {
            return Blocks.BLUE_CONCRETE.defaultBlockState();
        }

        if (fluidName.contains("lava")) {
            return Blocks.ORANGE_CONCRETE.defaultBlockState();
        }

        if (fluidName.contains("milk")) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }

        // Плейсхолдер для всех остальных жидкостей
        return Blocks.GRAY_CONCRETE.defaultBlockState();
    }
}
