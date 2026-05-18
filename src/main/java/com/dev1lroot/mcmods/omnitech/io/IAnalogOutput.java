/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by block entities that produce an analog signal (0.0–15.0).
 * Analog cables poll this to identify connectable endpoints.
 */
public interface IAnalogOutput {
    float getAnalogSignal();
}
