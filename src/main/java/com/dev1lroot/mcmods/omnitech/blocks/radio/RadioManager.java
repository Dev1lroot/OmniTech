package com.dev1lroot.mcmods.omnitech.blocks.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side in-memory store for the virtual FM radio spectrum.
 * Transmitters write their signal each tick; receivers and the scanner read it.
 * Cleared on every server start so stale values from a previous session don't persist.
 */
public final class RadioManager {

    /**
     * A PCM audio frame stored per frequency channel.
     * {@code frameTime} is the game-tick at which the frame was captured by the transmitter,
     * used by receivers to detect new frames without replaying the same buffer.
     */
    public record AudioFrame(byte[] samples, long frameTime) {}

    private static final Map<Integer, Float>      SIGNALS = new HashMap<>();
    private static final Map<Integer, AudioFrame> AUDIO   = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Integer, Set<BlockPos>>> TRANSMITTER_POSITIONS = new HashMap<>();

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

    /**
     * Store a PCM audio frame at the given frequency.
     * {@code frameTime} is the game-tick when the transmitter originally received this frame;
     * receivers use it to detect new frames without re-pushing the same buffer.
     */
    public static void setAudio(int freqX10, byte[] samples, long frameTime) {
        if (samples == null || samples.length == 0) {
            AUDIO.remove(freqX10);
        } else {
            AUDIO.put(freqX10, new AudioFrame(samples, frameTime));
        }
    }

    /** Return the current audio frame at a frequency, or {@code null} if none. */
    public static AudioFrame getAudio(int freqX10) {
        return AUDIO.get(freqX10);
    }

    /** Remove the audio contribution for a frequency. */
    public static void clearAudio(int freqX10) {
        AUDIO.remove(freqX10);
    }

    public static void registerTransmitter(ResourceKey<Level> dim, int freqX10, BlockPos pos) {
        TRANSMITTER_POSITIONS.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(freqX10, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregisterTransmitter(ResourceKey<Level> dim, int freqX10, BlockPos pos) {
        Map<Integer, Set<BlockPos>> byFreq = TRANSMITTER_POSITIONS.get(dim);
        if (byFreq == null) return;
        Set<BlockPos> set = byFreq.get(freqX10);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) byFreq.remove(freqX10);
        if (byFreq.isEmpty()) TRANSMITTER_POSITIONS.remove(dim);
    }

    public static Set<BlockPos> getTransmitterPositions(ResourceKey<Level> dim, int freqX10) {
        Map<Integer, Set<BlockPos>> byFreq = TRANSMITTER_POSITIONS.get(dim);
        if (byFreq == null) return Collections.emptySet();
        return Collections.unmodifiableSet(byFreq.getOrDefault(freqX10, Collections.emptySet()));
    }

    /** Wipe the entire spectrum — called on server start to remove stale state. */
    public static void clearAll() {
        SIGNALS.clear();
        AUDIO.clear();
        TRANSMITTER_POSITIONS.clear();
    }
}
