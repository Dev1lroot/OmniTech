/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.ReactorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the client when the player clicks the "FLUSH" button in the reactor GUI. */
public record DepressurizeReactorPacket(int containerId) implements CustomPacketPayload {

    public static final Type<DepressurizeReactorPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "depressurize_reactor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepressurizeReactorPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeInt(pkt.containerId),
                    buf -> new DepressurizeReactorPacket(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DepressurizeReactorPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.containerMenu.containerId != pkt.containerId()) return;
            if (!(sp.containerMenu instanceof ReactorMenu menu)) return;
            if (menu.reactorBE != null) menu.reactorBE.flush();
        });
    }
}
