/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.research_table.ResearchTableBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ResearchTableMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;

    /** Client-side constructor (called by IMenuTypeExtension from FriendlyByteBuf). */
    public ResearchTableMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** Server-side constructor (called from {@link ResearchTableBlockEntity#createMenu}). */
    public ResearchTableMenu(int id, Inventory inv, BlockEntity be) {
        super(OmniTechMenuTypes.RESEARCH_TABLE.get(), id);
        this.blockEntity = be;

        if (be instanceof ResearchTableBlockEntity rt) {
            // Input slots 0-8 (3 × 3 grid)
            for (int i = 0; i < 9; i++) {
                addSlot(new Slot(rt, i, 8 + (i % 3) * 18, 14 + (i / 3) * 18));
            }
            // Output slot 9 — players may take but not insert
            addSlot(new Slot(rt, 9, 8, 91) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            });
            this.data = rt.getContainerData();
        } else {
            this.data = new SimpleContainerData(1);
        }
        addDataSlots(data);

        // Player main inventory (slots 10-36)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 164 + row * 18));
        // Hotbar (slots 37-45)
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 222));
    }

    public int getProgress() { return data.get(0); }

    public BlockEntity getBlockEntity() { return blockEntity; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem().copy();
        ItemStack orig  = stack.copy();

        if (index == 9) { // output → player inv
            if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
        } else if (index < 9) { // input → player inv
            if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
        } else if (index < 37) { // player main → inputs, then hotbar
            if (!moveItemStackTo(stack, 0, 9, false))
                if (!moveItemStackTo(stack, 37, 46, false)) return ItemStack.EMPTY;
        } else { // hotbar → inputs, then player main
            if (!moveItemStackTo(stack, 0, 9, false))
                if (!moveItemStackTo(stack, 10, 37, false)) return ItemStack.EMPTY;
        }

        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.getCount() == orig.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return orig;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(blockEntity instanceof ResearchTableBlockEntity)) return false;
        return stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.RESEARCH_TABLE.get());
    }
}
