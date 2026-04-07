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
 * Menu for the Solar Panel — no item slots, just two data values:
 * <ul>
 *   <li>0 – currentOutput × 1000 (fixed-point, decode ÷ 1000)</li>
 *   <li>1 – sky light level above the panel (0–15)</li>
 * </ul>
 */
public class SolarPanelMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Client-side constructor — reads block pos from packet. */
    public SolarPanelMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    /** Server-side constructor. */
    public SolarPanelMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.SOLAR_PANEL.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    /** Current EU/tick output (decoded from fixed-point ×1000). */
    public float getCurrentOutput() { return data.get(0) / 1000f; }

    /** Sky light level directly above the panel (0–15). */
    public int getSkyLight() { return data.get(1); }

    /**
     * Sky light bar width in pixels (max {@value #BAR_MAX_W} px).
     * Scales 0–15 to 0–{@value #BAR_MAX_W}.
     */
    public static final int BAR_MAX_W = 100;
    public int getSkyLightBarWidth() { return getSkyLight() * BAR_MAX_W / 15; }

    /**
     * Output bar width in pixels (max {@value #BAR_MAX_W} px).
     * Scales 0–EU_PER_TICK (1.0f) to 0–{@value #BAR_MAX_W}.
     */
    public int getOutputBarWidth() { return (int)(getCurrentOutput() * BAR_MAX_W); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.SOLAR_PANEL.get());
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
