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
    private int outputSignal = 0;  // set by LogicMachine

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

    /** Called by LogicMachine when executing OUT instruction. */
    public void setOutputSignal(int signal) {
        int clamped = Math.clamp(signal, 0, 15);
        if (outputSignal == clamped) return;
        outputSignal = clamped;
        setChanged();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            GPIOPortBlockEntity be) {
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
