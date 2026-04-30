package com.dev1lroot.mcmods.omnitech.blocks.analog.speaker;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IAnalogInput;
import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
import com.dev1lroot.mcmods.omnitech.network.SpeakerPlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Block entity for {@link SpeakerBlock}.
 *
 * <p>Receives quantised PCM audio ({@link IAudioInput}) from the cable network
 * and broadcasts it to players within {@value #HEARING_RANGE} blocks via
 * {@link SpeakerPlayPacket}.  Also accepts a float analog signal
 * ({@link IAnalogInput}) as a fallback, emitting a beep proportional to the
 * signal level when no raw audio is present.
 */
public class SpeakerBlockEntity extends BlockEntity implements IAnalogInput, IAudioInput {

    /** Blocks within which players receive the audio packet. */
    private static final double HEARING_RANGE = 24.0;

    /** Ticks without a push before stored values are treated as zero/empty. */
    private static final long STALE_TICKS = 12;

    private float  signal     = 0f;
    private long   lastPushAt = Long.MIN_VALUE;
    private byte[] audioBuffer = new byte[0];
    private long   lastAudioAt = Long.MIN_VALUE;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SPEAKER.get(), pos, state);
    }

    @Override
    public void receiveAnalogSignal(float incomingSignal) {
        signal = Math.clamp(incomingSignal, 0f, 15f);
        if (level != null) lastPushAt = level.getGameTime();
        setChanged();
    }

    @Override
    public void receiveAudio(byte[] samples) {
        if (samples == null || samples.length == 0) return;
        audioBuffer = samples;
        if (level != null) lastAudioAt = level.getGameTime();
        setChanged();
    }

    public float getSignal() { return signal; }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SpeakerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        long now = level.getGameTime();
        boolean hasAudio  = now - be.lastAudioAt  <= STALE_TICKS && be.audioBuffer.length > 0;
        boolean hasSignal = now - be.lastPushAt   <= STALE_TICKS && be.signal >= 0.5f;

        if (hasAudio) {
            // Broadcast audio frame to nearby players
            SpeakerPlayPacket pkt = new SpeakerPlayPacket(pos, be.audioBuffer);
            double rangeSq = HEARING_RANGE * HEARING_RANGE;
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                    PacketDistributor.sendToPlayer(player, pkt);
                }
            }
            be.audioBuffer = new byte[0];

        } else if (hasSignal && now % 4 == 0) {
            // Analog-only fallback: emit a beep at the block position
            float volume = be.signal / 15.0f;
            level.playSound(null, pos,
                    SoundEvents.NOTE_BLOCK_BIT.value(),
                    SoundSource.BLOCKS,
                    volume, 1.0f);
        }
    }
}
