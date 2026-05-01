package com.dev1lroot.mcmods.omnitech.blocks.radio;

/** All RF bands supported by the OmniTech radio system. */
public enum FrequencyBand {
    //         name   unit    ch   fMin   fStep  audio  noisy  maxRange  interdim
    ELF ("ELF", "Hz",   10,   3f,   3f,   false, false,   -1,  false),
    VLF ("VLF", "kHz",  27,   3f,   1f,   false, false,   -1,  false),
    LF  ("LF",  "kHz",  28,  30f,  10f,   true,  true,    -1,  false),
    MF  ("MF",  "MHz",  28,  0.3f, 0.1f,  true,  true,    -1,  false),
    HF  ("HF",  "MHz",  28,   3f,   1f,   true,  false,   -1,  false),
    VHF ("VHF", "MHz", 270,  30f,   1f,   true,  false, 1000,  false),
    UHF ("UHF", "GHz",  28,  0.3f, 0.1f,  true,  false, 1000,  false),
    SHF ("SHF", "GHz",  28,   3f,   1f,   true,  false,   -1,  true),
    EHF ("EHF", "GHz",  28,  30f,  10f,   true,  false,   -1,  true);

    private final String  displayName;
    private final String  unit;
    private final int     channels;
    private final float   freqMin;
    private final float   freqStep;
    private final boolean audioAllowed;
    private final boolean noisy;
    private final int     maxRange;       // blocks; -1 = unlimited
    private final boolean interdimensional;

    FrequencyBand(String displayName, String unit, int channels,
                  float freqMin, float freqStep,
                  boolean audioAllowed, boolean noisy,
                  int maxRange, boolean interdimensional) {
        this.displayName      = displayName;
        this.unit             = unit;
        this.channels         = channels;
        this.freqMin          = freqMin;
        this.freqStep         = freqStep;
        this.audioAllowed     = audioAllowed;
        this.noisy            = noisy;
        this.maxRange         = maxRange;
        this.interdimensional = interdimensional;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public String  displayName()      { return displayName; }
    public String  unit()             { return unit; }
    public int     channels()         { return channels; }
    public float   freqMin()          { return freqMin; }
    public float   freqStep()         { return freqStep; }
    public boolean audioAllowed()     { return audioAllowed; }
    public boolean noisy()            { return noisy; }
    public int     maxRange()         { return maxRange; }
    public boolean interdimensional() { return interdimensional; }

    // ── Global channel key: bandOrdinal × 1000 + channelIndex ─────────────────
    // Key space: ELF 0-9 | VLF 1000-1026 | LF 2000-2027 | MF 3000-3027
    //            HF 4000-4027 | VHF 5000-5269 | UHF 6000-6027
    //            SHF 7000-7027 | EHF 8000-8027

    public int globalKey(int channelIndex) {
        return ordinal() * 1000 + Math.clamp(channelIndex, 0, channels - 1);
    }

    public static FrequencyBand fromGlobalKey(int globalKey) {
        int ord = globalKey / 1000;
        FrequencyBand[] vals = values();
        return (ord >= 0 && ord < vals.length) ? vals[ord] : VHF;
    }

    public static int channelOf(int globalKey) {
        return globalKey % 1000;
    }

    // ── Frequency display ──────────────────────────────────────────────────────

    public String freqDisplay(int channelIndex) {
        float f = freqMin + channelIndex * freqStep;
        return freqStep < 1f ? String.format("%.1f %s", f, unit)
                             : String.format("%.0f %s", f, unit);
    }

    /** Parse user-entered frequency value (in this band's unit) → clamped channel index. */
    public int channelFromFreq(float freq) {
        return Math.clamp(Math.round((freq - freqMin) / freqStep), 0, channels - 1);
    }
}
