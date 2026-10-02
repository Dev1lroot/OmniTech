/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbBurnerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import java.util.Comparator;

public class PcbBurnerMenu extends PcbStationMenu {

    private final ContainerData data;

    public PcbBurnerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private PcbBurnerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, clientContainer(inv, pos), pos, new SimpleContainerData(8));
    }

    public PcbBurnerMenu(int id, Inventory inv, PcbBurnerBlockEntity be, ContainerData data) {
        this(id, inv, be, be.getBlockPos(), data);
    }

    private PcbBurnerMenu(int id, Inventory inv, Container c, BlockPos pos, ContainerData data) {
        super(OmniTechMenuTypes.PCB_BURNER.get(), id, pos, c, PcbBurnerBlockEntity.SLOT_COUNT,
                OmniTechBlocks.PCB_BURNER);
        this.data = data;
        addDataSlots(data);
        GuiLayout layout = GuiLayoutLoader.load("pcb_burner");
        layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt((GuiElementDef e) -> e.slot_index))
                .forEach(e -> machineSlot(e.slot_index, e.x, e.y));
        layout.addPlayerInventory(inv, this::addSlot);
    }

    public float getEnergyStored()   { return data.get(0) / 10f; }
    public float getMaxEu()          { return data.get(1) / 10f; }
    public float getProgressScaled() { return data.get(3) > 0 ? data.get(2) * 100f / data.get(3) : 0f; }
    public float getEuPerBoard()     { return data.get(4) / 10f; }
    public float getInputWatts()     { return data.get(5); }
    public float getVoltage()        { return PowerMeter.decodeVolts(data.get(6)); }
    public float getLoadWatts()      { return data.get(7); }
    public float getRatedWatts()     { return data.get(3) > 0 ? (float) ElectricUnits.toWatts(getEuPerBoard() / data.get(3)) : 0f; }
}
