/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import com.dev1lroot.mcmods.omnitech.items.RomItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class LogicMachineMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    @Nullable private final LogicMachineBlockEntity blockEntity;

    private int syncedRunning = 0;

    /** Client-side constructor (opened from network). */
    public LogicMachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public LogicMachineMenu(int id, Inventory inv, @Nullable BlockEntity be) {
        super(OmniTechMenuTypes.LOGIC_MACHINE.get(), id);
        this.pos = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.blockEntity = be instanceof LogicMachineBlockEntity lm ? lm : null;

        // Slot 0 = CPU (Microcontroller), Slot 1 = ROM
        // Positions are in menu coordinate space (leftPos + x, topPos + y)
        if (blockEntity != null) {
            var slots = blockEntity.getItemSlots();
            addSlot(new Slot(slots, 0, 44, 36) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MicrocontrollerItem;
                }
            });
            addSlot(new Slot(slots, 1, 100, 36) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof RomItem;
                }
            });
        }

        // Player inventory (3 rows at y=58, 76, 94; hotbar at y=102)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 39 + col * 18, 58 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 39 + col * 18, 102));

        addDataSlots(new ContainerData() {
            @Override public int get(int i) {
                return (blockEntity != null && blockEntity.isRunning()) ? 1 : 0;
            }
            @Override public void set(int i, int v) { syncedRunning = v; }
            @Override public int getCount() { return 1; }
        });
    }

    public BlockPos getBlockPos() { return pos; }

    @Nullable
    public LogicMachineBlockEntity getBlockEntity() { return blockEntity; }

    public boolean isRunning() { return syncedRunning != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof LogicMachineBlockEntity lm) {
            return lm.handleButton(id);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < slots.size()) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem().copy();
            ItemStack orig  = stack.copy();

            if (index == 0) { // CPU slot → try to move to inventory
                if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
            } else if (index == 1) { // ROM slot → try to move to inventory
                if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
            } else { // from inventory → try machine slots
                if (stack.getItem() instanceof MicrocontrollerItem) {
                    if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
                } else if (stack.getItem() instanceof RomItem) {
                    if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }

            slot.set(stack);
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            if (stack.getCount() == orig.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
            return orig;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.LOGIC_MACHINE.get());
    }
}
