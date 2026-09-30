/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.items.BatteryItem;
import com.dev1lroot.mcmods.omnitech.items.BoreHeadItem;
import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * The opened bore: a head slot and a battery slot backed by the held bore's
 * {@code BORE_CONTENTS} component. Every change is written straight back to the stack.
 * The inventory slot holding the bore itself is locked while the menu is open.
 */
public class BoreMenu extends AbstractContainerMenu {

    public static final String LAYOUT_ID = "bore";

    private final Player player;
    private final InteractionHand hand;
    /** Writes itself back to the bore on every change. */
    private final SimpleContainer parts = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };
    /** Menu slot index of the bore in the player's inventory (-1 when held in the off hand). */
    private final int lockedSlot;
    private final int partSlotCount;

    /** Client-side constructor. */
    public BoreMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readEnum(InteractionHand.class));
    }

    /** Server-side constructor. */
    public BoreMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(OmniTechMenuTypes.BORE.get(), containerId);
        this.player = playerInventory.player;
        this.hand = hand;

        List<ItemStack> stored = BoreItem.getParts(player.getItemInHand(hand));
        parts.setItem(BoreItem.SLOT_HEAD, stored.get(BoreItem.SLOT_HEAD));
        parts.setItem(BoreItem.SLOT_BATTERY, stored.get(BoreItem.SLOT_BATTERY));

        GuiLayout layout = GuiLayoutLoader.load(LAYOUT_ID);
        List<GuiElementDef> partSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();
        for (GuiElementDef el : partSlots) {
            addSlot(new PartSlot(parts, el.slot_index, el.x, el.y));
        }
        this.partSlotCount = partSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
        // addPlayerInventory adds the 27 main slots, then the hotbar
        this.lockedSlot = hand == InteractionHand.MAIN_HAND
                ? partSlotCount + 27 + playerInventory.getSelectedSlot() : -1;
    }

    private void save() {
        ItemStack bore = player.getItemInHand(hand);
        if (bore.getItem() instanceof BoreItem) {
            BoreItem.setParts(bore, parts.getItem(BoreItem.SLOT_HEAD).copy(),
                    parts.getItem(BoreItem.SLOT_BATTERY).copy());
        }
    }

    public ItemStack getHead()    { return parts.getItem(BoreItem.SLOT_HEAD); }
    public ItemStack getBattery() { return parts.getItem(BoreItem.SLOT_BATTERY); }

    // ── Keep the bore itself in place ─────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId == lockedSlot && lockedSlot >= 0) return;
        if (input == ContainerInput.SWAP) {
            // number keys / F swap with a hotbar slot or the off hand
            if (hand == InteractionHand.MAIN_HAND && button == player.getInventory().getSelectedSlot()) return;
            if (hand == InteractionHand.OFF_HAND && button == Inventory.SLOT_OFFHAND) return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == lockedSlot) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int invEnd = slots.size();

        if (index < partSlotCount) {
            if (!moveItemStackTo(stack, partSlotCount, invEnd, true)) return ItemStack.EMPTY;
        } else {
            int target = stack.getItem() instanceof BoreHeadItem ? BoreItem.SLOT_HEAD
                    : stack.getItem() instanceof BatteryItem ? BoreItem.SLOT_BATTERY : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof BoreItem;
    }

    /** Accepts one head (slot 0) or one battery (slot 1). */
    private static class PartSlot extends Slot {
        PartSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() == BoreItem.SLOT_HEAD
                    ? stack.getItem() instanceof BoreHeadItem
                    : stack.getItem() instanceof BatteryItem;
        }

        @Override
        public int getMaxStackSize() { return 1; }
    }
}
