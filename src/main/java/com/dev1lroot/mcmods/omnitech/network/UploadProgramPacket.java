package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station.ProgrammingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: upload a program to the Microcontroller in Programming Station. */
public record UploadProgramPacket(BlockPos pos, String program) implements CustomPacketPayload {

    public static final int MAX_LEN = ProgrammingStationBlockEntity.MAX_PROGRAM_LEN;

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

    public static void handle(UploadProgramPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos) instanceof ProgrammingStationBlockEntity ps) {
                ps.uploadProgram(pkt.program);
            }
        });
    }
}
