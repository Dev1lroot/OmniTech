package com.dev1lroot.mcmods.omnitech.blocks.radio;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side in-memory store for the virtual FM radio spectrum.
 * Transmitters write their signal each tick; receivers and the scanner read it.
 * Cleared on every server start so stale values from a previous session don't persist.
 */
public final class RadioManager {

    private static final Map<Integer, Float> SIGNALS = new HashMap<>();

    private RadioManager() {}

    /** Write a signal (0.0–15.0) at the given frequency (stored as freq × 10). */
    public static void set(int freqX10, float value) {
        if (value <= 0f) {
            SIGNALS.remove(freqX10);
        } else {
            SIGNALS.put(freqX10, Math.min(15f, value));
        }
    }

    /** Read the current signal at a frequency, or 0 if no transmitter is active there. */
    public static float get(int freqX10) {
        return SIGNALS.getOrDefault(freqX10, 0f);
    }

    /**
     * Returns a snapshot row of all {@link RadioConstants#CHANNELS} channels,
     * starting from {@link RadioConstants#FREQ_MIN_X10}.
     */
    public static float[] getRow() {
        float[] row = new float[RadioConstants.CHANNELS];
        for (int i = 0; i < RadioConstants.CHANNELS; i++) {
            row[i] = SIGNALS.getOrDefault(RadioConstants.FREQ_MIN_X10 + i, 0f);
        }
        return row;
    }

    /** Remove a transmitter's contribution when the block entity is removed or unloaded. */
    public static void clear(int freqX10) {
        SIGNALS.remove(freqX10);
    }

    /** Wipe the entire spectrum — called on server start to remove stale state. */
    public static void clearAll() {
        SIGNALS.clear();
    }
}
