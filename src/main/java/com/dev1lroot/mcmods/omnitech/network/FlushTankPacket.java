/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.gui.FlushableMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: flush button pressed under a tank. The target is whatever machine
 * the player's open menu belongs to, so no position is trusted from the client.
 */
public record FlushTankPacket(int containerId, String tank) implements CustomPacketPayload {

    public static final Type<FlushTankPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "flush_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlushTankPacket> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.containerId); buf.writeUtf(p.tank, 64); },
            buf -> new FlushTankPacket(buf.readVarInt(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlushTankPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            var menu = sp.containerMenu;
            if (menu.containerId != pkt.containerId || !menu.stillValid(sp)) return;
            if (!(menu instanceof FlushableMenu fm)) return;
            var target = fm.flushTarget(sp);
            if (target != null && target.flushTank(pkt.tank)) {
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 0.8f);
            }
        });
    }
}
