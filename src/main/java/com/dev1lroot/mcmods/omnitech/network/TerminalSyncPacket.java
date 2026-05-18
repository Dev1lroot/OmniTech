/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Client: full terminal cell snapshot so the client can reconstruct the display. */
public record TerminalSyncPacket(BlockPos pos, int[] cells, int cursorRow, int cursorCol)
        implements CustomPacketPayload {

    public static final Type<TerminalSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "terminal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSyncPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeVarInt(pkt.cells.length);
                        for (int c : pkt.cells) buf.writeInt(c);
                        buf.writeVarInt(pkt.cursorRow);
                        buf.writeVarInt(pkt.cursorCol);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        int len = buf.readVarInt();
                        int[] cells = new int[len];
                        for (int i = 0; i < len; i++) cells[i] = buf.readInt();
                        int row = buf.readVarInt();
                        int col = buf.readVarInt();
                        return new TerminalSyncPacket(pos, cells, row, col);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Terminal sync is handled by the Display block now; this packet is no longer sent.
    public static void handle(TerminalSyncPacket pkt, IPayloadContext ctx) { }
}
