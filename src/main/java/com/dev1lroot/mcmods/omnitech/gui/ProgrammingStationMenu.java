package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station.ProgrammingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
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
    private final String initialProgram;

    /** Client-side constructor. */
    public ProgrammingStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv,
                inv.player.level().getBlockEntity(buf.readBlockPos()),
                buf.readUtf(ProgrammingStationBlockEntity.MAX_PROGRAM_LEN));
    }

    /** Used internally when screen supplies typed data directly. */
    public ProgrammingStationMenu(int id, Inventory inv, BlockEntity be, String program) {
        super(OmniTechMenuTypes.PROGRAMMING_STATION.get(), id);
        this.pos            = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.initialProgram = program;

        // Machine slot (Microcontroller only)
        if (be instanceof ProgrammingStationBlockEntity ps) {
            addSlot(new Slot(ps, 0, 80, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof MicrocontrollerItem;
                }
            });
        }

        // Player inventory
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 166 + row * 18));
        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 224));
    }

    public BlockPos getBlockPos()      { return pos; }
    public String   getInitialProgram() { return initialProgram; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack orig  = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof MicrocontrollerItem) {
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
