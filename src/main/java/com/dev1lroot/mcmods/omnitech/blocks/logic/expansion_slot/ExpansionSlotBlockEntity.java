package com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.ExpansionSlotMenu;
import com.dev1lroot.mcmods.omnitech.items.RamCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExpansionSlotBlockEntity extends BaseContainerBlockEntity {

    public static final int SLOT_COUNT = 8;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public ExpansionSlotBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.EXPANSION_SLOT.get(), pos, state);
    }

    public int getTotalCapacity() {
        int total = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof RamCardItem) {
                total += stack.getOrDefault(OmniTechDataComponents.RAM_CAPACITY.get(), 1024);
            }
        }
        return total;
    }

    public int getRamCardCount() {
        int count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof RamCardItem) count++;
        }
        return count;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.expansion_slot");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new ExpansionSlotMenu(id, inv, this);
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof RamCardItem;
    }
}
