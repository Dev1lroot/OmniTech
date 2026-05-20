/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

/**
 * Thermodynamic phase of a fluid at given temperature and pressure.
 *
 * <p>Phase is computed by {@link FluidPhaseUtil#getPhase} from a fluid's
 * {@link FluidPhysicsRegistry.PhaseDiagram}.  The {@link #phaseKey()} maps
 * to tooltip lang keys of the form {@code omnitech.fluid.phase.<key>} and
 * optional fluid-specific overrides {@code omnitech.fluid.<name>.<key>}.
 */
public enum FluidPhase {
    SOLID, LIQUID, VAPOUR, GAS, SUPERCRITICAL, PLASMA;

    /** Lower-case name used in lang key suffixes (e.g. {@code "liquid"}). */
    public String phaseKey() { return name().toLowerCase(); }
}
