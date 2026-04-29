package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadioScannerMenu extends AbstractContainerMenu {

    private final BlockEntity blockEntity;
    private final BlockPos pos;

    /** Client-side constructor. */
    public RadioScannerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** Server-side constructor. */
    public RadioScannerMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity) {
        super(OmniTechMenuTypes.RADIO_SCANNER.get(), containerId);
        this.blockEntity = blockEntity;
        this.pos         = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
    }

    public BlockPos getBlockPos() { return pos; }

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
