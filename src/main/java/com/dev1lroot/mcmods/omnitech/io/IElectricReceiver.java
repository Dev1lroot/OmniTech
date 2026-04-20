package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by block entities that consume Electric Units (EU) from the
 * electric network each tick (e.g. capacitors, electric furnace).
 *
 * <p>EU is always delivered unconditionally by
 * {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil} —
 * unlike kinetic force, electricity does not stall when demand exceeds supply.
 */
public interface IElectricReceiver {
    /**
     * Called by {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil}
     * during BFS dispatch to deliver EU to this machine.
     *
     * @param amount EU being offered this network clock
     * @return the amount actually accepted (0 if the buffer is full)
     */
    float addElectricity(float amount);
}
