package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
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

    public static final int BAR_MAX_W = 100;

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

        GuiLayout layout = GuiLayoutLoader.load("solar_panel");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    /** Current EU/tick output (decoded from fixed-point ×1000). */
    public float getCurrentOutput() { return data.get(0) / 1000f; }

    /** Sky light level directly above the panel (0–15). */
    public int getSkyLight() { return data.get(1); }

    public int getSkyLightBarWidth() { return getSkyLight() * BAR_MAX_W / 15; }
    public int getOutputBarWidth()   { return (int)(getCurrentOutput() * BAR_MAX_W); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.SOLAR_PANEL.get());
    }
}
