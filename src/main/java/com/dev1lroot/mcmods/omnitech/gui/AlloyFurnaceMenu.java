package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AlloyFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    // Slot positions
    private static final int INPUT_1_X = 38, INPUT_1_Y = 17;
    private static final int INPUT_2_X = 56, INPUT_2_Y = 17;
    private static final int INPUT_3_X = 74, INPUT_3_Y = 17;
    private static final int FUEL_X = 56, FUEL_Y = 53;
    private static final int OUTPUT_1_X = 116, OUTPUT_1_Y = 26;
    private static final int OUTPUT_2_X = 116, OUTPUT_2_Y = 53;

    // Client constructor
    public AlloyFurnaceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
             playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
             new SimpleContainerData(4));
    }

    // Server constructor
    public AlloyFurnaceMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ALLOY_FURNACE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Input slots (3)
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.SLOT_INPUT_1, INPUT_1_X, INPUT_1_Y));
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.SLOT_INPUT_2, INPUT_2_X, INPUT_2_Y));
        addSlot(new Slot(container, AlloyFurnaceBlockEntity.SLOT_INPUT_3, INPUT_3_X, INPUT_3_Y));

        // Fuel slot
        addSlot(new FuelSlot(container, AlloyFurnaceBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));

        // Output slots (2)
        addSlot(new OutputSlot(container, AlloyFurnaceBlockEntity.SLOT_OUTPUT_1, OUTPUT_1_X, OUTPUT_1_Y));
        addSlot(new OutputSlot(container, AlloyFurnaceBlockEntity.SLOT_OUTPUT_2, OUTPUT_2_X, OUTPUT_2_Y));

        // Player inventory
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public boolean isLit() {
        return data.get(0) > 0;
    }

    public int getBurnProgress() {
        int burnTime = data.get(0);
        int maxBurnTime = data.get(1);
        return maxBurnTime != 0 ? burnTime * 13 / maxBurnTime : 0;
    }

    public int getCookProgress() {
        int cookProgress = data.get(2);
        int cookTotalTime = data.get(3);
        return cookTotalTime != 0 ? cookProgress * 24 / cookTotalTime : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // Output slots (4-5)
            if (index == 4 || index == 5) {
                if (!this.moveItemStackTo(slotStack, 6, 42, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(slotStack, itemstack);
            }
            // Player inventory/hotbar (6-41)
            else if (index >= 6) {
                // Try fuel slot first
                if (isFuel(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, 3, 4, false)) {
                        // Then try input slots
                        if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
                // Otherwise try input slots
                else if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                    // Move between inventory and hotbar
                    if (index < 33) {
                        if (!this.moveItemStackTo(slotStack, 33, 42, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(slotStack, 6, 33, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            // Input/fuel slots (0-3)
            else if (!this.moveItemStackTo(slotStack, 6, 42, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, slotStack);
        }

        return itemstack;
    }

    private boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL) ||
               stack.is(Items.COAL_BLOCK) || stack.is(Items.LAVA_BUCKET) ||
               stack.is(Items.BLAZE_ROD) || stack.is(Items.STICK);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(container instanceof BlockEntity be ? be.getLevel() : null,
                container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.ALLOY_FURNACE.get());
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    // Custom slot that only accepts fuel
    private static class FuelSlot extends Slot {
        public FuelSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.COAL) || stack.is(Items.CHARCOAL) ||
                   stack.is(Items.COAL_BLOCK) || stack.is(Items.LAVA_BUCKET) ||
                   stack.is(Items.BLAZE_ROD) || stack.is(Items.STICK);
        }
    }

    // Custom slot for output only
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
