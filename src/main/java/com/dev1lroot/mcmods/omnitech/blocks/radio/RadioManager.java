package com.dev1lroot.mcmods.omnitech.blocks.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Server-side in-memory spectrum store for all RF bands.
 *
 * <p>Signals are dimension-scoped for ELF–UHF bands, and globally shared
 * across all dimensions for SHF/EHF (interdimensional) bands.
 * Transmitter positions are always stored per-dimension (used by the locator item).
 */
public final class RadioManager {

    public record AudioFrame(byte[] samples, long frameTime) {}

    // ELF–UHF: signals and audio keyed by dimension
    private static final Map<ResourceKey<Level>, Map<Integer, Float>>      SIGNALS_BY_DIM = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Integer, AudioFrame>> AUDIO_BY_DIM   = new HashMap<>();

    // SHF/EHF: signals and audio shared across all dimensions
    private static final Map<Integer, Float>      SIGNALS_GLOBAL = new HashMap<>();
    private static final Map<Integer, AudioFrame> AUDIO_GLOBAL   = new HashMap<>();

    // Transmitter positions — always per-dimension (for locator item)
    private static final Map<ResourceKey<Level>, Map<Integer, Set<BlockPos>>> TRANSMITTER_POSITIONS = new HashMap<>();

    private RadioManager() {}

    // ── Signal ────────────────────────────────────────────────────────────────

    public static void set(ResourceKey<Level> dim, int key, float value, boolean interdim) {
        if (interdim) {
            if (value <= 0f) SIGNALS_GLOBAL.remove(key);
            else             SIGNALS_GLOBAL.put(key, Math.min(15f, value));
        } else {
            Map<Integer, Float> m = SIGNALS_BY_DIM.computeIfAbsent(dim, k -> new HashMap<>());
            if (value <= 0f) m.remove(key);
            else             m.put(key, Math.min(15f, value));
        }
    }

    public static float get(ResourceKey<Level> dim, int key, boolean interdim) {
        if (interdim) return SIGNALS_GLOBAL.getOrDefault(key, 0f);
        Map<Integer, Float> m = SIGNALS_BY_DIM.get(dim);
        return m == null ? 0f : m.getOrDefault(key, 0f);
    }

    /** Snapshot of all channels in the given band, for the scanner. */
    public static float[] getRow(ResourceKey<Level> dim, FrequencyBand band) {
        float[] row = new float[band.channels()];
        for (int i = 0; i < band.channels(); i++) {
            int key = band.globalKey(i);
            if (band.interdimensional()) {
                row[i] = SIGNALS_GLOBAL.getOrDefault(key, 0f);
            } else {
                Map<Integer, Float> m = SIGNALS_BY_DIM.get(dim);
                row[i] = m == null ? 0f : m.getOrDefault(key, 0f);
            }
        }
        return row;
    }

    public static void clear(ResourceKey<Level> dim, int key, boolean interdim) {
        if (interdim) {
            SIGNALS_GLOBAL.remove(key);
        } else {
            Map<Integer, Float> m = SIGNALS_BY_DIM.get(dim);
            if (m != null) m.remove(key);
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    public static void setAudio(ResourceKey<Level> dim, int key, byte[] samples,
                                long frameTime, boolean interdim) {
        if (samples == null || samples.length == 0) {
            clearAudio(dim, key, interdim);
            return;
        }
        AudioFrame frame = new AudioFrame(samples, frameTime);
        if (interdim) {
            AUDIO_GLOBAL.put(key, frame);
        } else {
            AUDIO_BY_DIM.computeIfAbsent(dim, k -> new HashMap<>()).put(key, frame);
        }
    }

    public static AudioFrame getAudio(ResourceKey<Level> dim, int key, boolean interdim) {
        if (interdim) return AUDIO_GLOBAL.get(key);
        Map<Integer, AudioFrame> m = AUDIO_BY_DIM.get(dim);
        return m == null ? null : m.get(key);
    }

    public static void clearAudio(ResourceKey<Level> dim, int key, boolean interdim) {
        if (interdim) {
            AUDIO_GLOBAL.remove(key);
        } else {
            Map<Integer, AudioFrame> m = AUDIO_BY_DIM.get(dim);
            if (m != null) m.remove(key);
        }
    }

    // ── Transmitter positions (per-dimension) ─────────────────────────────────

    public static void registerTransmitter(ResourceKey<Level> dim, int key, BlockPos pos) {
        TRANSMITTER_POSITIONS.computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(key, k -> new HashSet<>()).add(pos.immutable());
    }

    public static void unregisterTransmitter(ResourceKey<Level> dim, int key, BlockPos pos) {
        Map<Integer, Set<BlockPos>> byKey = TRANSMITTER_POSITIONS.get(dim);
        if (byKey == null) return;
        Set<BlockPos> set = byKey.get(key);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) byKey.remove(key);
        if (byKey.isEmpty()) TRANSMITTER_POSITIONS.remove(dim);
    }

    public static Set<BlockPos> getTransmitterPositions(ResourceKey<Level> dim, int key) {
        Map<Integer, Set<BlockPos>> byKey = TRANSMITTER_POSITIONS.get(dim);
        if (byKey == null) return Collections.emptySet();
        return Collections.unmodifiableSet(byKey.getOrDefault(key, Collections.emptySet()));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Wipe all state — called on server start to remove stale data from a previous session. */
    public static void clearAll() {
        SIGNALS_BY_DIM.clear();
        SIGNALS_GLOBAL.clear();
        AUDIO_BY_DIM.clear();
        AUDIO_GLOBAL.clear();
        TRANSMITTER_POSITIONS.clear();
    }
}
