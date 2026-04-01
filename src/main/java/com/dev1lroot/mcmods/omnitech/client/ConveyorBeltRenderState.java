package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ConveyorBeltRenderState extends BlockEntityRenderState {
    /** Belt block model (includes the 4-pixel slab geometry). */
    public @Nullable MovingBlockRenderState beltModel = null;
    /** Facing direction of the belt (used to orient the item animation). */
    public Direction facing = Direction.NORTH;
    /** Whether KF is flowing — selects the moving/stopped belt model variant. */
    public boolean powered = false;
    /** Transfer progress [0, 1] — drives item sliding animation. */
    public float animProgress = 0f;
    /** Item currently held on the belt, or {@link ItemStack#EMPTY}. */
    public ItemStack heldItem = ItemStack.EMPTY;
}
