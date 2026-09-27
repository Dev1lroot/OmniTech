/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_capacitor.ElectricCapacitorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ElectricCapacitorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Client-side constructor — reads block pos from packet. */
    public ElectricCapacitorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(5));
    }

    /** Server-side constructor. */
    public ElectricCapacitorMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_CAPACITOR.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("electric_capacitor");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    /** Stored energy in kJ (0..maxEu). */
    public int getStoredEu()  { return data.get(0); }
    /** Capacity in kJ. */
    public int getMaxEu()     { return data.get(1); }
    /** Charging power in W (1 s average). */
    public float getInputWatts()   { return data.get(2); }
    /** Discharging power in W (1 s average). */
    public float getOutputWatts()  { return data.get(3); }
    /** Line voltage of the charging source in V. */
    public float getInputVoltage() { return PowerMeter.decodeVolts(data.get(4)); }
    /** Terminal voltage across the plates in V, from E = ½·C·U². */
    public float getVoltage() {
        return ElectricCapacitorBlockEntity.terminalVoltage(getStoredEu(), getMaxEu());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_CAPACITOR.get());
    }
}
