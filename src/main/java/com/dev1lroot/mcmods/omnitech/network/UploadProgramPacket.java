package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: legacy MCU program upload — superseded by FlashRomPacket. */
public record UploadProgramPacket(BlockPos pos, String program) implements CustomPacketPayload {

    private static final int MAX_LEN = 1_048_576;

    public static final Type<UploadProgramPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "upload_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UploadProgramPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeUtf(pkt.program, MAX_LEN);
                    },
                    buf -> new UploadProgramPacket(buf.readBlockPos(), buf.readUtf(MAX_LEN))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Superseded by FlashRomPacket — no-op kept for wire compatibility.
    public static void handle(UploadProgramPacket pkt, IPayloadContext ctx) { }
}
