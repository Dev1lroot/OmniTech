/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: set the numeric ID on a GPIO port block. */
public record SetGPIOIdPacket(BlockPos pos, int portId) implements CustomPacketPayload {

    public static final Type<SetGPIOIdPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_gpio_id"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGPIOIdPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.portId); },
                    buf -> new SetGPIOIdPacket(buf.readBlockPos(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGPIOIdPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos) instanceof GPIOPortBlockEntity g) {
                g.setPortId(pkt.portId);
            }
        });
    }
}
