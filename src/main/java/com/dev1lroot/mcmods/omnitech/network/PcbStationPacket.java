/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbWorkbenchBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.SolderingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.PcbStationMenu;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.mc.PcbCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client → Server actions of the PCB stations: the workbench's drawing (save / print)
 * and the soldering station's part placement (save plan / solder).
 */
public record PcbStationPacket(BlockPos pos, int action, PcbDesign design, List<PlacedPart> parts)
        implements CustomPacketPayload {

    public static final int WORKBENCH_SAVE   = 0;
    public static final int WORKBENCH_PRINT  = 1;
    public static final int SOLDER_PLAN      = 10;
    public static final int SOLDER_ONE       = 11;
    public static final int SOLDER_BATCH     = 12;

    public static PcbStationPacket workbench(BlockPos pos, int action, PcbDesign d) {
        return new PcbStationPacket(pos, action, d, List.of());
    }

    public static PcbStationPacket soldering(BlockPos pos, int action, List<PlacedPart> parts) {
        return new PcbStationPacket(pos, action, PcbDesign.DEFAULT, parts);
    }

    public static final Type<PcbStationPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "pcb_station"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PcbStationPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.action);
                PcbCodecs.DESIGN_STREAM.encode(buf, p.design);
                PcbCodecs.PARTS_STREAM.encode(buf, p.parts);
            },
            buf -> new PcbStationPacket(buf.readBlockPos(), buf.readVarInt(),
                    PcbCodecs.DESIGN_STREAM.decode(buf), PcbCodecs.PARTS_STREAM.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PcbStationPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof PcbStationMenu menu) || !menu.getPos().equals(pkt.pos)) return;
            var be = sp.level().getBlockEntity(pkt.pos);
            if (be instanceof PcbWorkbenchBlockEntity wb) {
                if (pkt.action == WORKBENCH_SAVE || pkt.action == WORKBENCH_PRINT) wb.setDesign(pkt.design);
                if (pkt.action == WORKBENCH_PRINT) wb.print(sp);
            } else if (be instanceof SolderingStationBlockEntity ss) {
                if (pkt.action >= SOLDER_PLAN && pkt.action <= SOLDER_BATCH) ss.setPlan(pkt.parts);
                if (pkt.action == SOLDER_ONE || pkt.action == SOLDER_BATCH) ss.solder(sp, pkt.action == SOLDER_BATCH);
            }
        });
    }
}
