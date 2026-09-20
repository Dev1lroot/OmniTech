/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidFillerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: the player pressed "Inject" on a Fluid Filler holding a flask, asking to
 * transfer {@code amount} mB of the machine's current input fluid into it. Handled by
 * {@link FluidFillerBlockEntity#tryInjectFlask}, which clamps to whatever's actually available
 * and whatever room the flask has left.
 */
public record FluidFillerInjectPacket(BlockPos pos, int amount) implements CustomPacketPayload {

    public static final Type<FluidFillerInjectPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "fluid_filler_inject"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidFillerInjectPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeVarInt(pkt.amount);
                    },
                    buf -> new FluidFillerInjectPacket(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FluidFillerInjectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos()) instanceof FluidFillerBlockEntity be) {
                be.tryInjectFlask(pkt.amount());
            }
        });
    }
}
