/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: set the numeric ID on a Display block. */
public record SetDisplayIdPacket(BlockPos pos, int displayId) implements CustomPacketPayload {

    public static final Type<SetDisplayIdPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_display_id"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDisplayIdPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeInt(pkt.displayId); },
                    buf -> new SetDisplayIdPacket(buf.readBlockPos(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetDisplayIdPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos) instanceof DisplayBlockEntity d) {
                DisplayBlockEntity master = d.isMaster() ? d : d.getMasterEntity(sp.level());
                if (master != null) master.setPortId(pkt.displayId);
            }
        });
    }
}
