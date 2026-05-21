/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.NuclearExplosionEffect;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Client: notification that a nuclear explosion occurred at a world position. */
public record NuclearExplosionFxPacket(double x, double y, double z) implements CustomPacketPayload {

    public static final Type<NuclearExplosionFxPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "nuclear_explosion_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NuclearExplosionFxPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeDouble(pkt.x);
                        buf.writeDouble(pkt.y);
                        buf.writeDouble(pkt.z);
                    },
                    buf -> new NuclearExplosionFxPacket(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(NuclearExplosionFxPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> NuclearExplosionEffect.addEffect(pkt.x(), pkt.y(), pkt.z()));
    }
}
