package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that produce Electric Units (EU) and inject
 * them into the electric network each tick.
 *
 * <p>EU scale: 1 unit = 1 EU.
 * Example: Electric Engine produces 10 EU/tick at 1 KF input.
 */
public interface IElectricSupplier {
    /** EU produced per network tick. Returns 0 when not producing. */
    float getEuSupply();
}
