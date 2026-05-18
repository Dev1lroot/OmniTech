/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenRocketGuiPacket() implements CustomPacketPayload {

    public static final Type<OpenRocketGuiPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "open_rocket_gui"));

    /** No data to encode/decode — the server identifies the player from the connection. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRocketGuiPacket> CODEC =
            StreamCodec.unit(new OpenRocketGuiPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side handler: opens the rocket GUI for the riding player. */
    public static void handle(OpenRocketGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp
                    && sp.getVehicle() instanceof RocketEntity rocket) {
                sp.openMenu(rocket, buf -> buf.writeInt(rocket.getId()));
            }
        });
    }
}
