package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by block entities that produce an analog signal (0.0–15.0).
 * Analog cables poll this to identify connectable endpoints.
 */
public interface IAnalogOutput {
    float getAnalogSignal();
}
