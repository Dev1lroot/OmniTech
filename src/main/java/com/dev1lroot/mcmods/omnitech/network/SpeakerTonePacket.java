package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.SpeakerToneManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client when a Speaker block's MMIO tone changes.
 * volume=0 or frequency=0 means stop playing.
 */
public record SpeakerTonePacket(BlockPos pos, int volume, int frequency) implements CustomPacketPayload {

    public static final Type<SpeakerTonePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "speaker_tone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerTonePacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeByte(pkt.volume);
                        buf.writeShort(pkt.frequency);
                    },
                    buf -> new SpeakerTonePacket(buf.readBlockPos(), buf.readUnsignedByte(), buf.readUnsignedShort())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeakerTonePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;
            SpeakerToneManager.setTone(pkt.pos(), pkt.volume(), pkt.frequency());
        });
    }
}
