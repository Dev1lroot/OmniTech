package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class DisplayRenderState extends BlockEntityRenderState {
    /** Pixel colors (0x00RRGGBB) for the 16×16 matrix, row-major. */
    public final int[] pixels = new int[256];
    /** Which horizontal direction the display faces. */
    public Direction facing = Direction.NORTH;
}
