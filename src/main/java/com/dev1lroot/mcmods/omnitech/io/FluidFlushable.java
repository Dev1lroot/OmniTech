/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

/**
 * A machine whose fluid tanks the player may dump from its GUI. Tank names are the
 * {@code source} ids of the GUI layout's {@code fluid_tank} elements.
 */
public interface FluidFlushable {
    /**
     * Empties the named tank; the fluid is destroyed.
     *
     * @return true if something was removed
     */
    boolean flushTank(String tank);
}
