package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.FluidTankBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

public class FluidTankMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    private static final int BUCKET_IN_X  = 27, BUCKET_IN_Y  = 17;
    private static final int BUCKET_OUT_X = 27, BUCKET_OUT_Y = 53;

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
        addSlot(new BucketInSlot(container, FluidTankBlockEntity.SLOT_BUCKET_IN,
                BUCKET_IN_X, BUCKET_IN_Y));
        addSlot(new OutputSlot(container, FluidTankBlockEntity.SLOT_BUCKET_OUT,
                BUCKET_OUT_X, BUCKET_OUT_Y));
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getStoredFluid()   { return data.get(0); }
    public int getMaxFluid()      { return data.get(1); }

    /** Fluid gauge fill height (0–52 px). */
    public int getFluidBarHeight() {
        int max = getMaxFluid();
        return max != 0 ? getStoredFluid() * 52 / max : 0;
    }

    // ── Menu logic ─────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Machine slots (0-1) → player inventory
        if (index < 2) {
            if (!moveItemStackTo(slotStack, 2, 38, false)) return ItemStack.EMPTY;
        }
        // Player inventory (2-37) → machine input slot if it's a filled bucket
        else {
            if (slotStack.getItem() instanceof BucketItem b && b.getContent() != Fluids.EMPTY) {
                if (!moveItemStackTo(slotStack, 0, 1, false)) {
                    if (index < 29) {
                        if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
                    } else {
                        if (!moveItemStackTo(slotStack, 2, 29, false)) return ItemStack.EMPTY;
                    }
                }
            } else {
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
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.FLUID_TANK.get());
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

    /** Accepts any filled fluid bucket. */
    private static class BucketInSlot extends Slot {
        public BucketInSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.getItem() instanceof BucketItem b) {
                return b.getContent() != Fluids.EMPTY;
            }
            return false;
        }
    }

    /** Output-only slot — players cannot manually insert items. */
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
