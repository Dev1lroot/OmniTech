package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server when the local player is speaking.
 * Carries one frame of 44100 Hz 16-bit signed mono PCM (~80 ms per frame).
 * The server forwards it to every player within VOICE_RANGE blocks.
 */
public record VoiceChatSendPacket(byte[] samples) implements CustomPacketPayload {

    /** Radius in blocks within which other players receive the voice audio. */
    public static final double VOICE_RANGE = 48.0;

    public static final Type<VoiceChatSendPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "voice_chat_send"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceChatSendPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.samples.length);
                        buf.writeBytes(pkt.samples);
                    },
                    buf -> {
                        int len = buf.readVarInt();
                        byte[] samples = new byte[len];
                        buf.readBytes(samples);
                        return new VoiceChatSendPacket(samples);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(VoiceChatSendPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) return;
            if (!(sender.level() instanceof ServerLevel serverLevel)) return;

            double rangeSq = VOICE_RANGE * VOICE_RANGE;
            double sx = sender.getX(), sy = sender.getY(), sz = sender.getZ();

            VoiceChatReceivePacket out = new VoiceChatReceivePacket(
                    sender.getUUID(), sx, sy, sz, pkt.samples());

            for (ServerPlayer listener : serverLevel.players()) {
                if (listener == sender) continue;
                if (listener.distanceToSqr(sx, sy, sz) <= rangeSq) {
                    PacketDistributor.sendToPlayer(listener, out);
                }
            }
        });
    }
}
