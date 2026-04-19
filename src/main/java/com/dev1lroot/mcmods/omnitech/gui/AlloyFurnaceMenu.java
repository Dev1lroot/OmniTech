package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class AlloyFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public AlloyFurnaceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
             playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
             new SimpleContainerData(6));
    }

    // Server constructor
    public AlloyFurnaceMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ALLOY_FURNACE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("alloy_furnace");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == AlloyFurnaceBlockEntity.SLOT_FUEL) {
                addSlot(new FuelSlot(container, el.slot_index, el.x, el.y));
            } else if (el.slot_index >= AlloyFurnaceBlockEntity.SLOT_OUTPUT_1) {
                addSlot(new OutputSlot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new Slot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    public boolean isLit() {
        return data.get(0) > 0;
    }

    public int getBurnProgress() {
        int burnTime = data.get(0);
        int maxBurnTime = data.get(1);
        return maxBurnTime != 0 ? burnTime * 14 / maxBurnTime : 0;
    }

    public int getCookProgress() {
        int cookProgress = data.get(2);
        int cookTotalTime = data.get(3);
        return cookTotalTime != 0 ? cookProgress * 41 / cookTotalTime : 0;
    }

    public float getCookProgressScaled() {
        int total = data.get(3);
        return total > 0 ? data.get(2) * 100f / total : 0f;
    }

    public int getTemperature() {
        return data.get(4);
    }

    public int getRequiredTemperature() {
        return data.get(5);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index == AlloyFurnaceBlockEntity.SLOT_OUTPUT_1 || index == AlloyFurnaceBlockEntity.SLOT_OUTPUT_2) {
                if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(slotStack, itemstack);
            } else if (index >= playerStart) {
                if (isFuel(slotStack)) {
                    if (!this.moveItemStackTo(slotStack, AlloyFurnaceBlockEntity.SLOT_FUEL, AlloyFurnaceBlockEntity.SLOT_FUEL + 1, false)) {
                        if (!this.moveItemStackTo(slotStack, 0, AlloyFurnaceBlockEntity.SLOT_FUEL, false)) return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(slotStack, 0, AlloyFurnaceBlockEntity.SLOT_FUEL, false)) {
                    if (index < playerEnd) {
                        if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
                    }
                }
            } else {
                if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, false)) return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (slotStack.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
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
