/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import com.dev1lroot.mcmods.omnitech.gui.GPIOPortMenu;
import net.minecraft.world.MenuProvider;

public class GPIOPortBlockEntity extends BlockEntity implements MenuProvider {

    private int portId      = 0;   // 0 .. 65535
    private int inputSignal = 0;   // read from adjacent redstone
    // Written from VM thread, read+applied from server thread via serverTick()
    private volatile int outputSignal = 0;
    private volatile boolean outputDirty = false;

    public GPIOPortBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.GPIO_PORT.get(), pos, state);
    }

    public int getPortId()       { return portId; }
    public int getInputSignal()  { return inputSignal; }
    public int getOutputSignal() { return outputSignal; }

    public void setPortId(int id) {
        portId = Math.clamp(id, 0, 65535);
        setChanged();
    }

    /**
     * Called from the VM thread via the MMIO writer callback.
     * Only mutates volatile fields — level methods are deferred to serverTick().
     */
    public void setOutputSignal(int signal) {
        int clamped = Math.clamp(signal, 0, 15);
        if (outputSignal == clamped) return;
        outputSignal = clamped;
        outputDirty = true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            GPIOPortBlockEntity be) {
        // Apply any output signal written by the VM thread
        if (be.outputDirty) {
            be.outputDirty = false;
            be.setChanged();
            level.updateNeighborsAt(pos, state.getBlock());
            level.sendBlockUpdated(pos, state, state, 3);
        }

        // Sample incoming redstone from all 6 sides each tick
        int max = 0;
        for (Direction dir : Direction.values()) {
            max = Math.max(max, level.getSignal(pos.relative(dir), dir.getOpposite()));
        }
        if (be.inputSignal != max) {
            be.inputSignal = max;
            be.setChanged();
        }
    }

    // ── MenuProvider ─────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.gpio_port");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId,
            Inventory playerInventory, Player player) {
        ContainerData data = new SimpleContainerData(3);
        data.set(0, portId);
        data.set(1, inputSignal);
        data.set(2, outputSignal);
        return new GPIOPortMenu(containerId, playerInventory, this, data);
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("PortId", portId);
        out.putInt("OutputSignal", outputSignal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        portId       = in.getIntOr("PortId", 0);
        outputSignal = in.getIntOr("OutputSignal", 0);
    }
}
