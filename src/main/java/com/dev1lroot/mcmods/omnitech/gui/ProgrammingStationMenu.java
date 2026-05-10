package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station.ProgrammingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.RomItem;
import com.dev1lroot.mcmods.omnitech.network.FlashRomPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ProgrammingStationMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final byte[] initialRomData;

    /** Client-side constructor. */
    public ProgrammingStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv,
                inv.player.level().getBlockEntity(buf.readBlockPos()),
                buf.readByteArray(FlashRomPacket.MAX_SIZE));
    }

    public ProgrammingStationMenu(int id, Inventory inv, BlockEntity be, byte[] romData) {
        super(OmniTechMenuTypes.PROGRAMMING_STATION.get(), id);
        this.pos           = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.initialRomData = romData;

        // ROM slot — only accepts writable Firmware ROM items, not read-only Linux ROM
        if (be instanceof ProgrammingStationBlockEntity ps) {
            addSlot(new Slot(ps, 0, 152, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof RomItem r
                            && RomItem.TYPE_FIRMWARE.equals(r.getRomType());
                }
            });
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 210));
    }

    public BlockPos getBlockPos()      { return pos; }
    public byte[]   getInitialRomData() { return initialRomData; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack orig  = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof RomItem r && RomItem.TYPE_FIRMWARE.equals(r.getRomType())) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
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
                player.level(), pos), player, OmniTechBlocks.PROGRAMMING_STATION.get());
    }
}
