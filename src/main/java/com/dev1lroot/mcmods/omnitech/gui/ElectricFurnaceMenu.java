package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ElectricFurnaceBlockEntity;
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

public class ElectricFurnaceMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    // Slot positions
    private static final int INPUT_X = 56, INPUT_Y = 35;
    private static final int OUTPUT_X = 116, OUTPUT_Y = 35;

    /** Client-side constructor. */
    public ElectricFurnaceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(3));
    }

    /** Server-side constructor. */
    public ElectricFurnaceMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_FURNACE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        Container container = (Container) blockEntity;
        addSlot(new Slot(container, ElectricFurnaceBlockEntity.SLOT_INPUT,  INPUT_X,  INPUT_Y));
        addSlot(new OutputSlot(container, ElectricFurnaceBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    /** Cook progress (0..cookTotalTime). */
    public int getCookProgress()   { return data.get(0); }
    /** Total cook time in ticks. */
    public int getCookTotalTime()  { return data.get(1); }
    /** 1 if powered by EU, 0 otherwise. */
    public boolean isPowered()     { return data.get(2) == 1; }

    /** Progress arrow width (0..24 px), matches vanilla furnace arrow width. */
    public int getProgressArrowWidth() {
        int total = getCookTotalTime();
        return total != 0 ? getCookProgress() * 24 / total : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index == ElectricFurnaceBlockEntity.SLOT_OUTPUT) {
            if (!moveItemStackTo(slotStack, 2, 38, true)) return ItemStack.EMPTY;
        } else if (index < 2) {
            if (!moveItemStackTo(slotStack, 2, 38, false)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(slotStack, 0, 1, false)) {
                if (index < 29) {
                    if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(slotStack, 2, 29, false)) return ItemStack.EMPTY;
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
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_FURNACE.get());
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    /** Output slot — players cannot place items into it. */
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
