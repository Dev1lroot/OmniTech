package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ElectricEngineBlockEntity;
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
 * Menu for the Electric Engine — display-only, no item slots.
 *
 * <h3>ContainerData layout (4 slots)</h3>
 * <ul>
 *   <li>0 – powered (1 = active, 0 = idle)</li>
 *   <li>1 – forward: lastKfAmount × 100; reverse: euBuffer × 10</li>
 *   <li>2 – forward: EU/tick × 10 when active; reverse: KF output × 100 when active</li>
 *   <li>3 – mode (0 = forward KF→EU, 1 = reverse EU→KF)</li>
 * </ul>
 *
 * <h3>Button IDs</h3>
 * <ul>
 *   <li>0 – toggle mode (forward ↔ reverse)</li>
 * </ul>
 */
public class ElectricEngineMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;

    /** Client-side constructor. */
    public ElectricEngineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    /** Server-side constructor. */
    public ElectricEngineMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_ENGINE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("electric_engine");
        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public boolean isPowered()   { return data.get(0) == 1; }
    public boolean isReverse()   { return data.get(3) == 1; }

    // Forward-mode accessors
    /** KF received (decoded from fixed-point ×100). Only meaningful in forward mode. */
    public float getKfReceived() { return data.get(1) / 100f; }
    /** EU/tick output (decoded from fixed-point ×10). Only meaningful in forward mode. */
    public float getEuPerTick()  { return data.get(2) / 10f; }

    // Reverse-mode accessors
    /** EU buffer level (decoded from fixed-point ×10). Only meaningful in reverse mode. */
    public float getEuBuffer()   { return data.get(1) / 10f; }
    /** KF output (decoded from fixed-point ×100). Only meaningful in reverse mode. */
    public float getKfOutput()   { return data.get(2) / 100f; }

    // ── Button handling ────────────────────────────────────────────────────────

    /**
     * Button 0 — toggle between forward (KF→EU) and reverse (EU→KF) mode.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && blockEntity instanceof ElectricEngineBlockEntity engine) {
            engine.toggleMode();
            return true;
        }
        return false;
    }

    // ── Container contract ─────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_ENGINE.get());
    }
}
