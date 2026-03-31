package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;

public class CrankRenderState extends BlockEntityRenderState {
    public float spinAngle = 0.0f;
    public @Nullable MovingBlockRenderState crankModel;
}
