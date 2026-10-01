/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

import net.minecraft.core.Direction;

/**
 * Implemented by block entities that produce electric energy and inject
 * it into the electric network each tick.
 *
 * <p>Energy is in kilojoules: 1 kJ/tick = 20 kW
 * (see {@link com.dev1lroot.mcmods.omnitech.util.ElectricUnits}).
 */
public interface IElectricSupplier {
    /** Energy produced per tick in kJ. Returns 0 when not producing. */
    float getEuSupply();

    /** Terminal voltage of this source in volts. Returns 0 when not producing. */
    float getSupplyVoltage();

    /**
     * Whether this block outputs energy through {@code side}. Used by wires to
     * decide which faces to connect to. Defaults to every face.
     */
    default boolean outputsElectricityTo(Direction side) {
        return true;
    }

    /** Kind of current this source produces. Defaults to AC (alternators). */
    default CurrentType getCurrentType() {
        return CurrentType.AC;
    }
}
