package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.FoundryBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.SmelterBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class SmelterMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    // 3×3 input grid top-left at (8, 17)
    public static final int GRID_START_X = 8;
    public static final int GRID_START_Y = 17;

    // Client constructor
    public SmelterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server constructor
    public SmelterMenu(int containerId, Inventory playerInventory,
                       BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.SMELTER.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // 9 input slots in 3×3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                addSlot(new Slot(container, slotIndex,
                        GRID_START_X + col * 18,
                        GRID_START_Y + row * 18));
            }
        }

        // Player inventory (slots 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 36–44)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public FluidStack getOutputFluid()
    {
        if (container instanceof SmelterBlockEntity be) {
            return be.getOutputFluid();
        }
        return FluidStack.EMPTY;
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getTemperature()         { return data.get(0); }
    public int getRequiredTemperature() { return data.get(1); }
    public int getCookProgress()        { return data.get(2); }
    public int getCookTotalTime()       { return data.get(3); }
    public int getFluidAmount()         { return data.get(4); }
    public int getFluidCapacity()       { return data.get(5); }

    public float getCookProgressScaled() {
        int total = getCookTotalTime();
        if (total <= 0) return 0f;
        // Возвращаем значение от 0.0 до 100.0
        return (float) getCookProgress() * 100f / total;
    }

    // ── Shift-click logic ──────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Machine input slots (0-8) → player inventory
        if (index < SmelterBlockEntity.SLOT_COUNT) {
            if (!this.moveItemStackTo(slotStack, SmelterBlockEntity.SLOT_COUNT, 45, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(slotStack, result);
        }
        // Player inventory/hotbar → machine input slots
        else if (index >= SmelterBlockEntity.SLOT_COUNT) {
            if (!this.moveItemStackTo(slotStack, 0, SmelterBlockEntity.SLOT_COUNT, false)) {
                int invStart = SmelterBlockEntity.SLOT_COUNT;
                int hotbarStart = invStart + 27;
                if (index < hotbarStart) {
                    if (!this.moveItemStackTo(slotStack, hotbarStart, 45, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, invStart, hotbarStart, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.SMELTER.get());
    }
}
