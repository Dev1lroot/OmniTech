package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.KineticGeneratorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class KineticGeneratorMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    // Fuel slot centered in the upper portion of the 176×166 GUI
    private static final int FUEL_X = 80, FUEL_Y = 35;

    // Client-side constructor — reads block pos from packet, fetches BE from level
    public KineticGeneratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server-side constructor — used directly from the block entity
    public KineticGeneratorMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.KF_GENERATOR.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);
        addSlot(new FuelSlot(container, KineticGeneratorBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    /** Current burn time remaining, in ticks. */
    public int getBurnTime()    { return data.get(0); }
    /** Max burn time of the last consumed fuel item, in ticks. */
    public int getMaxBurnTime() { return data.get(1); }

    /**
     * Burn progress scaled to 14px (matches a vanilla flame icon height).
     * Returns 0 when not burning.
     */
    public int getFlameHeight() {
        int max = getMaxBurnTime();
        return max != 0 ? getBurnTime() * 14 / max : 0;
    }

    // ── Menu logic ─────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Fuel slot (0) → player inventory
        if (index == 0) {
            if (!moveItemStackTo(slotStack, 1, 37, false)) return ItemStack.EMPTY;
        }
        // Player inventory / hotbar (1–36) → fuel slot
        else {
            if (!moveItemStackTo(slotStack, 0, 1, false)) {
                if (index < 28) {
                    if (!moveItemStackTo(slotStack, 28, 37, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(slotStack, 1, 28, false)) return ItemStack.EMPTY;
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
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.KF_GENERATOR.get());
    }

    // ── Layout helpers ─────────────────────────────────────────────────────────

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    // ── Inner slot types ───────────────────────────────────────────────────────

    /** Slot that only accepts items with a positive fuel burn duration. */
    private static class FuelSlot extends Slot {
        public FuelSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return KineticGeneratorBlockEntity.getBurnDuration(stack) > 0;
        }
    }
}
