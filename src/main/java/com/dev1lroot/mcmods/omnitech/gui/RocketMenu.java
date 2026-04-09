package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RocketMenu extends AbstractContainerMenu {

    private final RocketEntity rocket;

    // Slot positions (GUI-relative)
    public static final int INPUT_SLOT_X  = 62;
    public static final int INPUT_SLOT_Y  = 35;
    public static final int OUTPUT_SLOT_X = 98;
    public static final int OUTPUT_SLOT_Y = 35;

    // Client constructor — reads entity ID from the extra data buffer
    public RocketMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                findRocket(playerInventory, extraData.readInt()),
                new SimpleContainerData(2));
    }

    private static RocketEntity findRocket(Inventory playerInventory, int entityId) {
        Entity entity = playerInventory.player.level().getEntity(entityId);
        return entity instanceof RocketEntity r ? r : null;
    }

    // Server constructor
    public RocketMenu(int containerId, Inventory playerInventory, RocketEntity rocket, ContainerData data) {
        super(OmniTechMenuTypes.ROCKET.get(), containerId);
        this.rocket = rocket;

        addDataSlots(data);

        if (rocket != null) {
            // Slot 0: fuel input — accepts only water buckets
            addSlot(new Slot(rocket.getInventory(), 0, INPUT_SLOT_X, INPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.WATER_BUCKET);
                }
            });

            // Slot 1: output — extract-only (empty bucket comes out here)
            addSlot(new Slot(rocket.getInventory(), 1, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory (slots 2–28)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 29–37)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public int getFuelAmount() {
        return rocket != null ? rocket.getFuelAmount() : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int machineSlots = rocket != null ? 2 : 0;

        if (index < machineSlots) {
            // Machine slots -> player inventory
            if (!this.moveItemStackTo(slotStack, machineSlots, machineSlots + 36, true))
                return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player inventory -> machine input slot
            if (machineSlots > 0 && !this.moveItemStackTo(slotStack, 0, 1, false)) {
                int invStart = machineSlots;
                int hotbarStart = invStart + 27;
                if (index < hotbarStart) {
                    if (!this.moveItemStackTo(slotStack, hotbarStart, machineSlots + 36, false))
                        return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, invStart, hotbarStart, false))
                        return ItemStack.EMPTY;
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
        return rocket != null && rocket.isAlive() && player.distanceToSqr(rocket) < 64.0;
    }
}
