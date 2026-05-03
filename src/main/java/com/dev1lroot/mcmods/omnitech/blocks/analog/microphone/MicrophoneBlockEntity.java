package com.dev1lroot.mcmods.omnitech.blocks.analog.microphone;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IAnalogOutput;
import com.dev1lroot.mcmods.omnitech.util.AnalogNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for {@link MicrophoneBlock}.
 *
 * <p>The local player's microphone is captured client-side by
 * {@link com.dev1lroot.mcmods.omnitech.client.MicrophoneCapture}.  Every 4 ticks
 * a quantised PCM frame (8-bit unsigned mono, 11025 Hz) is sent to the server via
 * {@link com.dev1lroot.mcmods.omnitech.network.MicrophoneAudioPacket}.
 *
 * <p>On the server, {@link #receiveClientAudio} stores the latest samples and
 * accumulates the RMS-derived analog signal (max across all contributing players).
 * Every 4 ticks the block entity forwards both the float signal (via
 * {@link AnalogNetworkUtil#pushSignal}) and the raw PCM bytes (via
 * {@link AnalogNetworkUtil#pushAudio}) to the connected cable network.
 */
public class MicrophoneBlockEntity extends BlockEntity implements IAnalogOutput {

    /** Detection radius in blocks — must match the client-side scan range. */
    public static final float MAX_RANGE = 16.0f;

    private static final long STALE_TICKS = 12;

    private float  signal            = 0f;
    private float  signalAccumulator = 0f;
    private byte[] audioBuffer       = new byte[0];
    private long   lastAudioTick     = Long.MIN_VALUE;

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.MICROPHONE.get(), pos, state);
    }

    @Override
    public float getAnalogSignal() { return signal; }

    /**
     * Called by {@link com.dev1lroot.mcmods.omnitech.network.MicrophoneAudioPacket}
     * on the server thread.  Computes RMS of the quantised samples to derive the
     * analog signal and stores the samples for forwarding to IAudioInput endpoints.
     */
    public void receiveClientAudio(byte[] samples) {
        if (samples == null || samples.length == 0) return;
        audioBuffer = samples;

        // Compute RMS from 16-bit signed little-endian PCM samples → 0..1
        double sumSq = 0;
        int numSamples = samples.length / 2;
        for (int i = 0; i + 1 < samples.length; i += 2) {
            short s = (short) ((samples[i + 1] << 8) | (samples[i] & 0xFF));
            sumSq += (double) s * s;
        }
        float rms = (float) Math.min(1.0, Math.sqrt(sumSq / Math.max(1, numSamples)) / 32768.0);
        float analogSignal = rms * 15f;

        signalAccumulator = Math.max(signalAccumulator, analogSignal);
        if (level != null) lastAudioTick = level.getGameTime();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            MicrophoneBlockEntity be) {
        if (level.getGameTime() % 4 != 0) return;

        if (level.getGameTime() - be.lastAudioTick > STALE_TICKS) {
            be.signal = 0f;
            be.audioBuffer = new byte[0];
        } else {
            be.signal = be.signalAccumulator;
        }
        be.signalAccumulator = 0f;

        AnalogNetworkUtil.pushSignal(level, pos, be.signal, Direction.values());
        if (be.audioBuffer.length > 0) {
            AnalogNetworkUtil.pushAudio(level, pos, be.audioBuffer, Direction.values());
        }
        be.setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        signal = input.getFloatOr("Signal", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("Signal", signal);
    }
}
