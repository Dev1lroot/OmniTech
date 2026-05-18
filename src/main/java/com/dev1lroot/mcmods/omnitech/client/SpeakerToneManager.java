/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates continuous sine-wave PCM for Speaker blocks driven by MMIO tone writes.
 * Each active tone feeds into {@link SpeakerAudioManager} every client tick.
 * All methods must be called from the main client thread.
 */
public final class SpeakerToneManager {

    private static final int SAMPLE_RATE     = IAudioInput.SAMPLE_RATE;
    private static final int SAMPLES_PER_TICK = SAMPLE_RATE / 20;

    private static final Map<BlockPos, ToneState> tones = new HashMap<>();

    private static final class ToneState {
        int    volume;
        int    frequency;
        double phase = 0.0;

        ToneState(int volume, int frequency) {
            this.volume    = volume;
            this.frequency = frequency;
        }
    }

    private SpeakerToneManager() {}

    public static void setTone(BlockPos pos, int volume, int frequency) {
        if (frequency == 0 || volume == 0) {
            tones.remove(pos);
        } else {
            ToneState state = tones.computeIfAbsent(pos, p -> new ToneState(0, 0));
            if (state.frequency != frequency) state.phase = 0.0;
            state.volume    = volume;
            state.frequency = frequency;
        }
    }

    public static void tick(long gameTime) {
        for (Map.Entry<BlockPos, ToneState> entry : tones.entrySet()) {
            ToneState ts = entry.getValue();
            if (ts.frequency <= 0 || ts.volume <= 0) continue;
            byte[] pcm = generatePcm(ts);
            SpeakerAudioManager.queueAudio(entry.getKey(), pcm, SAMPLE_RATE, gameTime);
        }
    }

    public static void closeAll() {
        tones.clear();
    }

    private static byte[] generatePcm(ToneState ts) {
        byte[] pcm = new byte[SAMPLES_PER_TICK * 2];
        double phaseInc = 2.0 * Math.PI * ts.frequency / SAMPLE_RATE;
        double amplitude = (ts.volume / 255.0) * Short.MAX_VALUE;
        for (int i = 0; i < SAMPLES_PER_TICK; i++) {
            short sample = (short) (Math.sin(ts.phase) * amplitude);
            pcm[i * 2]     = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            ts.phase += phaseInc;
        }
        ts.phase %= (2.0 * Math.PI);
        return pcm;
    }
}
