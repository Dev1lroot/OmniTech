package com.dev1lroot.mcmods.omnitech.client;

import org.jspecify.annotations.Nullable;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side utility that continuously reads PCM samples from the default
 * system microphone.
 *
 * <h3>Audio pipeline</h3>
 * <ol>
 *   <li>Capture: 44100 Hz, 16-bit signed mono PCM from the system mic.</li>
 *   <li>{@link #drainSamples()}: concatenates queued chunks and returns them
 *       as raw 44100 Hz 16-bit little-endian PCM, ready to feed an
 *       AL_FORMAT_MONO16 OpenAL buffer.</li>
 * </ol>
 *
 * <p>Call {@link #start()} when a Microphone block is within range and
 * {@link #stop()} when none is.  {@link #drainSamples()} is safe to call from
 * the main thread while the capture thread runs concurrently.  If no microphone
 * is available the class silently returns empty arrays everywhere.
 */
public final class MicrophoneCapture {

    /** Capture format: 44100 Hz, 16-bit signed mono, little-endian. */
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            44100f, 16, 1, 2, 44100f, false);

    /** Buffer size: 20 ms of audio at 44100 Hz (882 frames × 2 bytes = 1764 bytes). */
    private static final int BUFFER_BYTES = 882 * 2;

    /**
     * Maximum raw chunks held in the queue (≈ 300 ms).
     * Oldest chunks are discarded when the queue is full so drain never
     * returns an unexpectedly large batch.
     */
    private static final int MAX_QUEUE_SIZE = 15;

    private static volatile String selectedDevice = "";
    private static volatile float rmsLevel = 0f;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static Thread captureThread = null;
    private static TargetDataLine line = null;

    /** Thread-safe queue of raw 16-bit PCM chunks produced by the capture thread. */
    private static final ConcurrentLinkedQueue<byte[]> sampleQueue = new ConcurrentLinkedQueue<>();

    private MicrophoneCapture() {}

    public static boolean isRunning() { return running.get(); }

    /** Current normalised RMS amplitude (0.0 = silence, 1.0 = clipping). */
    public static float getRmsLevel() { return rmsLevel; }

    /**
     * Returns the display names of all available input devices (mixers with TargetDataLine support).
     * The list does not include the implicit "default" entry.
     */
    public static List<String> getAvailableInputDevices() {
        List<String> devices = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(info)) {
                    devices.add(mixerInfo.getName());
                }
            } catch (Exception ignored) {}
        }
        return devices;
    }

    /**
     * Switches to a different input device. Pass "" for the system default.
     * If capture is already running it is restarted on the new device.
     */
    public static void setDevice(String deviceName) {
        selectedDevice = deviceName == null ? "" : deviceName;
        if (running.get()) {
            stop();
            start();
        }
    }

    /**
     * Starts the capture thread.  Device acquisition ({@link AudioSystem#getLine} /
     * {@link TargetDataLine#open}) happens inside the thread so this method never
     * blocks the caller (the Minecraft main thread).
     * No-op if already running.
     */
    public static void start() {
        if (running.getAndSet(true)) return;
        sampleQueue.clear();
        captureThread = new Thread(MicrophoneCapture::captureLoop, "omnitech-mic-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private static @Nullable TargetDataLine openNamedDevice(String name, DataLine.Info info) {
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (!mixerInfo.getName().equals(name)) continue;
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    return (TargetDataLine) mixer.getLine(info);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Signals the capture thread to stop and returns immediately.
     *
     * <p>{@link TargetDataLine#stop()} and {@link TargetDataLine#close()} are
     * dispatched to a short-lived daemon thread so this method never blocks the
     * caller.  On Linux (ALSA/PulseAudio) those calls can stall for seconds
     * while the driver drains hardware buffers — blocking the Minecraft main
     * thread or the world-save path would cause an infinite hang.
     */
    public static void stop() {
        if (!running.getAndSet(false)) return;
        rmsLevel = 0f;
        sampleQueue.clear();
        TargetDataLine l = line;
        line = null;
        Thread t = captureThread;
        captureThread = null;
        Thread closer = new Thread(() -> {
            // stop() unblocks any pending read(); close() releases the ALSA device.
            // Split into two try-blocks so close() always runs even if stop() throws.
            if (l != null) {
                try { l.stop(); } catch (Exception ignored) {}
                try { l.close(); } catch (Exception ignored) {}
            }
            if (t != null) t.interrupt();
        }, "omnitech-mic-close");
        closer.setDaemon(true);
        closer.start();
    }

    /**
     * Drains all accumulated raw PCM chunks from the capture thread and returns
     * them concatenated as a single 44100 Hz 16-bit signed little-endian byte array,
     * ready to feed directly into an AL_FORMAT_MONO16 OpenAL buffer.
     *
     * <p>Returns an empty array when no new data has arrived since the last call.
     * This method is safe to call from the main/client-tick thread.
     */
    public static byte[] drainSamples() {
        List<byte[]> chunks = new ArrayList<>();
        int totalBytes = 0;
        byte[] chunk;
        while ((chunk = sampleQueue.poll()) != null) {
            chunks.add(chunk);
            totalBytes += chunk.length;
        }
        if (totalBytes == 0) return new byte[0];

        byte[] out = new byte[totalBytes];
        int offset = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, out, offset, c.length);
            offset += c.length;
        }
        return out;
    }

    // ── Background capture thread ─────────────────────────────────────────────

    private static void captureLoop() {
        TargetDataLine myLine;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (selectedDevice.isEmpty()) {
                if (!AudioSystem.isLineSupported(info)) { running.set(false); return; }
                myLine = (TargetDataLine) AudioSystem.getLine(info);
            } else {
                myLine = openNamedDevice(selectedDevice, info);
                if (myLine == null) { running.set(false); return; }
            }
            myLine.open(FORMAT);
            myLine.start();
        } catch (LineUnavailableException e) {
            running.set(false);
            return;
        }

        // Publish the line.  If stop() fired while we were blocked in open(),
        // stop()'s closer thread may have seen line==null — close locally and exit.
        line = myLine;
        if (!running.get()) {
            try { myLine.stop(); } catch (Exception ignored) {}
            try { myLine.close(); } catch (Exception ignored) {}
            line = null;
            return;
        }

        byte[] buf = new byte[BUFFER_BYTES];
        while (running.get()) {
            TargetDataLine l = line;
            if (l == null) break;
            int read = l.read(buf, 0, buf.length);
            if (read <= 0) continue;

            rmsLevel = computeRms(buf, read);

            // Drop oldest entries if the queue is growing faster than it's drained
            while (sampleQueue.size() >= MAX_QUEUE_SIZE) sampleQueue.poll();

            byte[] copy = new byte[read];
            System.arraycopy(buf, 0, copy, 0, read);
            sampleQueue.offer(copy);
        }
    }

    /** Normalised RMS of 16-bit little-endian PCM (result in [0, 1]). */
    private static float computeRms(byte[] buf, int length) {
        double sum = 0;
        int samples = length / 2;
        for (int i = 0; i + 1 < length; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xFF));
            sum += (double) s * s;
        }
        return (float) Math.min(1.0, Math.sqrt(sum / samples) / 32768.0);
    }
}
