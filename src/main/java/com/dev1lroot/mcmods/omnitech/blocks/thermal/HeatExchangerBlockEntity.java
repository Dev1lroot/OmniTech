/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.HeatExchangerMenu;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import com.dev1lroot.mcmods.omnitech.util.FluidMixing;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import com.dev1lroot.mcmods.omnitech.util.SolutionPhases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Heat Exchanger — passive thermal conditioner.
 *
 * <p>When fluid enters, its temperature is averaged with ambient
 * ({@code outTemp = (fluidTemp + ambient) / 2}) and output.  The machine itself
 * absorbs the fluid's temperature ({@code machineTemp = fluidTemp}) and emits that
 * as its thermal-network temperature.  machineTemp decays toward ambient at 1 °C
 * per {@link #DECAY_INTERVAL} ticks.  Processing is gated: the machine can only
 * condition a fluid whose temperature differs from machineTemp by at least 1 °C.
 */
public class HeatExchangerBlockEntity extends BlockEntity implements MenuProvider, IThermalNode {

    public static final int INPUT_TANK_CAPACITY  = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;
    public static final int PROCESS_TIME         = 20;
    public static final int BATCH_SIZE           = 1_000;

    private static final int AMBIENT_TEMPERATURE = 20;
    private static final int DECAY_INTERVAL      = 20;

    private FluidStack inputFluid  = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private int machineTemp  = AMBIENT_TEMPERATURE;
    private int processTimer = 0;
    private int decayTimer   = 0;

    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // [0]=machineTemp  [1]=processTimer
    // [2]=inAmt  [3]=inCap  [4]=outAmt  [5]=outCap
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> machineTemp;
                case 1 -> processTimer;
                case 2 -> inputFluid.getAmount();
                case 3 -> INPUT_TANK_CAPACITY;
                case 4 -> outputFluid.getAmount();
                case 5 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {
            if (i == 0) machineTemp  = value;
            if (i == 1) processTimer = value;
        }
        @Override public int getCount() { return 6; }
    };

    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.HEAT_EXCHANGER.get(), pos, state);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.omnitech.heat_exchanger"); }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new HeatExchangerMenu(containerId, inv, this, dataAccess);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── IThermalNode ──────────────────────────────────────────────────────────

    @Override
    public float getTemperature() { return machineTemp; }

    // The machine is a thermal source; its temperature is set by fluid physics alone.
    @Override
    public void applyHeat(float dT) {}

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            HeatExchangerBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(HeatExchangerBlock.FACING);

        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var src = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(facing), facing.getOpposite());
            if (src != null) changed |= FluidNetworkUtil.tryPullFluid(src, be.inputFluidHandler);
        }

        if (be.canProcess()) {
            be.processTimer++;
            if (be.processTimer >= PROCESS_TIME) {
                be.process();
            }
            changed = true;
        } else if (be.processTimer > 0) {
            be.processTimer = 0;
            changed = true;
        }

        // Ambient decay — machineTemp drifts toward ambient by 1 °C every DECAY_INTERVAL ticks
        if (be.machineTemp != AMBIENT_TEMPERATURE) {
            be.decayTimer++;
            if (be.decayTimer >= DECAY_INTERVAL) {
                be.decayTimer = 0;
                if (be.machineTemp > AMBIENT_TEMPERATURE) be.machineTemp--;
                else be.machineTemp++;
                changed = true;
            }
        } else {
            be.decayTimer = 0;
        }

        boolean shouldBeLit = be.canProcess() || be.machineTemp != AMBIENT_TEMPERATURE;
        if (state.getValue(HeatExchangerBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(HeatExchangerBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        if (!be.outputFluid.isEmpty()) {
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing.getOpposite()), facing);
            if (neighbor != null) {
                if (SolutionFluids.isMixture(be.outputFluid)) {
                    // A mixture may hold several phases after the machine changed its conditions:
                    // condensed part and gas part leave separately.
                    FluidStack before = be.outputFluid;
                    be.outputFluid = SolutionPhases.pushByPhase(be.outputFluid, neighbor, 1000);
                    changed |= be.outputFluid != before;
                } else {
                    changed |= FluidNetworkUtil.tryPushFluid(be.outputFluidHandler, neighbor);
                }
            }
        }

        if (changed) { be.setChanged(); if (!level.isClientSide()) level.sendBlockUpdated(pos, state, state, 3); }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess() {
        if (inputFluid.isEmpty() || inputFluid.getAmount() < BATCH_SIZE) return false;
        int fluidT = fluidTemp(inputFluid);
        // machine must differ from fluid by at least 1 °C to exchange heat
        if (Math.abs(machineTemp - fluidT) < 1) return false;
        if (outputFluid.isEmpty()) return true;
        if (!FluidMixing.canBlend(outputFluid, FluidResource.of(inputFluid))) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= BATCH_SIZE;
    }

    private void process() {
        int fluidT  = fluidTemp(inputFluid);
        int fluidP  = fluidPressure(inputFluid);
        int outTemp = (fluidT + AMBIENT_TEMPERATURE) / 2;
        machineTemp = fluidT;

        FluidResource res = FluidResource.of(inputFluid);
        inputFluid.shrink(BATCH_SIZE);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack produced = res.toStack(BATCH_SIZE);
        applyAttributes(produced, outTemp, fluidP);

        // Blends by volume — temperature, pressure and, for mixtures, composition.
        outputFluid = FluidNetworkUtil.blendInto(outputFluid, FluidResource.of(produced), BATCH_SIZE);

        processTimer = 0;
        setChanged();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyAttributes(FluidStack fs, int temp, int pressure) {
        if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        else            fs.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressure != 101) fs.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
        else                 fs.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    private static int fluidTemp(FluidStack fs) {
        if (fs.isEmpty()) return 20;
        Integer t = fs.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

    private static int fluidPressure(FluidStack fs) {
        if (fs.isEmpty()) return 101;
        Integer p = fs.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : 101;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getInputFluid()  { return inputFluid; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getMachineTemp()        { return machineTemp; }
    public int getProcessTimer()       { return processTimer; }

    // ── Tank handlers ─────────────────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()         { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size()                             { return 1; }
        @Override public FluidResource getResource(int i)      { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int i)           { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)        { return FluidMixing.canBlend(inputFluid, r); }
        @Override public int insert(int i, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || !FluidMixing.canBlend(inputFluid, resource)) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, resource, toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource r, int amount, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()         { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size()                             { return 1; }
        @Override public FluidResource getResource(int i)      { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int i)           { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)        { return false; }
        @Override public int insert(int i, FluidResource r, int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int i, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inputFluid   = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid  = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        machineTemp  = input.getIntOr("MachineTemp",  AMBIENT_TEMPERATURE);
        processTimer = input.getIntOr("ProcessTimer", 0);
        decayTimer   = input.getIntOr("DecayTimer",   0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putInt("MachineTemp",  machineTemp);
        output.putInt("ProcessTimer", processTimer);
        output.putInt("DecayTimer",   decayTimer);
    }
}
