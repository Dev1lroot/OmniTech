/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class DecompressorMenu extends AbstractContainerMenu {

    private final DecompressorBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor
    public DecompressorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (DecompressorBlockEntity) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(8));
    }

    // Server constructor
    public DecompressorMenu(int containerId, Inventory playerInventory,
                            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.DECOMPRESSOR.get(), containerId);
        this.blockEntity = (DecompressorBlockEntity) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("decompressor");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid()  { return blockEntity != null ? blockEntity.getInputFluid()  : FluidStack.EMPTY; }
    public FluidStack getOutputFluid() { return blockEntity != null ? blockEntity.getOutputFluid() : FluidStack.EMPTY; }

    // ── ContainerData accessors ────────────────────────────────────────────────
    // [0]=inAmt  [1]=inCap  [2]=outAmt  [3]=outCap  [4]=targetPressure
    // [5]=processCooldown  [6]=kfCurrent×100  [7]=kfRequired×100

    public int getInputFluidAmount()    { return data.get(0); }
    public int getInputFluidCapacity()  { return data.get(1); }
    public int getOutputFluidAmount()   { return data.get(2); }
    public int getOutputFluidCapacity() { return data.get(3); }
    public int getTargetPressure()      { return data.get(4); }
    public int getProcessCooldown()     { return data.get(5); }
    public float getKineticForce()         { return data.get(6) / 100f; }
    public float getKineticForceRequired() { return data.get(7) / 100f; }
    public float getKfProgressScaled() {
        float req = getKineticForceRequired();
        return req > 0f ? Math.min(100f, getKineticForce() / req * 100f) : 0f;
    }

    public float getProcessProgressScaled() {
        int cooldown = getProcessCooldown();
        return cooldown > 0 ? (cooldown / (float) DecompressorBlockEntity.MIN_CYCLE_TICKS) * 100f : 0f;
    }

    public BlockPos getBlockPos() {
        return blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

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
                ContainerLevelAccess.create(
                        blockEntity != null ? blockEntity.getLevel() : null,
                        blockEntity != null ? blockEntity.getBlockPos() : null),
                player, OmniTechBlocks.DECOMPRESSOR.get());
    }
}
