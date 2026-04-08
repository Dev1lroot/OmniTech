package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.HeatExchangerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class HeatExchangerMenu extends AbstractContainerMenu {

    private final HeatExchangerBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor
    public HeatExchangerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (HeatExchangerBlockEntity) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(7));
    }

    // Server constructor
    public HeatExchangerMenu(int containerId, Inventory playerInventory,
                             BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.HEAT_EXCHANGER.get(), containerId);
        this.blockEntity = (HeatExchangerBlockEntity) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Player inventory (rows 0–2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid()  { return blockEntity != null ? blockEntity.getInputFluid()  : FluidStack.EMPTY; }
    public FluidStack getOutputFluid() { return blockEntity != null ? blockEntity.getOutputFluid() : FluidStack.EMPTY; }

    // ── ContainerData accessors ────────────────────────────────────────────────
    // 0=storedHeat  1=maxHeat  2=processTimer
    // 3=inFluidAmt  4=inFluidCap  5=outFluidAmt  6=outFluidCap

    public int getStoredHeat()         { return data.get(0); }
    public int getMaxHeat()            { return data.get(1); }
    public int getProcessTimer()       { return data.get(2); }
    public int getInputFluidAmount()   { return data.get(3); }
    public int getInputFluidCapacity() { return data.get(4); }
    public int getOutputFluidAmount()  { return data.get(5); }
    public int getOutputFluidCapacity(){ return data.get(6); }

    /**
     * Stored heat as a value in [0, 100] relative to recipe's maxHeat.
     * Returns 0 when no recipe is active.
     */
    public float getHeatScaled() {
        int max = getMaxHeat();
        if (max <= 0) return 0f;
        return Math.min(100f, getStoredHeat() / (float) max * 100f);
    }

    /**
     * Processing progress as a value in [0, 100].
     */
    public float getProcessProgressScaled() {
        return Math.min(100f, getProcessTimer() / (float) HeatExchangerBlockEntity.PROCESS_TIME * 100f);
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Slots 0–26 = inventory, 27–35 = hotbar
        if (index < 27) {
            if (!this.moveItemStackTo(slotStack, 27, 36, false)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(slotStack, 0, 27, false)) return ItemStack.EMPTY;
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
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.HEAT_EXCHANGER.get());
    }
}
