package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class ValveRenderState extends BlockEntityRenderState {
    /** FACING direction of the valve (where the wheel is). */
    public Direction facing = Direction.NORTH;
    public boolean vertical = false;
    /** Wheel spin angle in degrees (LEVEL * 45). */
    public float wheelAngle = 0f;

    /** Model state for the spinning wheel ghost block. */
    public @Nullable MovingBlockRenderState wheelModel;
}
