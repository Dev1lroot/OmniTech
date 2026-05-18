/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.keyboard.KeyboardBlock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → Server: client-side keyboard capture ended (window lost focus, level unloaded, etc.). */
public record KeyboardReleasePacket() implements CustomPacketPayload {

    public static final Type<KeyboardReleasePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "keyboard_release"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyboardReleasePacket> CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new KeyboardReleasePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KeyboardReleasePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                KeyboardBlock.clearSession(sp.getUUID());
            }
        });
    }
}
