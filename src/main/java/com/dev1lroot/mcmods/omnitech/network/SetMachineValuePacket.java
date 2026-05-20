/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Generic packet: client → server, sets a named integer parameter on a block entity.
 * Used by pressure and temperature sliders on machine GUIs.
 */
public record SetMachineValuePacket(BlockPos pos, String key, int value) implements CustomPacketPayload {

    public static final Type<SetMachineValuePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_machine_value"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMachineValuePacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeUtf(pkt.key);
                        buf.writeInt(pkt.value);
                    },
                    buf -> new SetMachineValuePacket(buf.readBlockPos(), buf.readUtf(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetMachineValuePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Level level = sp.level();
            BlockEntity be = level.getBlockEntity(pkt.pos());
            if (be == null) return;
            switch (pkt.key()) {
                case "targetPressure" -> {
                    if (be instanceof RotaryCompressorBlockEntity rc) rc.setTargetPressure(pkt.value());
                    else if (be instanceof DecompressorBlockEntity dc)  dc.setTargetPressure(pkt.value());
                }
            }
        });
    }
}
