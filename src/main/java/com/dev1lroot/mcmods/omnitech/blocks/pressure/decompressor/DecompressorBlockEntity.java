/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor;

import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.DecompressorMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Decompressor — recipe-free, attribute-based decompression.
 *
 * <p>Always processes {@link #BATCH_SIZE} mB per cycle at a KF cost of
 * 0.1 KF per 100 kPa of pressure difference.  KF is accumulated from the
 * network tick-by-tick; processing fires as soon as the buffer reaches the
 * cycle cost.  A hard cooldown of {@link #MIN_CYCLE_TICKS} enforces a
 * maximum throughput of {@link #BATCH_SIZE} mB per {@link #MIN_CYCLE_TICKS}
 * ticks regardless of how much KF is buffered.
 */
public class DecompressorBlockEntity extends BlockEntity implements MenuProvider, IKineticReceiver {

    public static final int   INPUT_TANK_CAPACITY  = 8_000;
    public static final int   OUTPUT_TANK_CAPACITY = 8_000;
    public static final int   BATCH_SIZE           = 1_000;
    public static final int   MIN_CYCLE_TICKS      = 10;
    public static final float KF_PER_100KPA        = 0.1f;
    public static final int   MIN_PRESSURE         = 101;
    public static final int   MAX_PRESSURE         = 16_000;

    private FluidStack inputFluid      = FluidStack.EMPTY;
    private FluidStack outputFluid     = FluidStack.EMPTY;
    private int        processCooldown = 0;
    private int        targetPressure  = 101;
    private float      kineticForce    = 0f;

    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // [0]=inAmt  [1]=inCap  [2]=outAmt  [3]=outCap  [4]=targetPressure
    // [5]=processCooldown  [6]=kfCurrent×100  [7]=kfRequired×100
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> inputFluid.getAmount();
                case 1 -> INPUT_TANK_CAPACITY;
                case 2 -> outputFluid.getAmount();
                case 3 -> OUTPUT_TANK_CAPACITY;
                case 4 -> targetPressure;
                case 5 -> processCooldown;
                case 6 -> (int)(kineticForce * 100f);
                case 7 -> (int)(kfNeededForCycle() * 100f);
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {
            if (i == 4) targetPressure  = value;
            if (i == 5) processCooldown = value;
        }
        @Override public int getCount() { return 8; }
    };

    public DecompressorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.DECOMPRESSOR.get(), pos, state);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.omnitech.decompressor"); }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new DecompressorMenu(containerId, inv, this, dataAccess);
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

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    private float kfNeededForCycle() {
        if (inputFluid.isEmpty()) return 0f;
        int deltaP = Math.abs(fluidPressure(inputFluid) - targetPressure);
        return deltaP / 100f * KF_PER_100KPA;
    }

    @Override
    public float getKfDemand() {
        if (!canProcessFluid()) return 0f;
        return kfNeededForCycle() / MIN_CYCLE_TICKS;
    }

    @Override
    public boolean addKineticForce(float amount) {
        if (!canProcessFluid()) { kineticForce = 0f; return false; }
        float kfNeeded = kfNeededForCycle();
        kineticForce = Math.min(kineticForce + amount, kfNeeded * 5f);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            DecompressorBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(DecompressorBlock.FACING);

        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var src = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(facing), facing.getOpposite());
            if (src != null) changed |= FluidNetworkUtil.tryPullFluid(src, be.inputFluidHandler);
        }

        if (be.processCooldown > 0) {
            be.processCooldown--;
            changed = true;
        }

        if (be.processCooldown == 0 && be.canProcess()) {
            be.process();
            changed = true;
        }

        boolean shouldBeLit = be.canProcessFluid();
        if (state.getValue(DecompressorBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(DecompressorBlock.LIT, shouldBeLit), 3);
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

    private boolean canProcessFluid() {
        if (inputFluid.isEmpty() || inputFluid.getAmount() < BATCH_SIZE) return false;
        if (fluidPressure(inputFluid) <= targetPressure) return false;
        if (outputFluid.isEmpty()) return true;
        if (!FluidMixing.canBlend(outputFluid, FluidResource.of(inputFluid))) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= BATCH_SIZE;
    }

    private boolean canProcess() {
        if (!canProcessFluid()) return false;
        float kfNeeded = kfNeededForCycle();
        return kfNeeded <= 0f || kineticForce >= kfNeeded;
    }

    private void process() {
        float kfCost     = kfNeededForCycle();
        int   inPressure = fluidPressure(inputFluid);
        int   inTemp     = fluidTemp(inputFluid);
        int   deltaP     = inPressure - targetPressure;
        int   minTemp    = SolutionPhases.minTemp(inputFluid);   // tightest floor of every component
        int   outTemp    = Math.max(minTemp, inTemp - deltaP / 20);
        FluidResource res = FluidResource.of(inputFluid);

        inputFluid.shrink(BATCH_SIZE);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack produced = res.toStack(BATCH_SIZE);
        applyAttributes(produced, outTemp, targetPressure);

        // Blends by volume — temperature, pressure and, for mixtures, composition.
        outputFluid = FluidNetworkUtil.blendInto(outputFluid, FluidResource.of(produced), BATCH_SIZE);

        kineticForce    = Math.max(0f, kineticForce - kfCost);
        processCooldown = MIN_CYCLE_TICKS;
        setChanged();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static void applyAttributes(FluidStack fs, int temp, int pressure) {
        if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        else            fs.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressure != 101) fs.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
        else                 fs.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    static int fluidTemp(FluidStack fs) {
        if (fs.isEmpty()) return 20;
        Integer t = fs.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

    static int fluidPressure(FluidStack fs) {
        if (fs.isEmpty()) return 101;
        Integer p = fs.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : 101;
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public void setTargetPressure(int kPa) {
        targetPressure = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, kPa));
        setChanged();
    }

    public FluidStack getInputFluid()    { return inputFluid; }
    public FluidStack getOutputFluid()   { return outputFluid; }
    public int        getTargetPressure(){ return targetPressure; }

    // ── Tank handlers ─────────────────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()         { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int i) { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int i) { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r) { return FluidMixing.canBlend(inputFluid, r); }
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
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int i) { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int i) { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r) { return false; }
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
        inputFluid      = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid     = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        processCooldown = input.getIntOr("ProcessCooldown", 0);
        targetPressure  = input.getIntOr("TargetPressure",  101);
        kineticForce    = input.getFloatOr("KineticForce",  0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putInt("ProcessCooldown", processCooldown);
        output.putInt("TargetPressure",  targetPressure);
        output.putFloat("KineticForce",  kineticForce);
    }
}
