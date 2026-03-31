package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that accept kinetic force from the Crank.
 */
public interface IKineticReceiver {
    /** @return true if force was accepted (a valid recipe is loaded) */
    boolean addKineticForce(int amount);
}
