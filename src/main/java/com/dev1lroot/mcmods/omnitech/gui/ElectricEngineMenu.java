package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
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

/**
 * Menu for the Electric Engine — display-only, no item slots.
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – powered (1) / idle (0)</li>
 *   <li>1 – KF received × 100 (centi-KF units, so 10 = 0.10 KF)</li>
 *   <li>2 – EU/tick output</li>
 * </ul>
 */
public class ElectricEngineMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Client-side constructor. */
    public ElectricEngineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(3));
    }

    /** Server-side constructor. */
    public ElectricEngineMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_ENGINE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public boolean isPowered()    { return data.get(0) == 1; }
    /** KF received (fixed-point, divide by 100 for display). */
    public int getKfCenti()       { return data.get(1); }
    /** EU/tick output (decoded from fixed-point ×10). */
    public float getEuPerTick()   { return data.get(2) / 10f; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_ENGINE.get());
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
