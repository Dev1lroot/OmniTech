/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.analog.microphone.MicrophoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server every 4 ticks while the local player is within range of a
 * {@link MicrophoneBlockEntity}.  Carries one frame of quantised PCM audio
 * (8-bit unsigned mono at 11025 Hz, ~200 ms per frame ≈ 2205 bytes) captured
 * from the player's system microphone.
 */
public record MicrophoneAudioPacket(BlockPos pos, byte[] samples) implements CustomPacketPayload {

    public static final Type<MicrophoneAudioPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "microphone_audio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MicrophoneAudioPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeVarInt(pkt.samples.length);
                        buf.writeBytes(pkt.samples);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        int len = buf.readVarInt();
                        byte[] samples = new byte[len];
                        buf.readBytes(samples);
                        return new MicrophoneAudioPacket(pos, samples);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MicrophoneAudioPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            BlockPos pos = pkt.pos();
            double distSq = sp.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double maxRange = MicrophoneBlockEntity.MAX_RANGE;
            if (distSq > maxRange * maxRange) return;

            BlockEntity be = sp.level().getBlockEntity(pos);
            if (be instanceof MicrophoneBlockEntity mic) {
                mic.receiveClientAudio(pkt.samples());
            }
        });
    }
}
