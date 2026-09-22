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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class BoilerMenu extends AbstractContainerMenu {

    private final BoilerBlockEntity blockEntity;
    private final Container container;
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
        this.container = blockEntity instanceof Container c ? c : new SimpleContainer(BoilerBlockEntity.SLOT_COUNT);
        this.data = data;
        addDataSlots(data);
        addSlot(new OutputSlot(container, BoilerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addPlayerInventory(playerInventory, 8, 84);
    }

    /** Must match the {@code output_slot} element of {@code gui/boiler.json}. */
    private static final int OUTPUT_X = 120;
    private static final int OUTPUT_Y = 50;

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
        int playerStart = BoilerBlockEntity.SLOT_COUNT;
        int hotbarEnd   = playerStart + 36;

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack slotStack = slot.getItem();
        ItemStack result = slotStack.copy();

        // Only the output slot can be taken from; nothing can be put into the boiler by hand.
        if (index >= playerStart) return ItemStack.EMPTY;
        if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
        slot.onQuickCraft(slotStack, result);

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.BOILER.get());
    }
}
