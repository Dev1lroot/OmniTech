/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_scanner.RadioScannerBlockEntity;
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

public class RadioScannerMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client-side constructor — ContainerData is synced from server. */
    public RadioScannerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(1));
    }

    /** Server-side constructor. */
    public RadioScannerMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.RADIO_SCANNER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data        = data;
        this.pos         = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);
    }

    public BlockPos getBlockPos() { return pos; }

    public FrequencyBand getActiveBand() {
        int ord = data.get(0);
        FrequencyBand[] vals = FrequencyBand.values();
        return (ord >= 0 && ord < vals.length) ? vals[ord] : FrequencyBand.VHF;
    }

    /** Called when the player clicks a band tab (id = band ordinal). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity instanceof RadioScannerBlockEntity scanner) {
            scanner.setActiveBand(id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.RADIO_SCANNER.get());
    }
}
