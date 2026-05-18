/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

/**
 * Implemented by block entities that consume a quantized audio stream.
 * Samples are 8-bit unsigned PCM mono at {@link #SAMPLE_RATE} Hz.
 * This is distinct from {@link IAnalogInput} (float signal 0–15);
 * only dedicated audio consumers (e.g. Speakers) implement this interface.
 */
public interface IAudioInput {

    /** Transfer sample rate: 44100 Hz, 16-bit signed mono PCM (CD quality). */
    int SAMPLE_RATE = 44100;

    /** Receive a chunk of quantized audio samples. */
    void receiveAudio(byte[] samples);
}
