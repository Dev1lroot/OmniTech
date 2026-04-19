package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
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
    private final int machineSlotCount;

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

        GuiLayout layout = GuiLayoutLoader.load("rocket");
        int slotCount = 0;

        if (rocket != null) {
            GuiElementDef fuelIn   = layout.getElementById("fuel_in").orElse(null);
            GuiElementDef bucketOut = layout.getElementById("bucket_out").orElse(null);

            if (fuelIn != null) {
                addSlot(new Slot(rocket.getInventory(), 0, fuelIn.x, fuelIn.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return stack.is(Items.WATER_BUCKET); }
                });
                slotCount++;
            }
            if (bucketOut != null) {
                addSlot(new Slot(rocket.getInventory(), 1, bucketOut.x, bucketOut.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return false; }
                });
                slotCount++;
            }
        }
        this.machineSlotCount = slotCount;

        layout.addPlayerInventory(playerInventory, this::addSlot);
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

        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        if (index < machineSlotCount) {
            // Machine slots -> player inventory
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player inventory -> machine input slot
            if (machineSlotCount > 0 && !this.moveItemStackTo(slotStack, 0, 1, false)) {
                if (index < playerEnd) {
                    if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
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
