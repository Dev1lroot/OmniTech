/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_transformer.PowerTransformerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
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
 * Menu for the Power Transformer — no item slots; settings are changed via
 * menu buttons (ids in {@link PowerTransformerBlockEntity}). See the block
 * entity for the ContainerData layout.
 */
public class PowerTransformerMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Client-side constructor. */
    public PowerTransformerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(10));
    }

    /** Server-side constructor. */
    public PowerTransformerMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.POWER_TRANSFORMER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("power_transformer");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /** Output voltage setpoint in V. */
    public int     getOutputVoltageSetpoint() { return data.get(0); }
    /** Power limit in kW. */
    public int     getPowerLimitKw()          { return data.get(1); }
    public boolean isEnabled()                { return data.get(2) == 1; }

    // ── Readings ──────────────────────────────────────────────────────────────

    public float getInputWatts()   { return data.get(3); }
    public float getInputVoltage() { return PowerMeter.decodeVolts(data.get(4)); }
    public float getOutputWatts()  { return data.get(5); }
    public float getOutputVoltage(){ return PowerMeter.decodeVolts(data.get(6)); }
    public float getLossWatts()    { return data.get(7); }
    public float getBuffer()       { return data.get(8) / 10f; }
    public float getBufferMax()    { return data.get(9) / 10f; }
    public float getLimitWatts()   { return getPowerLimitKw() * 1000f; }

    /** Turns ratio N_primary : N_secondary = U_in : U_out (0 if there is no input). */
    public float getTurnsRatio() {
        float in = getInputVoltage();
        return in > 0f ? in / getOutputVoltageSetpoint() : 0f;
    }

    /** Measured efficiency P_out / (P_out + losses), or 0 when idle. */
    public float getEfficiency() {
        float out = getOutputWatts();
        return out > 0f ? out / (out + getLossWatts()) : 0f;
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return blockEntity instanceof PowerTransformerBlockEntity transformer
                && transformer.handleButton(id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.POWER_TRANSFORMER.get());
    }
}
