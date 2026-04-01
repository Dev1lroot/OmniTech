package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class KineticPipeRenderState extends BlockEntityRenderState {
    public boolean                     powered      = false;
    public float                       rotationAngle = 0f;
    public Direction.Axis              axis          = Direction.Axis.Y;
    public @Nullable MovingBlockRenderState pipeModel = null;
}
