/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

import net.minecraft.core.Direction;

/**
 * Implemented by block entities that consume electric energy from the
 * electric network (e.g. capacitors, electric furnace).
 *
 * <p>Energy is in kilojoules (see {@link com.dev1lroot.mcmods.omnitech.util.ElectricUnits}).
 * It is always delivered unconditionally by
 * {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil} —
 * unlike kinetic force, electricity does not stall when demand exceeds supply.
 */
public interface IElectricReceiver {
    /**
     * Called by {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil}
     * during BFS dispatch to deliver energy to this machine.
     *
     * @param amount energy being offered this network clock, in kJ
     * @param volts  line voltage of the source that is offering it
     * @return the amount actually accepted in kJ (0 if the buffer is full)
     */
    float addElectricity(float amount, float volts);

    /**
     * Whether energy may enter through {@code side} (the face of this block that
     * touches the network). Wires only connect to accepting faces and the
     * network only delivers through them. Defaults to every face.
     */
    default boolean acceptsElectricityFrom(Direction side) {
        return true;
    }
}
