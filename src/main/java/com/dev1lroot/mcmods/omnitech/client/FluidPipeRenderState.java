package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class FluidPipeRenderState extends BlockEntityRenderState {
    /** 0 = no color; 1–16 = DyeColor ordinal + 1 */
    public int colorIndex = 0;
    public int lightCoords = 0;
    public boolean north, south, east, west, up, down;

    /** Populated in extractRenderState from the fluid_pipe_trim blockstate model. */
    public final BlockModelRenderState trimRenderState = new BlockModelRenderState();
}
