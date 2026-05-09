package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class ExpansionSlotRenderState extends BlockEntityRenderState
{
    public @Nullable ItemStackRenderState item01a = null;
    public @Nullable ItemStackRenderState item02a = null;
    public @Nullable ItemStackRenderState item03a = null;
    public @Nullable ItemStackRenderState item04a = null;

    public @Nullable ItemStackRenderState item01b = null;
    public @Nullable ItemStackRenderState item02b = null;
    public @Nullable ItemStackRenderState item03b = null;
    public @Nullable ItemStackRenderState item04b = null;

    public int lightCoords = 0;
    public Direction facing = Direction.NORTH;
}
