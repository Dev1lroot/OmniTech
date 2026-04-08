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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

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

        // Slots
        addSlot(new BucketInSlot(container, FluidTankBlockEntity.SLOT_BUCKET_IN,
                BUCKET_IN_X, BUCKET_IN_Y));
        addSlot(new OutputSlot(container, FluidTankBlockEntity.SLOT_BUCKET_OUT,
                BUCKET_OUT_X, BUCKET_OUT_Y));

        // Player inventory
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        // Player hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    // ── Fluid Accessor ────────────────────────────────────────────────────────
    // Этот метод позволит GuiUtil в классе Screen получить объект FluidStack
    // для извлечения названия, цвета и текстуры.

    public FluidStack getFluidStack() {
        if (container instanceof FluidTankBlockEntity be) {
            return be.getFluid();
        }
        return FluidStack.EMPTY;
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getStoredFluid()   { return data.get(0); }
    public int getMaxFluid()      { return data.get(1); }

    /** Fluid gauge fill height (0–52 px). */
    public int getFluidBarHeight() {
        int max = getMaxFluid();
        return max > 0 ? (int)((long)getStoredFluid() * 52 / max) : 0;
    }

    // ── Menu logic ─────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < 2) {
                // Из слотов бака в инвентарь игрока
                if (!moveItemStackTo(slotStack, 2, 38, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(slotStack, result);
            } else {
                // Из инвентаря игрока в бак (только ведра с жидкостью)
                if (slotStack.getItem() instanceof BucketItem b && b.getContent() != Fluids.EMPTY) {
                    if (!moveItemStackTo(slotStack, 0, 1, false)) {
                        // Если слот входа занят, перемещаем между инв/хотбаром
                        if (!moveBetweenPlayerInventories(index, slotStack)) return ItemStack.EMPTY;
                    }
                } else {
                    // Обычные предметы перемещаем между инв/хотбаром
                    if (!moveBetweenPlayerInventories(index, slotStack)) return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, slotStack);
        }
        return result;
    }

    private boolean moveBetweenPlayerInventories(int index, ItemStack stack) {
        if (index < 29) {
            return moveItemStackTo(stack, 29, 38, false);
        } else {
            return moveItemStackTo(stack, 2, 29, false);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.FLUID_TANK.get());
    }

    // ── Inner slot types ───────────────────────────────────────────────────────

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