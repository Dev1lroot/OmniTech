package com.dev1lroot.mcmods.omnitech.client;

import org.lwjgl.openal.AL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wraps a single OpenAL streaming source positioned at one Speaker block.
 * Buffers are queued with {@link #queueBuffer} and freed automatically as
 * OpenAL finishes processing them.  The source uses the AL_FORMAT_MONO16
 * format matching the 16-bit signed little-endian PCM produced by {@link MicrophoneCapture}.
 *
 * <p>All methods must be called from the main client thread (which owns the
 * OpenAL context).
 */
public final class SpeakerAudioSource {

    private static final int MAX_QUEUED_BUFFERS = 3;

    private final int source;

    public SpeakerAudioSource(double x, double y, double z) {
        this(x, y, z, 4.0f, 24.0f, 1.0f);
    }

    public SpeakerAudioSource(double x, double y, double z,
                               float referenceDistance, float maxDistance, float rolloff) {
        source = AL10.alGenSources();
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, referenceDistance);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maxDistance);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, rolloff);
    }

    /** Updates the 3D world position of this source (for moving speakers such as players). */
    public void setPosition(double x, double y, double z) {
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
    }

    /**
     * Uploads {@code samples} as an OpenAL buffer and queues it on this source.
     * Processed buffers are freed first; if too many buffers are already queued
     * the new buffer is dropped to prevent audio lag buildup.
     */
    public void queueBuffer(byte[] samples, int sampleRate) {
        // Free any buffers that OpenAL has already finished playing
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buf = AL10.alSourceUnqueueBuffers(source);
            AL10.alDeleteBuffers(buf);
        }

        // Drop the incoming buffer if we are already saturated
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        if (queued >= MAX_QUEUED_BUFFERS) return;

        int buf = AL10.alGenBuffers();
        // ByteOrder.nativeOrder() so OpenAL reads the 16-bit values correctly on this platform
        ByteBuffer data = ByteBuffer.allocateDirect(samples.length)
                .order(ByteOrder.nativeOrder())
                .put(samples)
                .flip();
        AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, data, sampleRate);
        AL10.alSourceQueueBuffers(source, buf);

        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
    }

    /** Stops playback, unqueues and deletes all pending buffers, then deletes the source. */
    public void close() {
        AL10.alSourceStop(source);

        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        while (queued-- > 0) {
            int buf = AL10.alSourceUnqueueBuffers(source);
            AL10.alDeleteBuffers(buf);
        }

        AL10.alDeleteSources(source);
    }
}
