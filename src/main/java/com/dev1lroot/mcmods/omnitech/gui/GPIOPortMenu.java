package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
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

public class GPIOPortMenu extends AbstractContainerMenu {

    private final BlockPos     pos;
    private final ContainerData data;

    /** Client-side constructor. */
    public GPIOPortMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv,
                inv.player.level().getBlockEntity(buf.readBlockPos()),
                makeData(buf.readInt(), buf.readInt(), buf.readInt()));
    }

    public GPIOPortMenu(int id, Inventory inv, BlockEntity be, ContainerData data) {
        super(OmniTechMenuTypes.GPIO_PORT.get(), id);
        this.pos  = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        addDataSlots(data);
    }

    private static ContainerData makeData(int portId, int inSig, int outSig) {
        SimpleContainerData d = new SimpleContainerData(3);
        d.set(0, portId); d.set(1, inSig); d.set(2, outSig);
        return d;
    }

    public int getPortId()       { return data.get(0); }
    public int getInputSignal()  { return data.get(1); }
    public int getOutputSignal() { return data.get(2); }
    public BlockPos getBlockPos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.GPIO_PORT.get());
    }
}
