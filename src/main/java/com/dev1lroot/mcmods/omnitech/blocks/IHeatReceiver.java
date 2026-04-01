package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that can accept heat energy (measured in celsius).
 *
 * <p>The {@link HeaterBlockEntity} pushes heat to every directly adjacent block
 * that implements this interface each server tick.
 */
public interface IHeatReceiver {
    /**
     * Add {@code celsius} degrees of heat to this block.
     *
     * @return {@code true} if any heat was absorbed (i.e. not already at max).
     */
    boolean addHeat(int celsius);
}
