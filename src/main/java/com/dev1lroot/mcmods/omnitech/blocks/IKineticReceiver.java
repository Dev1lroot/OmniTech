package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that accept kinetic force from the KF network
 * or from a Crank.
 *
 * <p>KF values use a fixed-point scale of <b>10 units = 1 KF</b>.
 * The default demand is 5 (= 0.5 KF).  Cheap consumers such as conveyor belts
 * should override {@link #getKfDemand()} to return a lower value.
 */
public interface IKineticReceiver {
    /** @return true if force was accepted */
    boolean addKineticForce(int amount);

    /**
     * Fixed-point KF units (10 = 1 KF) this machine requires per network clock.
     * Default: 5 (= 0.5 KF).
     */
    default int getKfDemand() { return 5; }
}
