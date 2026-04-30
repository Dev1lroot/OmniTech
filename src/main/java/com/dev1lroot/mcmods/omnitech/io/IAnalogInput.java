package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by block entities that consume an analog signal (0.0–15.0).
 * {@link com.dev1lroot.mcmods.omnitech.util.AnalogNetworkUtil} calls this
 * when propagating a signal from an IAnalogOutput source through the cable network.
 */
public interface IAnalogInput {
    void receiveAnalogSignal(float signal);
}
