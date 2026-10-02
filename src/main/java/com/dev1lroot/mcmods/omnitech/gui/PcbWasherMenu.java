/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbWasherBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;

public class PcbWasherMenu extends PcbStationMenu implements FlushableMenu {

    private final ContainerData data;

    public PcbWasherMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private PcbWasherMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, clientContainer(inv, pos), pos, new SimpleContainerData(6));
    }

    public PcbWasherMenu(int id, Inventory inv, PcbWasherBlockEntity be, ContainerData data) {
        this(id, inv, be, be.getBlockPos(), data);
    }

    private PcbWasherMenu(int id, Inventory inv, Container c, BlockPos pos, ContainerData data) {
        super(OmniTechMenuTypes.PCB_WASHER.get(), id, pos, c, PcbWasherBlockEntity.SLOT_COUNT,
                OmniTechBlocks.PCB_WASHER);
        this.data = data;
        addDataSlots(data);
        GuiLayout layout = GuiLayoutLoader.load("pcb_washer");
        layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt((GuiElementDef e) -> e.slot_index))
                .forEach(e -> machineSlot(e.slot_index, e.x, e.y));
        layout.addPlayerInventory(inv, this::addSlot);
    }

    public float getProgressScaled() { return data.get(1) > 0 ? data.get(0) * 100f / data.get(1) : 0f; }
    public int getAcidAmount()       { return data.get(2); }
    public int getAcidCapacity()     { return data.get(3); }
    public int getAcidPerBoard()     { return data.get(4); }

    public FluidStack getAcid() {
        int id = data.get(5);
        if (id < 0 || getAcidAmount() <= 0) return FluidStack.EMPTY;
        return new FluidStack(BuiltInRegistries.FLUID.byId(id), getAcidAmount());
    }

    @Override
    public com.dev1lroot.mcmods.omnitech.io.@org.jspecify.annotations.Nullable FluidFlushable flushTarget(net.minecraft.world.entity.player.Player player) {
        return container instanceof com.dev1lroot.mcmods.omnitech.io.FluidFlushable f ? f : null;
    }
}
