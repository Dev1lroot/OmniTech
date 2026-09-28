/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.items.HelmetVision;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the player switched their space-suit helmet view mode. */
public record HelmetVisionPacket(int mode) implements CustomPacketPayload {

    public static final Type<HelmetVisionPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "helmet_vision"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelmetVisionPacket> CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeByte(pkt.mode), buf -> new HelmetVisionPacket(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelmetVisionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) HelmetVision.setMode(sp, HelmetVision.Mode.byId(pkt.mode));
        });
    }
}
