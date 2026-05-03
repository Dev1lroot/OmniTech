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

public class DisplayMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final int portId;

    /** Client-side constructor (called by MenuType). */
    public DisplayMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readInt());
    }

    /** Shared constructor (also called server-side from DisplayBlockEntity.createMenu). */
    public DisplayMenu(int id, Inventory inv, BlockPos pos, int portId) {
        super(OmniTechMenuTypes.DISPLAY.get(), id);
        this.pos    = pos;
        this.portId = portId;
    }

    public BlockPos getBlockPos() { return pos; }
    public int getPortId()        { return portId; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.DISPLAY.get());
    }
}
