package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CokeOvenMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final CokeOvenEntity entity;

    // Client constructor (entity looked up by network ID from buffer)
    public CokeOvenMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                (CokeOvenEntity) playerInventory.player.level().getEntity(buf.readInt()),
                new SimpleContainerData(3));
    }

    // Server constructor
    public CokeOvenMenu(int containerId, Inventory playerInventory, CokeOvenEntity entity) {
        this(containerId, playerInventory, entity, new SimpleContainerData(3) {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> entity.isLit() ? 1 : 0;
                    case 1 -> entity.getProcessTimer();
                    case 2 -> entity.getProcessTotalTime();
                    default -> 0;
                };
            }
        });
    }

    private CokeOvenMenu(int containerId, Inventory playerInventory, CokeOvenEntity entity, ContainerData data) {
        super(OmniTechMenuTypes.COKE_OVEN.get(), containerId);
        this.entity = entity;
        this.container = entity != null ? entity.getInventory() : new net.minecraft.world.SimpleContainer(CokeOvenEntity.INVENTORY_SIZE);
        this.data = data;
        addDataSlots(data);

        // Input slots (0-8): 3×3 grid at x=8, y=17
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new CoalOnlySlot(container, CokeOvenEntity.SLOT_INPUT_START + row * 3 + col,
                        8 + col * 18, 17 + row * 18));
            }
        }

        // Active/burning slot (9): centered
        addSlot(new LockedSlot(container, CokeOvenEntity.SLOT_ACTIVE, 80, 35));

        // Output slots (10-18): 3×3 grid at x=134, y=17
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new OutputOnlySlot(container, CokeOvenEntity.SLOT_OUTPUT_START + row * 3 + col,
                        134 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    public boolean isLit()        { return data.get(0) > 0; }
    public int getProcessProgress() {
        int t = data.get(2);
        return t > 0 ? data.get(1) * 24 / t : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int machineSlots = CokeOvenEntity.INVENTORY_SIZE;
        int playerStart  = machineSlots;
        int playerEnd    = playerStart + 27;
        int hotbarEnd    = playerEnd + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index >= CokeOvenEntity.SLOT_OUTPUT_START && index <= CokeOvenEntity.SLOT_OUTPUT_END) {
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
        } else if (index >= playerStart) {
            if (isCoal(slotStack)) {
                if (!this.moveItemStackTo(slotStack, CokeOvenEntity.SLOT_INPUT_START,
                        CokeOvenEntity.SLOT_INPUT_END + 1, false)) {
                    if (index < playerEnd) {
                        if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false))
                            return ItemStack.EMPTY;
                    }
                }
            } else {
                if (index < playerEnd) {
                    if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false))
                        return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false))
                        return ItemStack.EMPTY;
                }
            }
        } else {
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, false)) return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity == null || entity.isRemoved()) return false;
        return player.distanceToSqr(entity) < 64.0;
    }

    private static boolean isCoal(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    // ── Custom slot types ─────────────────────────────────────────────────────

    private static class CoalOnlySlot extends Slot {
        CoalOnlySlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return isCoal(stack); }
        private static boolean isCoal(ItemStack s) { return s.is(Items.COAL) || s.is(Items.CHARCOAL); }
    }

    private static class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    private static class OutputOnlySlot extends Slot {
        OutputOnlySlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
