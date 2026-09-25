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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

/**
 * Server → Client: another player's gravity frame (see {@link PlayerFrames}), so their model is
 * drawn turned the way they really are — including a zero-g barrel roll.
 */
public record PlayerFrameUpdatePacket(int entityId, float x, float y, float z, float w) implements CustomPacketPayload {

    public static final Type<PlayerFrameUpdatePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "player_frame_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerFrameUpdatePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerFrameUpdatePacket::entityId,
            ByteBufCodecs.FLOAT, PlayerFrameUpdatePacket::x,
            ByteBufCodecs.FLOAT, PlayerFrameUpdatePacket::y,
            ByteBufCodecs.FLOAT, PlayerFrameUpdatePacket::z,
            ByteBufCodecs.FLOAT, PlayerFrameUpdatePacket::w,
            PlayerFrameUpdatePacket::new);

    public static PlayerFrameUpdatePacket of(int entityId, Quaternionf q) {
        return new PlayerFrameUpdatePacket(entityId, q.x, q.y, q.z, q.w);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerFrameUpdatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> PlayerFrames.setRemote(pkt.entityId(),
                new Quaternionf(pkt.x(), pkt.y(), pkt.z(), pkt.w()).normalize()));
    }
}
