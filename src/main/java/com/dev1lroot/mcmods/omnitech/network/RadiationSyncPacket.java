/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.radiation.ClientRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server → Client: full list of nuclear explosion centers for HUD radiation display. */
public record RadiationSyncPacket(List<BlockPos> centers) implements CustomPacketPayload {

    public static final Type<RadiationSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "radiation_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationSyncPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.centers().size());
                        for (BlockPos pos : pkt.centers()) buf.writeBlockPos(pos);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<BlockPos> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(buf.readBlockPos());
                        return new RadiationSyncPacket(list);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RadiationSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientRadiationData.CENTERS.clear();
            ClientRadiationData.CENTERS.addAll(pkt.centers());
        });
    }
}
