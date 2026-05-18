/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class RotaryCompressorMenu extends AbstractContainerMenu {

    private final RotaryCompressorBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor
    public RotaryCompressorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (RotaryCompressorBlockEntity) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server constructor
    public RotaryCompressorMenu(int containerId, Inventory playerInventory,
                                RotaryCompressorBlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ROTARY_COMPRESSOR.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("rotary_compressor");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Fluid accessors ────────────────────────────────────────────────────────

    public FluidStack getInputFluid() {
        return blockEntity != null ? blockEntity.getInputFluid() : FluidStack.EMPTY;
    }

    public FluidStack getOutputFluid() {
        return blockEntity != null ? blockEntity.getOutputFluid() : FluidStack.EMPTY;
    }

    // ── ContainerData accessors ────────────────────────────────────────────────

    public float getKineticForce()         { return data.get(0) / 100f; }
    public float getRequiredKineticForce() { return data.get(1) / 100f; }
    public int getInputFluidAmount()       { return data.get(2); }
    public int getInputFluidCapacity()     { return data.get(3); }
    public int getOutputFluidAmount()      { return data.get(4); }
    public int getOutputFluidCapacity()    { return data.get(5); }

    public float getKfProgressScaled() {
        float required = getRequiredKineticForce();
        if (required <= 0f) return 0f;
        return Math.min(100f, getKineticForce() / required * 100f);
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
                player, OmniTechBlocks.ROTARY_COMPRESSOR.get());
    }
}
