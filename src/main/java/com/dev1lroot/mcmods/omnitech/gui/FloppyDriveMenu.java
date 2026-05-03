package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FloppyDriveMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public FloppyDriveMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv,
                inv.player.level().getBlockEntity(buf.readBlockPos()),
                makeData(buf.readInt()));
    }

    public FloppyDriveMenu(int id, Inventory inv, BlockEntity be, ContainerData data) {
        super(OmniTechMenuTypes.FLOPPY_DRIVE.get(), id);
        this.pos  = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        addDataSlots(data);

        if (be instanceof FloppyDriveBlockEntity fd) {
            addSlot(new Slot(fd, 0, 80, 35) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return s.getItem() instanceof FloppyDiskItem;
                }
            });
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
    }

    private static ContainerData makeData(int driveId) {
        SimpleContainerData d = new SimpleContainerData(1);
        d.set(0, driveId);
        return d;
    }

    public int getDriveId()       { return data.get(0); }
    public BlockPos getBlockPos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack orig  = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof FloppyDiskItem) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else return ItemStack.EMPTY;
        }
        slot.set(stack);
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        if (stack.getCount() == orig.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return orig;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.FLOPPY_DRIVE.get());
    }
}
