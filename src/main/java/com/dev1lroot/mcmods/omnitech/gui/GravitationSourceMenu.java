/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
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
 * Menu for the Gravitation Source.
 *
 * <h3>ContainerData layout (1 entry)</h3>
 * <ul>
 *   <li>0 – radius in blocks</li>
 * </ul>
 *
 * <h3>Button IDs</h3>
 * Forwarded to {@link GravitationSourceBlockEntity#adjustRadius(int)}:
 * 0 = −5, 1 = −1, 2 = +1, 3 = +5.
 */
public class GravitationSourceMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;

    /** Client-side constructor. */
    public GravitationSourceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(1));
    }

    /** Server-side constructor. */
    public GravitationSourceMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.GRAVITATION_SOURCE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("gravitation_source");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public int getRadius() { return data.get(0); }

    // ── Button handling ───────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity instanceof GravitationSourceBlockEntity grav) {
            return grav.adjustRadius(id);
        }
        return false;
    }

    // ── Container contract ────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.GRAVITATION_SOURCE.get());
    }
}
