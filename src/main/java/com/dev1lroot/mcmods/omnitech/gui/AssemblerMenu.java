/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.assembler.AssemblerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.items.BlueprintItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class AssemblerMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final BlockEntity blockEntity;
    private final int machineSlotCount;

    /** Client-side constructor. */
    public AssemblerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(5));
    }

    /** Server-side constructor. */
    public AssemblerMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ASSEMBLER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        Container container = (Container) blockEntity;
        GuiLayout layout = GuiLayoutLoader.load("assembler");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == AssemblerBlockEntity.SLOT_OUTPUT) {
                addSlot(new OutputSlot(container, el.slot_index, el.x, el.y));
            } else if (el.slot_index == AssemblerBlockEntity.SLOT_BLUEPRINT) {
                addSlot(new BlueprintSlot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new Slot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    public float getEnergyStored()       { return data.get(0) / 10f; }
    public float getMaxEu()              { return data.get(1) / 10f; }
    public int   getCraftProgress()      { return data.get(2); }
    public int   getProcessingTime()     { return data.get(3); }
    public float getEuPerRecipe()        { return data.get(4) / 10f; }

    public float getCraftProgressScaled() {
        int max = getProcessingTime();
        return max > 0 ? getCraftProgress() * 100f / max : 0f;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack slotStack = slot.getItem();
        ItemStack result    = slotStack.copy();

        if (index < machineSlotCount) {
            // Machine → player
            if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player → machine: blueprints try blueprint slot first, then storage
            boolean moved;
            int bp = AssemblerBlockEntity.SLOT_BLUEPRINT;
            if (slotStack.getItem() instanceof BlueprintItem) {
                moved = moveItemStackTo(slotStack, bp, bp + 1, false);
                if (!slotStack.isEmpty())
                    moved = moveItemStackTo(slotStack, 0, AssemblerBlockEntity.STORAGE_SLOTS, false) || moved;
            } else {
                moved = moveItemStackTo(slotStack, 0, AssemblerBlockEntity.STORAGE_SLOTS, false);
            }
            if (!moved) return ItemStack.EMPTY;

            // Cycle between player main inv and hotbar for any remainder
            if (index < playerEnd) {
                if (!slotStack.isEmpty()) moveItemStackTo(slotStack, playerEnd, hotbarEnd, false);
            } else {
                if (!slotStack.isEmpty()) moveItemStackTo(slotStack, playerStart, playerEnd, false);
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
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ASSEMBLER.get());
    }

    /** Output slot — players cannot place items into it. */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }

    /** Blueprint slot — only accepts BlueprintItem instances. */
    private static class BlueprintSlot extends Slot {
        BlueprintSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof BlueprintItem; }
    }
}
