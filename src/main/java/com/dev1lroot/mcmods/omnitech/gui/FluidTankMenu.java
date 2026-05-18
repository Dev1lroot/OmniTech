/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidTankBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;

public class FluidTankMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public FluidTankMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server constructor
    public FluidTankMenu(int containerId, Inventory playerInventory,
                         BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.FLUID_TANK.get(), containerId);
        this.container = (Container) blockEntity;
        this.data      = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("fluid_tank");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == FluidTankBlockEntity.SLOT_BUCKET_IN) {
                addSlot(new BucketInSlot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new OutputSlot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Fluid Accessor ────────────────────────────────────────────────────────

    public FluidStack getFluidStack() {
        if (container instanceof FluidTankBlockEntity be) {
            return be.getFluid();
        }
        return FluidStack.EMPTY;
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getStoredFluid()   { return data.get(0); }
    public int getMaxFluid()      { return data.get(1); }

    // ── Menu logic ─────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < machineSlotCount) {
                if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(slotStack, result);
            } else {
                if (slotStack.getItem() instanceof BucketItem b && b.getContent() != Fluids.EMPTY) {
                    if (!moveItemStackTo(slotStack, 0, 1, false)) {
                        if (index < playerEnd) {
                            if (!moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                        } else {
                            if (!moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
                        }
                    }
                } else {
                    if (index < playerEnd) {
                        if (!moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                    } else {
                        if (!moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, slotStack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.FLUID_TANK.get());
    }

    private static class BucketInSlot extends Slot {
        public BucketInSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof BucketItem b && b.getContent() != Fluids.EMPTY;
        }
    }

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
