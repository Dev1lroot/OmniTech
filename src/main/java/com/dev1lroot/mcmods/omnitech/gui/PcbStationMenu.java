/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Common plumbing for the PCB station menus: filtered machine slots + player inventory. */
public abstract class PcbStationMenu extends AbstractContainerMenu {

    protected final BlockPos pos;
    protected final Container container;
    private final Supplier<? extends Block> block;
    private int machineSlots;

    protected PcbStationMenu(MenuType<?> type, int id, BlockPos pos, Container container,
            int size, Supplier<? extends Block> block) {
        super(type, id);
        this.pos = pos;
        this.container = container != null ? container : new SimpleContainer(size);
        this.block = block;
    }

    /** Resolves the station's block entity on the client, or null. */
    protected static Container clientContainer(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof Container c ? c : null;
    }

    protected void machineSlot(int index, int x, int y) {
        addSlot(new Slot(container, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return container.canPlaceItem(index, stack); }
        });
        machineSlots++;
    }

    protected void playerSlots(Inventory inv, int x, int y) {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, x + c * 18, y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, x + c * 18, y + 58));
    }

    public BlockPos getPos() { return pos; }
    public Container getContainer() { return container; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerEnd = machineSlots + 27, hotbarEnd = playerEnd + 9;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, original);
        } else {
            boolean moved = false;
            for (int i = 0; i < machineSlots && !stack.isEmpty(); i++) {
                if (slots.get(i).mayPlace(stack)) moved |= moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) {
                moved = index < playerEnd
                        ? moveItemStackTo(stack, playerEnd, hotbarEnd, false)
                        : moveItemStackTo(stack, machineSlots, playerEnd, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), pos), player, block.get());
    }
}
