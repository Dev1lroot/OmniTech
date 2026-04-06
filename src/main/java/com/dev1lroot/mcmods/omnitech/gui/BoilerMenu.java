package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.BoilerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BoilerMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** Output slot position in GUI (screen-relative). */
    public static final int OUTPUT_SLOT_X = 110;
    public static final int OUTPUT_SLOT_Y = 50;

    // ── Client constructor ────────────────────────────────────────────────────

    public BoilerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // ── Server constructor ────────────────────────────────────────────────────

    public BoilerMenu(int containerId, Inventory playerInventory,
                      BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.BOILER.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Output slot — players cannot insert items manually
        addSlot(new OutputSlot(container, BoilerBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));

        // Player inventory (slots 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 36–44)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public int getTemperature()           { return data.get(0); }
    public int getRequiredTemperature()   { return data.get(1); }
    public int getCookProgress()          { return data.get(2); }
    public int getCookTotalTime()         { return data.get(3); }
    public int getWaterAmount()           { return data.get(4); }
    public int getSteamAmount()           { return data.get(5); }
    public int getCapacity()              { return BoilerBlockEntity.MAX_FLUID; }

    public float getCookProgressScaled() {
        int total = getCookTotalTime();
        if (total <= 0) return 0f;
        return (float) getCookProgress() * 100f / total;
    }

    public net.neoforged.neoforge.fluids.FluidStack getWaterFluid() {
        if (container instanceof BoilerBlockEntity be) return be.getWaterTank();
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    public net.neoforged.neoforge.fluids.FluidStack getSteamFluid() {
        if (container instanceof BoilerBlockEntity be) return be.getSteamTank();
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Slot 0 is the machine output — move to player inventory
        if (index == BoilerBlockEntity.SLOT_OUTPUT) {
            if (!this.moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        }
        // Player inventory/hotbar — nowhere to go (no input slots for items)
        else if (index >= 1 && index < 10) {
            if (!this.moveItemStackTo(slotStack, 10, 37, false)) return ItemStack.EMPTY;
        } else if (index >= 10) {
            if (!this.moveItemStackTo(slotStack, 1, 10, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.BOILER.get());
    }

    // ── Inner: output-only slot ───────────────────────────────────────────────

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
