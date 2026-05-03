package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
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

public class LogicMachineMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    /** Client-side constructor. */
    public LogicMachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv,
                inv.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(5));
    }

    public LogicMachineMenu(int id, Inventory inv, BlockEntity be, ContainerData data) {
        super(OmniTechMenuTypes.LOGIC_MACHINE.get(), id);
        this.pos  = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.data = data;
        addDataSlots(data);

        if (be instanceof LogicMachineBlockEntity lm) {
            addSlot(new Slot(lm, 0, 116, 4) {
                @Override public boolean mayPlace(ItemStack s) {
                    return s.getItem() instanceof MicrocontrollerItem;
                }
            });
        }

        // Player inventory (moved down for debug tab space)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 138 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 196));
    }

    public boolean isRunning()          { return data.get(0) == 1; }
    public boolean isHalted()           { return data.get(1) == 1; }
    public int     getCurrentLine()     { return data.get(2); }
    public boolean hasMicrocontroller() { return data.get(3) == 1; }
    public boolean hasError()           { return data.get(4) == 1; }
    public BlockPos getBlockPos()       { return pos; }

    /** Returns the program text stored on the Microcontroller in the slot, or "". */
    public String getProgram() {
        ItemStack mc = getSlot(0).getItem();
        if (mc.isEmpty() || !(mc.getItem() instanceof MicrocontrollerItem)) return "";
        return mc.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof LogicMachineBlockEntity lm)
            return lm.handleButton(id);
        return false;
    }

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
                player.level(), pos), player, OmniTechBlocks.LOGIC_MACHINE.get());
    }
}
