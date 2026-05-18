/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver.RadioReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadioReceiverMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client-side constructor — ContainerData is synced from server. */
    public RadioReceiverMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(3));
    }

    /** Server-side constructor. */
    public RadioReceiverMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.RADIO_RECEIVER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data        = data;
        this.pos         = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public FrequencyBand getBand() {
        int ord = data.get(0);
        FrequencyBand[] vals = FrequencyBand.values();
        return (ord >= 0 && ord < vals.length) ? vals[ord] : FrequencyBand.VHF;
    }

    public int   getChannelIndex()   { return data.get(1); }
    public float getCurrentSignal()  { return data.get(2) / 100f; }
    public BlockPos getBlockPos()    { return pos; }

    public String getFreqDisplay() { return getBand().freqDisplay(getChannelIndex()); }

    public float getFreqValue() {
        FrequencyBand b = getBand();
        return b.freqMin() + getChannelIndex() * b.freqStep();
    }

    // ── Button handling ───────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity instanceof RadioReceiverBlockEntity rx) {
            return rx.handleButton(id);
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
                player, OmniTechBlocks.RADIO_RECEIVER.get());
    }
}
