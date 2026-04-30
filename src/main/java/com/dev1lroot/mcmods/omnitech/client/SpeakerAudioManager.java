package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side registry of {@link SpeakerAudioSource} instances, one per active
 * Speaker block position.  Sources that have not received audio for
 * {@value #EXPIRY_TICKS} ticks are closed and removed automatically.
 *
 * <p>All public methods must be called from the main client thread.
 */
public final class SpeakerAudioManager {

    /** Ticks of silence before a source is closed and removed. */
    private static final long EXPIRY_TICKS = 40;

    private static final Map<BlockPos, SpeakerAudioSource> sources    = new HashMap<>();
    private static final Map<BlockPos, Long>               lastAudioAt = new HashMap<>();

    private SpeakerAudioManager() {}

    /**
     * Delivers {@code samples} to the audio source at {@code pos}, creating a
     * new {@link SpeakerAudioSource} if one does not exist yet.
     *
     * @param pos        world position of the Speaker block
     * @param samples    8-bit unsigned PCM mono samples
     * @param sampleRate sample rate matching the data (typically 11025 Hz)
     * @param gameTime   current {@code level.getGameTime()} for expiry tracking
     */
    public static void queueAudio(BlockPos pos, byte[] samples, int sampleRate, long gameTime) {
        if (samples == null || samples.length == 0) return;

        SpeakerAudioSource src = sources.computeIfAbsent(pos,
                p -> new SpeakerAudioSource(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
        src.queueBuffer(samples, sampleRate);
        lastAudioAt.put(pos, gameTime);
    }

    /**
     * Removes sources that have been silent for more than {@value #EXPIRY_TICKS}
     * ticks.  Call once per client tick.
     */
    public static void tick(long gameTime) {
        Iterator<Map.Entry<BlockPos, Long>> it = lastAudioAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (gameTime - entry.getValue() > EXPIRY_TICKS) {
                SpeakerAudioSource src = sources.remove(entry.getKey());
                if (src != null) src.close();
                it.remove();
            }
        }
    }

    /** Closes and removes all active sources. Safe to call multiple times. */
    public static void closeAll() {
        sources.values().forEach(SpeakerAudioSource::close);
        sources.clear();
        lastAudioAt.clear();
    }
}
