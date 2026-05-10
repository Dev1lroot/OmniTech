package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Client: terminal output bytes to be processed by the client-side terminal. */
public record TerminalOutputPacket(BlockPos pos, byte[] data) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalOutputPacket.class);

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

    public static void handle(TerminalOutputPacket pkt, IPayloadContext ctx) {
        LOGGER.info("[client] TerminalOutputPacket received: {} bytes at {}", pkt.data.length, pkt.pos);
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) {
                LOGGER.warn("[client] level is null, dropping terminal output");
                return;
            }
            BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pkt.pos);
            if (be instanceof LogicMachineBlockEntity lm) {
                LOGGER.info("[client] delivering {} bytes to terminal", pkt.data.length);
                lm.handleTerminalOutput(pkt.data);
            } else {
                LOGGER.warn("[client] no LogicMachineBlockEntity at {}, be={}", pkt.pos, be);
            }
        });
    }
}
