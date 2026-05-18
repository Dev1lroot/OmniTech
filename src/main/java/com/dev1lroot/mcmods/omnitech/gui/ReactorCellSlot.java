/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCell;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ReactorCellSlot extends Slot {

    private final ServerLevel level;
    private final BlockPos    cellPos;

    public ReactorCellSlot(Container container, int index, int x, int y,
                            ServerLevel level, BlockPos cellPos) {
        super(container, index, x, y);
        this.level   = level;
        this.cellPos = cellPos;
    }

    // ── Gate: block ejection when rod is ≥ 100 °C ────────────────────────────

    @Override
    public boolean mayPickup(Player player) {
        return !ReactorRodItem.isHot(getItem());
    }

    // ── Gate: only rod items may enter a reactor cell ─────────────────────────

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof ReactorRodItem;
    }

    // ── Insertion: add reactor tags before storing ────────────────────────────

    @Override
    public void set(ItemStack newStack) {
        ItemStack current = getItem();
        if (!newStack.isEmpty()
                && (current.isEmpty() || !ItemStack.isSameItem(current, newStack))) {
            if (newStack.getItem() instanceof ReactorRodItem) {
                ReactorRodItem.setTemperature(newStack, 0);
                if (newStack.getItem() instanceof ReactorControlRodItem) {
                    ReactorControlRodItem.setControl(newStack, 100);
                }
            }
        }
        super.set(newStack);
        syncCellType(newStack);
    }

    // ── Ejection: strip reactor tags from the taken item ─────────────────────

    @Override
    public void onTake(Player player, ItemStack stack) {
        ReactorRodItem.removeReactorTags(stack);
        super.onTake(player, stack);
        syncCellType(ItemStack.EMPTY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void syncCellType(ItemStack stack) {
        BlockState state = level.getBlockState(cellPos);
        if (!(state.getBlock() instanceof ReactorCell)) return;

        ReactorCellType type = stack.isEmpty() || !(stack.getItem() instanceof ReactorRodItem rod)
                ? ReactorCellType.EMPTY
                : rod.getCellType();

        BlockState updated = state.setValue(ReactorCell.CELL_TYPE, type);
        if (!updated.equals(state)) {
            level.setBlock(cellPos, updated, 3);
        }
    }
}
