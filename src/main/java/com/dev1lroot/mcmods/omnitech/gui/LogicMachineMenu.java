package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class LogicMachineMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    @Nullable private final LogicMachineBlockEntity blockEntity;

    // Synced server→client every tick via the container system.
    // Slot 0: 1 = running, 0 = not running.
    private int syncedRunning = 0;

    /** Client-side constructor (opened from network). */
    public LogicMachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public LogicMachineMenu(int id, Inventory inv, @Nullable BlockEntity be) {
        super(OmniTechMenuTypes.LOGIC_MACHINE.get(), id);
        this.pos = be != null ? be.getBlockPos() : BlockPos.ZERO;
        this.blockEntity = be instanceof LogicMachineBlockEntity lm ? lm : null;

        addDataSlots(new ContainerData() {
            @Override public int get(int i) {
                // Server: read live state from BE. Client: never called.
                return (blockEntity != null && blockEntity.isRunning()) ? 1 : 0;
            }
            @Override public void set(int i, int v) {
                // Client: called by the container sync mechanism with the server value.
                syncedRunning = v;
            }
            @Override public int getCount() { return 1; }
        });
    }

    public BlockPos getBlockPos() { return pos; }

    @Nullable
    public LogicMachineBlockEntity getBlockEntity() { return blockEntity; }

    /** True when the server reports the VM is running. Accurate on both sides. */
    public boolean isRunning() { return syncedRunning != 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof LogicMachineBlockEntity lm) {
            return lm.handleButton(id);
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (blockEntity != null && player instanceof ServerPlayer sp) {
            blockEntity.removeViewer(sp);
        }
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                player.level(), pos), player, OmniTechBlocks.LOGIC_MACHINE.get());
    }
}
