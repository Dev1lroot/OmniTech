/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.VoiceAudioManager;
import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Sent server → client to deliver a voice frame from a speaking player.
 * Carries the speaker's current world position so the client can update the
 * OpenAL source location as the speaker moves.
 */
public record VoiceChatReceivePacket(UUID speakerId, double x, double y, double z,
                                     byte[] samples) implements CustomPacketPayload {

    public static final Type<VoiceChatReceivePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "voice_chat_receive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceChatReceivePacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUUID(pkt.speakerId);
                        buf.writeDouble(pkt.x);
                        buf.writeDouble(pkt.y);
                        buf.writeDouble(pkt.z);
                        buf.writeVarInt(pkt.samples.length);
                        buf.writeBytes(pkt.samples);
                    },
                    buf -> {
                        UUID id = buf.readUUID();
                        double x = buf.readDouble();
                        double y = buf.readDouble();
                        double z = buf.readDouble();
                        int len = buf.readVarInt();
                        byte[] samples = new byte[len];
                        buf.readBytes(samples);
                        return new VoiceChatReceivePacket(id, x, y, z, samples);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(VoiceChatReceivePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            VoiceAudioManager.queueAudio(
                    pkt.speakerId(), pkt.x(), pkt.y(), pkt.z(),
                    pkt.samples(), IAudioInput.SAMPLE_RATE,
                    mc.level.getGameTime());
        });
    }
}
