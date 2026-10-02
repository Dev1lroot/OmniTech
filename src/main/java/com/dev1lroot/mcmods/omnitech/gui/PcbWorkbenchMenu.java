/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbWorkbenchBlockEntity;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.mc.PcbCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

import static com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbWorkbenchBlockEntity.*;

public class PcbWorkbenchMenu extends PcbStationMenu {

    public static final int W = 246, H = 234;
    public static final int CANVAS_X = 70, CANVAS_Y = 18;
    public static final int SLOTS_Y = 116;
    public static final int INV_X = 42, INV_Y = 152;

    /** Client: the screen's working copy. Server: snapshot at open time. */
    private PcbDesign design;

    public PcbWorkbenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), PcbCodecs.DESIGN_STREAM.decode(buf));
    }

    private PcbWorkbenchMenu(int id, Inventory inv, BlockPos pos, PcbDesign design) {
        this(id, inv, clientContainer(inv, pos), pos, design);
    }

    public PcbWorkbenchMenu(int id, Inventory inv, PcbWorkbenchBlockEntity be, PcbDesign design) {
        this(id, inv, be, be.getBlockPos(), design);
    }

    private PcbWorkbenchMenu(int id, Inventory inv, Container c, BlockPos pos, PcbDesign design) {
        super(OmniTechMenuTypes.PCB_WORKBENCH.get(), id, pos, c, SLOT_COUNT, OmniTechBlocks.PCB_WORKBENCH);
        this.design = design;
        machineSlot(SLOT_PAPER, 8, SLOTS_Y);
        machineSlot(SLOT_SOURCE, 26, SLOTS_Y);
        machineSlot(SLOT_OUTPUT, 44, SLOTS_Y);
        playerSlots(inv, INV_X, INV_Y);
    }

    public PcbDesign getDesign() { return design; }
    public void setDesign(PcbDesign d) { design = d; }
}
