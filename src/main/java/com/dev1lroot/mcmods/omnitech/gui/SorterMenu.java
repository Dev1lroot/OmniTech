/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.SorterBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

/**
 * Menu for {@link SorterBlockEntity}.
 *
 * <p>Slot layout (menu indices):
 * <pre>
 *  0–26  : Filter slots (27 total, 3 rows × 9)
 * 27–53  : Player inventory (3 rows × 9)
 * 54–62  : Player hotbar (9)
 * </pre>
 */
public class SorterMenu extends AbstractContainerMenu {

    private final Container container;
    private final int machineSlotCount;

    private static final int PLAYER_INV_START   = 27;
    private static final int PLAYER_HOT_START   = 54;

    /** Client-side constructor. */
    public SorterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    /** Server-side constructor. */
    public SorterMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity) {
        super(OmniTechMenuTypes.SORTER.get(), containerId);
        this.container = (Container) blockEntity;

        GuiLayout layout = GuiLayoutLoader.load("sorter");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(new Slot(container, el.slot_index, el.x, el.y));
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index < PLAYER_INV_START) {
            if (!moveItemStackTo(slotStack, PLAYER_INV_START, 63, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            if (!moveItemStackTo(slotStack, 0, PLAYER_INV_START, false)) {
                if (index < PLAYER_HOT_START) {
                    if (!moveItemStackTo(slotStack, PLAYER_HOT_START, 63, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOT_START, false)) return ItemStack.EMPTY;
                }
            }
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container instanceof BlockEntity be
                && stillValid(ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                        player, OmniTechBlocks.SORTER.get());
    }
}
