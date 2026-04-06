package com.dev1lroot.mcmods.omnitech.blocks;

/**
 * Implemented by block entities that produce kinetic force (e.g. generators,
 * Stirling engines).
 *
 * <p>KF values use a fixed-point scale of <b>10 units = 1 KF</b>.
 * Examples: generator = 10 (1 KF), Stirling engine = 20 (2 KF).
 */
public interface IKineticSupplier {
    /** Fixed-point KF units (10 = 1 KF) produced per network clock. */
    float getKfSupply();
}
