package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class DisplayRenderState extends BlockEntityRenderState {
    /** Pixel size of this display tier (16, 64, or 128). */
    public int size = 16;
    /** Pixel colors (0x00RRGGBB), row-major. Length is always 128*128 (max). */
    public final int[] pixels = new int[128 * 128];
    /** Which horizontal direction the display faces. */
    public Direction facing = Direction.NORTH;
}
