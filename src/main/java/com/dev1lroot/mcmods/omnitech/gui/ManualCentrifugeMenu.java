package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ManualCentrifugeBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ManualCentrifugeMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    // Slot positions (176x166 GUI)
    // Input on the left, 5 outputs in a column on the right
    private static final int INPUT_X    = 80,  INPUT_Y    = 17;
    private static final int OUTPUT_1_X = 44, OUTPUT_1_Y = 53;
    private static final int OUTPUT_2_X = 62, OUTPUT_2_Y = 53;
    private static final int OUTPUT_3_X = 80, OUTPUT_3_Y = 53;
    private static final int OUTPUT_4_X = 98, OUTPUT_4_Y = 53;
    private static final int OUTPUT_5_X = 116, OUTPUT_5_Y = 53;

    // Client constructor
    public ManualCentrifugeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server constructor
    public ManualCentrifugeMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.MANUAL_CENTRIFUGE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Machine slots: 0=input, 1-5=outputs
        addSlot(new Slot(container, ManualCentrifugeBlockEntity.SLOT_INPUT,    INPUT_X,    INPUT_Y));
        addSlot(new OutputSlot(container, ManualCentrifugeBlockEntity.SLOT_OUTPUT_1, OUTPUT_1_X, OUTPUT_1_Y));
        addSlot(new OutputSlot(container, ManualCentrifugeBlockEntity.SLOT_OUTPUT_2, OUTPUT_2_X, OUTPUT_2_Y));
        addSlot(new OutputSlot(container, ManualCentrifugeBlockEntity.SLOT_OUTPUT_3, OUTPUT_3_X, OUTPUT_3_Y));
        addSlot(new OutputSlot(container, ManualCentrifugeBlockEntity.SLOT_OUTPUT_4, OUTPUT_4_X, OUTPUT_4_Y));
        addSlot(new OutputSlot(container, ManualCentrifugeBlockEntity.SLOT_OUTPUT_5, OUTPUT_5_X, OUTPUT_5_Y));

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public int getKineticForce() { return data.get(0); }
    public int getRequiredKineticForce() { return data.get(1); }

    public int getKineticProgress() {
        int force = data.get(0);
        int required = data.get(1);
        return required != 0 ? force * 24 / required : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Output slots (1-5) → player inventory
        if (index >= 1 && index <= 5) {
            if (!this.moveItemStackTo(slotStack, 6, 42, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        }
        // Player inventory/hotbar (6-41) → input slot
        else if (index >= 6) {
            if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                if (index < 33) {
                    if (!this.moveItemStackTo(slotStack, 33, 42, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, 6, 33, false)) return ItemStack.EMPTY;
                }
            }
        }
        // Input slot (0) → player inventory
        else {
            if (!this.moveItemStackTo(slotStack, 6, 42, false)) return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.MANUAL_CENTRIFUGE.get());
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

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
