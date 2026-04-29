package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter.RadioTransmitterBlockEntity;
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

public class RadioTransmitterMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client-side constructor. */
    public RadioTransmitterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(2));
    }

    /** Server-side constructor. */
    public RadioTransmitterMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.RADIO_TRANSMITTER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data        = data;
        this.pos         = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
        addDataSlots(data);
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public int   getFrequencyX10()  { return data.get(0); }
    public float getCurrentSignal() { return data.get(1) / 100f; }
    public BlockPos getBlockPos()   { return pos; }

    public float getFrequencyMHz()  { return getFrequencyX10() / 10f; }

    // ── Button handling ───────────────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity instanceof RadioTransmitterBlockEntity tx) {
            return tx.adjustFrequency(id);
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
                player, OmniTechBlocks.RADIO_TRANSMITTER.get());
    }
}
