/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.SpeakerAudioManager;
import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client to deliver a quantised audio frame to players near a
 * Speaker block.  The client queues the samples to the OpenAL source managed
 * by {@link SpeakerAudioManager}.
 */
public record SpeakerPlayPacket(BlockPos pos, byte[] samples) implements CustomPacketPayload {

    public static final Type<SpeakerPlayPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "speaker_play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerPlayPacket> CODEC =
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
                        return new SpeakerPlayPacket(pos, samples);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeakerPlayPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            SpeakerAudioManager.queueAudio(
                    pkt.pos(), pkt.samples(),
                    IAudioInput.SAMPLE_RATE,
                    mc.level.getGameTime());
        });
    }
}
