/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater.ElectricHeaterBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu for the Electric Heater — display-only, no item slots.
 *
 * <h3>ContainerData layout (4 entries)</h3>
 * <ul>
 *   <li>0 – energyStored × 10</li>
 *   <li>1 – currentTemp × 10</li>
 *   <li>2 – targetTemp (integer °C)</li>
 *   <li>3 – MAX_EU × 10 (constant)</li>
 * </ul>
 *
 * <h3>Button IDs</h3>
 * Forwarded to {@link ElectricHeaterBlockEntity#adjustTargetTemp(int)}:
 * 0 = −1°C, 1 = +1°C, 2 = −10°C, 3 = +10°C, 4 = −100°C, 5 = +100°C.
 */
public class ElectricHeaterMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;

    /** Client-side constructor. */
    public ElectricHeaterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(4));
    }

    /** Server-side constructor. */
    public ElectricHeaterMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_HEATER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("electric_heater");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public float getEnergyStored() { return data.get(0) / 10f; }
    public float getCurrentTemp()  { return data.get(1) / 10f; }
    public int   getTargetTemp()   { return data.get(2); }
    public float getMaxEu()        { return data.get(3) / 10f; }

    public float getEuPerTick() {
        return ElectricHeaterBlockEntity.computeEuPerTick(getTargetTemp());
    }

    // ── Button handling ───────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity instanceof ElectricHeaterBlockEntity heater) {
            return heater.adjustTargetTemp(id);
        }
        return false;
    }

    // ── Container contract ────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_HEATER.get());
    }
}
