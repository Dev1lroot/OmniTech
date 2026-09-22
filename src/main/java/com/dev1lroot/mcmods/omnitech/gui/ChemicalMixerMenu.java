/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ChemicalMixerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class ChemicalMixerMenu extends AbstractContainerMenu {

    private final ChemicalMixerBlockEntity blockEntity;
    private final ContainerData data;

    // Client constructor
    public ChemicalMixerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (ChemicalMixerBlockEntity) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(8));
    }

    // Server constructor
    public ChemicalMixerMenu(int containerId, Inventory playerInventory,
                             BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.CHEMICAL_MIXER.get(), containerId);
        this.blockEntity = (ChemicalMixerBlockEntity) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("chemical_mixer");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getFluidA() { return blockEntity != null ? blockEntity.getFluidA() : FluidStack.EMPTY; }
    public FluidStack getFluidB() { return blockEntity != null ? blockEntity.getFluidB() : FluidStack.EMPTY; }
    public FluidStack getOutput() { return blockEntity != null ? blockEntity.getOutput() : FluidStack.EMPTY; }

    // ── ContainerData accessors ────────────────────────────────────────────────
    // [0]=ratioA [1]=ratioB  [2]=A amt [3]=A cap  [4]=B amt [5]=B cap  [6]=out amt [7]=out cap

    public int getRatioA()        { return data.get(0); }
    public int getRatioB()        { return data.get(1); }
    public int getFluidAAmount()  { return data.get(2); }
    public int getFluidACapacity(){ return data.get(3); }
    public int getFluidBAmount()  { return data.get(4); }
    public int getFluidBCapacity(){ return data.get(5); }
    public int getOutputAmount()  { return data.get(6); }
    public int getOutputCapacity(){ return data.get(7); }

    // ── Ratio buttons ─────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return blockEntity != null && blockEntity.adjustRatio(id);
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
                player, OmniTechBlocks.CHEMICAL_MIXER.get());
    }
}
