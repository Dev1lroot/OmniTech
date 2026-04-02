package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;

public class FluidTankRenderState extends BlockEntityRenderState {
    /** Proportion of tank that is filled (0.0 = empty, 1.0 = full). */
    public float fillLevel = 0f;
    /** Block model used to represent the inner fluid volume; null when empty. */
    public @Nullable MovingBlockRenderState fluidModel = null;
}
