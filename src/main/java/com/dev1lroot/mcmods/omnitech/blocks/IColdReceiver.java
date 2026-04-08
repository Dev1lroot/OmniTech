package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that can absorb cold (measured in celsius).
 *
 * <p>The {@link DecompressorBlockEntity} pushes cold to every directly adjacent
 * block that implements this interface each server tick.  The caller deducts
 * the returned amount from its own stored cold, so thermal energy genuinely
 * flows rather than being duplicated.
 *
 * <p>Absorbing cold is equivalent to losing heat — implementations should
 * reduce their own stored heat/temperature by the absorbed amount.
 */
public interface IColdReceiver {
    /**
     * Absorb up to {@code celsius} degrees of cold (removing that heat from this block).
     *
     * @return the amount actually absorbed (0 if already at minimum temperature).
     */
    int addCold(int celsius);
}
