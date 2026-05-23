/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: list of active {@link com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlock}
 * positions and their radii, filtered to those near the receiving player's dimension and position.
 *
 * <p>Sent every 20 ticks (once per second) from {@link OmniTech#onServerTick}.
 * The client stores this in {@link GravityFieldManager} so the entity-render mixin can
 * tilt entity models toward the nearest active gravity source.
 */
public record GravityFieldSyncPacket(List<BlockPos> positions, List<Integer> radii)
        implements CustomPacketPayload {

    public static final Type<GravityFieldSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "gravity_field_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravityFieldSyncPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        int size = pkt.positions().size();
                        buf.writeVarInt(size);
                        for (BlockPos pos : pkt.positions()) buf.writeBlockPos(pos);
                        for (int r : pkt.radii())            buf.writeVarInt(r);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<BlockPos> pos  = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) pos.add(buf.readBlockPos());
                        List<Integer>  radii = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) radii.add(buf.readVarInt());
                        return new GravityFieldSyncPacket(pos, radii);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GravityFieldSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GravityFieldManager.updateFromPacket(pkt));
    }
}
