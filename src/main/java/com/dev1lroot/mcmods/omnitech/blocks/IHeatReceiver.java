package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that can accept heat energy (measured in celsius).
 *
 * <p>The {@link HeaterBlockEntity} pushes heat to every directly adjacent block
 * that implements this interface each server tick.  The caller deducts the
 * returned amount from its own stored heat, so heat genuinely "flows" from the
 * source into the receiver.
 */
public interface IHeatReceiver {
    /**
     * Add up to {@code celsius} degrees of heat to this block.
     *
     * @return the amount actually absorbed (0 if already at maximum capacity).
     */
    int addHeat(int celsius);
}
