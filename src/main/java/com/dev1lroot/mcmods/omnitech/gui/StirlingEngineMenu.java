package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class StirlingEngineMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final ContainerLevelAccess access;

    // Клиентский конструктор
    public StirlingEngineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    // Серверный конструктор
    public StirlingEngineMenu(int containerId, Inventory playerInventory,
                              @Nullable BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.STIRLING_ENGINE.get(), containerId);
        this.data = data;
        this.access = ContainerLevelAccess.create(
                blockEntity != null ? blockEntity.getLevel() : null,
                blockEntity != null ? blockEntity.getBlockPos() : null
        );

        addDataSlots(data);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public int getStoredSteam() { return data.get(0); }
    public int getMaxSteam()    { return data.get(1); }
    public int getStoredWater() { return data.get(2); }
    public int getMaxWater()    { return data.get(3); }

    public boolean isRunning() {
        return getStoredSteam() >= 10 && getStoredWater() < getMaxWater();
    }

    public int getSteamBarHeight() {
        int max = getMaxSteam();
        return max != 0 ? getStoredSteam() * 52 / max : 0;
    }

    public int getWaterBarHeight() {
        int max = getMaxWater();
        return max != 0 ? getStoredWater() * 52 / max : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 36) {
                if (!moveItemStackTo(stack, 36, 36, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, OmniTechBlocks.STIRLING_ENGINE.get());
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }
}