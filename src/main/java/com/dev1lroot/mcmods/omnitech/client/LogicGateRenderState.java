package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jetbrains.annotations.Nullable;

public class LogicGateRenderState extends BlockEntityRenderState {
    public @Nullable ItemStackRenderState templateItemState = null;
    public int lightCoords = 0;
}
