/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.SolderingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.mc.PcbCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.SolderingStationBlockEntity.*;

public class SolderingStationMenu extends PcbStationMenu {

    public static final int W = 246, H = 254;
    public static final int CANVAS_X = 70, CANVAS_Y = 18;
    public static final int PARTS_X = 8, PARTS_Y = 40;
    public static final int OUTPUT_X = 222, OUTPUT_Y = 149;
    public static final int INV_X = 42, INV_Y = 172;

    /** Placement plans keyed by board geometry; the client edits its own copy. */
    private final Map<Integer, List<PlacedPart>> plans;

    public SolderingStationMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), readPlans(buf));
    }

    private SolderingStationMenu(int id, Inventory inv, BlockPos pos, Map<Integer, List<PlacedPart>> plans) {
        this(id, inv, clientContainer(inv, pos), pos, plans);
    }

    public SolderingStationMenu(int id, Inventory inv, SolderingStationBlockEntity be, Map<Integer, List<PlacedPart>> plans) {
        this(id, inv, be, be.getBlockPos(), plans);
    }

    private SolderingStationMenu(int id, Inventory inv, Container c, BlockPos pos, Map<Integer, List<PlacedPart>> plans) {
        super(OmniTechMenuTypes.SOLDERING_STATION.get(), id, pos, c, SLOT_COUNT, OmniTechBlocks.SOLDERING_STATION);
        this.plans = plans;
        machineSlot(SLOT_BOARD, 8, 18);
        machineSlot(SLOT_SOLDER, 26, 18);
        for (int i = 0; i < PARTS_SLOTS; i++) machineSlot(PARTS_START + i, PARTS_X + (i % 3) * 18, PARTS_Y + (i / 3) * 18);
        machineSlot(SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        playerSlots(inv, INV_X, INV_Y);
    }

    /** The plan the player saved for this board geometry, or null if none yet. */
    public List<PlacedPart> getStoredPlan(int boardHash) { return plans.get(boardHash); }
    public void setPlan(int boardHash, List<PlacedPart> parts) { plans.put(boardHash, List.copyOf(parts)); }

    public static void writePlans(RegistryFriendlyByteBuf buf, Map<Integer, List<PlacedPart>> plans) {
        buf.writeVarInt(plans.size());
        plans.forEach((k, v) -> {
            buf.writeInt(k);
            PcbCodecs.PARTS_STREAM.encode(buf, v);
        });
    }

    public static Map<Integer, List<PlacedPart>> readPlans(RegistryFriendlyByteBuf buf) {
        Map<Integer, List<PlacedPart>> m = new HashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) m.put(buf.readInt(), PcbCodecs.PARTS_STREAM.decode(buf));
        return m;
    }
}
