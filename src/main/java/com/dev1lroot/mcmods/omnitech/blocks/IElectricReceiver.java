package com.dev1lroot.mcmods.omnitech.blocks;

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
     * @return true if the receiver accepted any energy
     */
    boolean addElectricity(float amount);
}
