/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.RamCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ExpansionSlotMenu extends AbstractContainerMenu {

    private final BlockPos pos;

    public ExpansionSlotMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public ExpansionSlotMenu(int id, Inventory inv, BlockEntity be) {
        super(OmniTechMenuTypes.EXPANSION_SLOT.get(), id);
        this.pos = be != null ? be.getBlockPos() : BlockPos.ZERO;

        if (be instanceof ExpansionSlotBlockEntity es) {
            // 8 slots in a 4×2 grid (top-left area of GUI)
            for (int i = 0; i < ExpansionSlotBlockEntity.SLOT_COUNT; i++) {
                final int fi = i;
                addSlot(new Slot(es, i, 8 + (i % 4) * 18, 18 + (i / 4) * 18) {
                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return s.getItem() instanceof RamCardItem;
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
    }

    public BlockPos getBlockPos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack orig  = stack.copy();
        int expansionSlots = ExpansionSlotBlockEntity.SLOT_COUNT;
        if (index < expansionSlots) {
            if (!moveItemStackTo(stack, expansionSlots, expansionSlots + 36, true))
                return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof RamCardItem) {
                if (!moveItemStackTo(stack, 0, expansionSlots, false))
                    return ItemStack.EMPTY;
            } else return ItemStack.EMPTY;
        }
        slot.set(stack);
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        if (stack.getCount() == orig.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return orig;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.EXPANSION_SLOT.get());
    }
}
