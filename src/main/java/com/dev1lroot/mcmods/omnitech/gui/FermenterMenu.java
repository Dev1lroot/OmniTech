/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FermenterBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class FermenterMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public FermenterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    // Server constructor
    public FermenterMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.FERMENTER.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("fermenter");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(new Slot(container, el.slot_index, el.x, el.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(getContainerSlot(), stack);
                }
            });
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The mixture. Read from the (client-synced) block entity, so it is valid on both sides. */
    public Solution getSolution() {
        if (container instanceof FermenterBlockEntity be) return be.getSolution();
        return Solution.EMPTY;
    }

    public int getTotalAmount()     { return data.get(0); }
    public int getCapacity()        { return data.get(1); }
    public int getActiveReactions() { return data.get(2); }
    public int getLiveMicrobes()    { return data.get(3); }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        if (index < machineSlotCount) {
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (container.canPlaceItem(FermenterBlockEntity.SLOT_INPUT, slotStack)) {
            if (!this.moveItemStackTo(slotStack, FermenterBlockEntity.SLOT_INPUT,
                    FermenterBlockEntity.SLOT_INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (index < playerEnd) {
            if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.FERMENTER.get());
    }
}
