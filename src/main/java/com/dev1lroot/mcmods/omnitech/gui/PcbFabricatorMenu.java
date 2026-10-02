/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbFabricatorBlockEntity;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;

public class PcbFabricatorMenu extends PcbStationMenu implements FlushableMenu {

    private final ContainerData data;

    public PcbFabricatorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private PcbFabricatorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, clientContainer(inv, pos), pos, new SimpleContainerData(12));
    }

    public PcbFabricatorMenu(int id, Inventory inv, PcbFabricatorBlockEntity be, ContainerData data) {
        this(id, inv, be, be.getBlockPos(), data);
    }

    private PcbFabricatorMenu(int id, Inventory inv, Container c, BlockPos pos, ContainerData data) {
        super(OmniTechMenuTypes.PCB_FABRICATOR.get(), id, pos, c, PcbFabricatorBlockEntity.SLOT_COUNT,
                OmniTechBlocks.PCB_FABRICATOR);
        this.data = data;
        addDataSlots(data);
        GuiLayout layout = GuiLayoutLoader.load("pcb_fabricator");
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
    public int getResistAmount()     { return data.get(8); }
    public int getResistCapacity()   { return data.get(9); }
    public int getCopperPerBoard()   { return data.get(10); }
    public int getResistPerBoard()   { return data.get(11); }

    public FluidStack getResist() {
        return getResistAmount() > 0
                ? new FluidStack(PcbFabricatorBlockEntity.photoresist(), getResistAmount())
                : FluidStack.EMPTY;
    }

    @Override
    public com.dev1lroot.mcmods.omnitech.io.@org.jspecify.annotations.Nullable FluidFlushable flushTarget(net.minecraft.world.entity.player.Player player) {
        return container instanceof com.dev1lroot.mcmods.omnitech.io.FluidFlushable f ? f : null;
    }
}
