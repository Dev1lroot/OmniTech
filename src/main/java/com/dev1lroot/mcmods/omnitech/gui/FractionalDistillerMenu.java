/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Menu for the Fractional Distiller multiblock GUI.
 *
 * <p>Always bound to the bottom (master) block entity.  Fluid data for each
 * output segment is read directly from the block entity hierarchy via
 * {@link FractionalDistillerBlockEntity#getStructureOutputFluid(int)}.
 *
 * <h3>ContainerData layout (6 indices)</h3>
 * <pre>
 *   0  temperature×10 (0.1 °C resolution)
 *   1  requiredTemperature
 *   2  processTimer
 *   3  processTotalTime
 *   4  inputFluidAmount
 *   5  structureHeight
 * </pre>
 */
public class FractionalDistillerMenu extends AbstractContainerMenu {

    private final FractionalDistillerBlockEntity blockEntity;
    private final ContainerData data;

    // ── Client constructor (reads master block pos from network buffer) ────────

    public FractionalDistillerMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv,
                (FractionalDistillerBlockEntity) inv.player.level()
                        .getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(6));
    }

    // ── Server constructor ────────────────────────────────────────────────────

    public FractionalDistillerMenu(int containerId, Inventory inv,
                                   FractionalDistillerBlockEntity masterBE,
                                   ContainerData data) {
        super(OmniTechMenuTypes.FRACTIONAL_DISTILLER.get(), containerId);
        this.blockEntity = masterBE;
        this.data = data;

        addDataSlots(data);

        // Player inventory (rows 0–2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid() {
        return blockEntity != null ? blockEntity.getInputFluid() : FluidStack.EMPTY;
    }

    /**
     * Returns the output fluid of the i-th segment in the structure
     * (0 = bottom block, 1 = second from bottom, …).
     */
    public FluidStack getOutputFluid(int i) {
        return blockEntity != null ? blockEntity.getStructureOutputFluid(i) : FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    /** Current machine temperature in °C (0.1 °C resolution). */
    public float getTemperature()    { return data.get(0) / 10f; }
    public int   getRequiredTemp()   { return data.get(1); }
    public int getProcessTimer()     { return data.get(2); }
    public int getProcessTotalTime() { return data.get(3); }
    public int getInputFluidAmount() { return data.get(4); }
    public int getStructureHeight()  { return data.get(5); }

    public float getProcessProgressScaled() {
        int total = getProcessTotalTime();
        if (total <= 0) return 0f;
        return Math.min(100f, getProcessTimer() / (float) total * 100f);
    }

    // ── Validity ──────────────────────────────────────────────────────────────

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) return false;
        return stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.FRACTIONAL_DISTILLER.get());
    }

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
}
