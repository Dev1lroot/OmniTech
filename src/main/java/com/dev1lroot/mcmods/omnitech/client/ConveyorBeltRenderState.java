package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class ConveyorBeltRenderState extends BlockEntityRenderState {
    /** Belt block model (4-pixel slab geometry + powered texture variant). */
    public @Nullable MovingBlockRenderState beltModel = null;
    /** Facing direction of the belt (drives item slide direction). */
    public Direction facing = Direction.NORTH;
    /** Whether KF is flowing — used to gate animation. */
    public boolean powered = false;
    /** Transfer progress [0, 1] — drives item sliding animation. */
    public float animProgress = 0f;
    /**
     * The last {@code PROGRESS} blockstate value seen by {@link #extractRenderState}.
     * Kept across frames so the animation can only move forward within a tick,
     * preventing partialTicks jitter from causing backward micro-movement (shivering).
     * -1 = uninitialized (forces a snap on the first non-empty frame).
     */
    public int lastProgress = -1;
    /** Prepared item render state, or {@code null} when the slot is empty. */
    public @Nullable ItemStackRenderState heldItemState = null;
}
