/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler.BoilerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class BoilerMenu extends AbstractContainerMenu {

    private final BoilerBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor
    public BoilerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server constructor
    public BoilerMenu(int containerId, Inventory playerInventory,
                      BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.BOILER.get(), containerId);
        this.blockEntity = (BoilerBlockEntity) blockEntity;
        this.data = data;
        addDataSlots(data);
        addPlayerInventory(playerInventory, 8, 84);
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public int getTemperature()  { return data.get(0); }
    public int getFluidAmount()  { return data.get(1); }
    public int getCapacity()     { return BoilerBlockEntity.MAX_FLUID; }

    public FluidStack getFluid() {
        return blockEntity != null ? blockEntity.getFluidTank() : FluidStack.EMPTY;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.BOILER.get());
    }
}
