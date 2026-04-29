package com.dev1lroot.mcmods.omnitech.blocks.radio;

public final class RadioConstants {
    /** Lowest tunable frequency × 10 (87.5 MHz). */
    public static final int FREQ_MIN_X10 = 875;
    /** Highest tunable frequency × 10 (117.4 MHz). */
    public static final int FREQ_MAX_X10 = 1174;
    /** Number of discrete channels: 87.5–117.4 MHz at 0.1 MHz steps. */
    public static final int CHANNELS = FREQ_MAX_X10 - FREQ_MIN_X10 + 1; // 300

    /** Rows of history shown in the scanner waterfall. */
    public static final int SCAN_HISTORY = 100;

    private RadioConstants() {}
}
