package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class ThermalConductorRenderState extends BlockEntityRenderState {
    /** Packed ARGB color for the glow overlay (0 = no glow). */
    public int argb = 0;
    /** Direction arm connections, mirrored from blockstate. */
    public boolean north, south, east, west, up, down;
    /** Packed sky+block light for the conductor's block position. */
    public int lightCoords = 0;
}
