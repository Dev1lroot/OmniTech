/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class FluidPipeRenderState extends BlockEntityRenderState {
    /** ARGB COLOR */
    public int color = 0;
    public int lightCoords = 0;
    public boolean north, south, east, west, up, down;

    /** Populated in extractRenderState from the fluid_pipe_trim blockstate model. */
    public final BlockModelRenderState trimRenderState = new BlockModelRenderState();
}
