package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Client: terminal output bytes (now unused; terminal renders on Display block). */
public record TerminalOutputPacket(BlockPos pos, byte[] data) implements CustomPacketPayload {

    public static final Type<TerminalOutputPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "terminal_output"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalOutputPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeByteArray(pkt.data);
                    },
                    buf -> new TerminalOutputPacket(buf.readBlockPos(), buf.readByteArray(8192))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Terminal output is now rendered directly on the Display block; this packet is no longer sent.
    public static void handle(TerminalOutputPacket pkt, IPayloadContext ctx) { }
}
