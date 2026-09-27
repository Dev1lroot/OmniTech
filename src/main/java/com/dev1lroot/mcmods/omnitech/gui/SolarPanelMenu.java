/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
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
 * Menu for the Solar Panel — no item slots, just two data values:
 * <ul>
 *   <li>0 – currentOutput × 1000 (fixed-point, decode ÷ 1000)</li>
 *   <li>1 – sky light level above the panel (0–15)</li>
 *   <li>2 – power delivered to the network in W</li>
 *   <li>3 – terminal voltage × 10</li>
 * </ul>
 */
public class SolarPanelMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    public static final int BAR_MAX_W = 100;

    /** Client-side constructor — reads block pos from packet. */
    public SolarPanelMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    /** Server-side constructor. */
    public SolarPanelMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.SOLAR_PANEL.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("solar_panel");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    /** Currently generated kJ/tick (decoded from fixed-point ×1000). */
    public float getCurrentOutput() { return data.get(0) / 1000f; }

    /** Currently generated power in W. */
    public float getGeneratedWatts() { return (float) ElectricUnits.toWatts(getCurrentOutput()); }

    /** Power actually accepted by the network in W (1 s average). */
    public float getDeliveredWatts() { return data.get(2); }

    /** Panel terminal voltage in V. */
    public float getVoltage() { return PowerMeter.decodeVolts(data.get(3)); }

    /** Sky light level directly above the panel (0–15). */
    public int getSkyLight() { return data.get(1); }

    public int getSkyLightBarWidth() { return getSkyLight() * BAR_MAX_W / 15; }
    public int getOutputBarWidth()   { return (int)(getCurrentOutput() * BAR_MAX_W); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.SOLAR_PANEL.get());
    }
}
