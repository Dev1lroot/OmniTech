package com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.ExpansionSlotMenu;
import com.dev1lroot.mcmods.omnitech.items.RamCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = super.removeItem(slot, amount);
        clearAddressComponents(result);
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = super.removeItemNoUpdate(slot);
        clearAddressComponents(result);
        return result;
    }

    private static void clearAddressComponents(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof RamCardItem) {
            stack.remove(OmniTechDataComponents.RAM_ADDR_START.get());
            stack.remove(OmniTechDataComponents.RAM_ADDR_END.get());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
    }
}
