package com.dev1lroot.mcmods.omnitech.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side registry of {@link SpeakerAudioSource} instances, one per actively
 * speaking player.  The source position is updated on every incoming packet so
 * voices track moving players.  Sources idle for more than {@value #EXPIRY_TICKS}
 * ticks are closed and removed.
 *
 * <p>All public methods must be called from the main client thread.
 */
public final class VoiceAudioManager {

    /** Voice attenuation: starts rolling off at this distance (blocks). */
    private static final float REFERENCE_DISTANCE = 4.0f;
    /** Voice hearing limit (blocks) — must match VOICE_RANGE in VoiceChatSendPacket. */
    private static final float MAX_DISTANCE       = 48.0f;
    /** Standard linear rolloff. */
    private static final float ROLLOFF_FACTOR     = 1.0f;

    /** Ticks of silence before a source is closed and removed. */
    private static final long EXPIRY_TICKS = 40;

    private static final Map<UUID, SpeakerAudioSource> sources     = new HashMap<>();
    private static final Map<UUID, Long>               lastAudioAt = new HashMap<>();

    private VoiceAudioManager() {}

    /**
     * Delivers a voice frame from the player identified by {@code id}.
     * Creates the OpenAL source on first use; updates its position on every call
     * so the audio follows the speaker as they move.
     */
    public static void queueAudio(UUID id, double x, double y, double z,
                                   byte[] samples, int sampleRate, long gameTime) {
        if (samples == null || samples.length == 0) return;

        SpeakerAudioSource src = sources.computeIfAbsent(id, uuid -> {
            SpeakerAudioSource s = new SpeakerAudioSource(x, y, z,
                    REFERENCE_DISTANCE, MAX_DISTANCE, ROLLOFF_FACTOR);
            return s;
        });

        src.setPosition(x, y, z);
        src.queueBuffer(samples, sampleRate);
        lastAudioAt.put(id, gameTime);
    }

    /**
     * Removes sources that have been silent for more than {@value #EXPIRY_TICKS}
     * ticks.  Call once per client tick.
     */
    public static void tick(long gameTime) {
        Iterator<Map.Entry<UUID, Long>> it = lastAudioAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
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
