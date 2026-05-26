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
 * Server → Client: list of active gravity sources near the receiving player.
 * Each source carries its position, outer radius, and inner radius so the client
 * can replicate the graduated-gravity falloff for rendering and physics.
 */
public record GravityFieldSyncPacket(
        List<BlockPos> positions,
        List<Integer>  outerRadii,
        List<Integer>  innerRadii)
        implements CustomPacketPayload {

    public static final Type<GravityFieldSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "gravity_field_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravityFieldSyncPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        int size = pkt.positions().size();
                        buf.writeVarInt(size);
                        for (BlockPos pos : pkt.positions())   buf.writeBlockPos(pos);
                        for (int r : pkt.outerRadii())         buf.writeVarInt(r);
                        for (int r : pkt.innerRadii())         buf.writeVarInt(r);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<BlockPos> pos        = new ArrayList<>(size);
                        List<Integer>  outerRadii = new ArrayList<>(size);
                        List<Integer>  innerRadii = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) pos.add(buf.readBlockPos());
                        for (int i = 0; i < size; i++) outerRadii.add(buf.readVarInt());
                        for (int i = 0; i < size; i++) innerRadii.add(buf.readVarInt());
                        return new GravityFieldSyncPacket(pos, outerRadii, innerRadii);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GravityFieldSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GravityFieldManager.updateFromPacket(pkt));
    }
}
