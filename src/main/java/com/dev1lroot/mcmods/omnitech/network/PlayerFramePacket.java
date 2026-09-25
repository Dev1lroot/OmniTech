/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.PlayerFrames;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

/**
 * Client → Server: the sender's current gravity frame (see {@link PlayerFrames}) — tilted onto an
 * asteroid or rolled in zero-g. The server keeps it for server-side aiming and relays it to every
 * player tracking the sender as a {@link PlayerFrameUpdatePacket}.
 */
public record PlayerFramePacket(float x, float y, float z, float w) implements CustomPacketPayload {

    public static final Type<PlayerFramePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "player_frame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerFramePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PlayerFramePacket::x,
            ByteBufCodecs.FLOAT, PlayerFramePacket::y,
            ByteBufCodecs.FLOAT, PlayerFramePacket::z,
            ByteBufCodecs.FLOAT, PlayerFramePacket::w,
            PlayerFramePacket::new);

    public static PlayerFramePacket of(Quaternionf q) {
        return new PlayerFramePacket(q.x, q.y, q.z, q.w);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerFramePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Quaternionf q = new Quaternionf(pkt.x, pkt.y, pkt.z, pkt.w);
            if (!Float.isFinite(q.lengthSquared()) || q.lengthSquared() < 1e-6f) return;   // junk from a bad client
            q.normalize();
            PlayerFrames.setServer(sp.getUUID(), q);
            PacketDistributor.sendToPlayersTrackingEntity(sp, PlayerFrameUpdatePacket.of(sp.getId(), q));
        });
    }
}
