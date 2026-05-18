package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class ReactorCellBERRenderState extends BlockEntityRenderState {
    /** -1 = no control rod; 0–100 = insertion percentage. */
    public int controlInsertion = -1;
    public int light            = 0xF000F0;
}
