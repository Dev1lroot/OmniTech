/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;

public class ElectrolysisMachineMenu extends AbstractContainerMenu {

    private final Container    container;
    private final ContainerData data;
    /** Number of machine (non-player) slots — derived from the JSON layout. */
    private final int          machineSlotCount;

    // Client constructor
    public ElectrolysisMachineMenu(int containerId, Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(13));
    }

    // Server constructor
    public ElectrolysisMachineMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTROLYSIS_MACHINE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("electrolysis_machine");

        // ── Machine slots ────────────────────────────────────────────────────
        // Collect slot elements, order by slot_index so menu slot 0 = container
        // slot 0, menu slot 1 = container slot 1, etc.
        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(new Slot(container, el.slot_index, el.x, el.y));
        }
        this.machineSlotCount = machineSlots.size();

        // ── Player inventory + hotbar ─────────────────────────────────────────
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getInputFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getAnodeFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getAnodeFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getCathodeFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getCathodeFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getSolutionFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getSolutionFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    public float getEnergyStored()       { return data.get(0) / 10f; }
    public float getMaxEu()              { return data.get(1) / 10f; }
    public int   getCookProgress()       { return data.get(2); }
    public int   getCookTime()           { return data.get(3); }
    public float getEuPerRecipe()        { return data.get(4) / 10f; }

    public int getInputFluidAmount()     { return data.get(5); }
    public int getInputFluidCapacity()   { return data.get(6); }
    public int getAnodeFluidAmount()     { return data.get(7); }
    public int getAnodeFluidCapacity()   { return data.get(8); }
    public int getCathodeFluidAmount()   { return data.get(9); }
    public int getCathodeFluidCapacity() { return data.get(10); }
    public int getSolutionFluidAmount()  { return data.get(11); }
    public int getSolutionFluidCapacity(){ return data.get(12); }

    public float getCookProgressScaled() {
        int max = getCookTime();
        return max > 0 ? getCookProgress() * 100f / max : 0f;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;   // 3 rows × 9 = 27
        int hotbarEnd   = playerEnd + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index < machineSlotCount) {
            // Machine slot → player inventory
            if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index < playerEnd) {
            // Player inventory → machine slots, or hotbar
            if (!moveItemStackTo(slotStack, 0, machineSlotCount, false))
                if (!moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else {
            // Hotbar → machine slots, or player inventory
            if (!moveItemStackTo(slotStack, 0, machineSlotCount, false))
                if (!moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.ELECTROLYSIS_MACHINE.get());
    }
}
