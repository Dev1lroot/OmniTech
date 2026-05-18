/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
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

/** Client → server: flash binary firmware data to the ROM in Programming Station. */
public record FlashRomPacket(BlockPos pos, byte[] data) implements CustomPacketPayload {

    public static final int MAX_SIZE = 1_048_576;

    public static final Type<FlashRomPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "flash_rom"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlashRomPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeByteArray(pkt.data);
                    },
                    buf -> new FlashRomPacket(buf.readBlockPos(), buf.readByteArray(MAX_SIZE))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlashRomPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos) instanceof ProgrammingStationBlockEntity ps) {
                ps.flashRom(pkt.data);
            }
        });
    }
}
