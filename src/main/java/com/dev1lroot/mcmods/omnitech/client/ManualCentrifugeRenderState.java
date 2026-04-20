package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;

public class ManualCentrifugeRenderState extends BlockEntityRenderState {
    public float rotorAngle = 0f;
    public @Nullable MovingBlockRenderState rotorModel;
}
