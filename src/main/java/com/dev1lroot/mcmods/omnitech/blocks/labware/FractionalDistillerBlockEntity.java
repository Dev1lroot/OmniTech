/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.blocks.ThermalState;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.FractionalDistillerMenu;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipeManager;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Block entity for one segment of the Fractional Distiller multiblock.
 *
 * <h3>Multiblock rules</h3>
 * <ul>
 *   <li>The <em>bottom block</em> is any segment whose block directly below is
 *       <em>not</em> a {@link FractionalDistillerBlock}.  It acts as the master.</li>
 *   <li>Only the master runs the full server tick (processing, recipe matching,
 *       heat management, output distribution).  Upper segments return early.</li>
 *   <li>Each segment has its own {@link #outputFluid} tank exposed on the back face.</li>
 *   <li>The master holds {@link #inputFluid} and {@link #temperature}.</li>
 * </ul>
 *
 * <h3>Recipe matching</h3>
 * A recipe is selected when:
 * <ol>
 *   <li>Its input fluid matches the master's input tank.</li>
 *   <li>The temperature condition is satisfied.</li>
 *   <li>Its output count equals the current structure height.</li>
 * </ol>
 * Output {@code i} is deposited into segment {@code i}'s output tank (bottom = 0).
 */
public class FractionalDistillerBlockEntity extends BlockEntity
        implements MenuProvider, IHeatReceiver, IColdReceiver {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int INPUT_TANK_CAPACITY  = 16_000;
    public static final int OUTPUT_TANK_CAPACITY =  8_000;
    public static final int MAX_HEIGHT           = 4;

    /** Ambient bleed rate (°C/tick) applied when no conductor is connected. */
    private static final float AMBIENT_BLEED = 0.5f;

    // ── State shared by all segments ──────────────────────────────────────────

    /** This segment's output product. Exposed on the back face. */
    FluidStack outputFluid = FluidStack.EMPTY;

    // ── State owned by the master (bottom) segment ────────────────────────────

    /** Fluid being processed. Only meaningful on the master. */
    FluidStack inputFluid  = FluidStack.EMPTY;

    /** Current temperature in °C, driven by the adjacent thermal conductor. */
    float temperature = IThermalNode.AMBIENT_TEMP;
    private int processTimer  = 0;
    private int processTotalTime = 20;
    /** Cached structure height updated every tick by the master. */
    private int structureHeight  = 1;

    private FractionalDistillationRecipe currentRecipe   = null;
    private String                       currentRecipeId = null;

    // ── Fluid handlers ────────────────────────────────────────────────────────

    /** Insert-only; exposed on the front face of the bottom block. */
    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    /** Extract-only; exposed on the back face of every block. */
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // ── ContainerData (synced from master to GUI) ─────────────────────────────
    // Index: 0=temperature×10  1=requiredTemp  2=processTimer  3=processTotalTime
    //        4=inputFluidAmount  5=structureHeight

    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> (int)(temperature * 10f);
                case 1 -> currentRecipe != null ? currentRecipe.getRequiredTemperature() : 0;
                case 2 -> processTimer;
                case 3 -> processTotalTime;
                case 4 -> inputFluid.getAmount();
                case 5 -> structureHeight;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {
            switch (i) {
                case 0 -> temperature      = v / 10f;
                case 2 -> processTimer     = v;
                case 3 -> processTotalTime = v;
                case 5 -> structureHeight  = v;
            }
        }
        @Override public int getCount() { return 6; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public FractionalDistillerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FRACTIONAL_DISTILLER.get(), pos, state);
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.fractional_distiller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FractionalDistillerMenu(id, inv, this, dataAccess);
    }

    // ── IHeatReceiver / IColdReceiver ─────────────────────────────────────────
    // Implemented so ThermalConductorBlock.canConnectTo() recognises this block
    // and renders the connector arm.  Temperature is read directly from the
    // conductor each tick in serverTick(); push-based accumulation is not used.

    @Override public int addHeat(int celsius) { return 0; }
    @Override public int addCold(int celsius) { return 0; }

    // ── Multiblock helpers ────────────────────────────────────────────────────

    /**
     * True when the block directly below is <em>not</em> a
     * {@link FractionalDistillerBlock}.  This segment is the master.
     */
    public boolean isBottomBlock() {
        if (level == null) return true;
        return !(level.getBlockState(worldPosition.below()).getBlock()
                instanceof FractionalDistillerBlock);
    }

    /**
     * Scans downward to locate the master of this structure.
     * Returns {@code null} if the level is unloaded.
     */
    public FractionalDistillerBlockEntity findBottomBlock() {
        if (level == null) return null;
        BlockPos cursor = worldPosition;
        for (int i = 0; i < MAX_HEIGHT; i++) {
            BlockPos below = cursor.below();
            if (level.getBlockState(below).getBlock() instanceof FractionalDistillerBlock) {
                cursor = below;
            } else {
                BlockEntity be = level.getBlockEntity(cursor);
                return be instanceof FractionalDistillerBlockEntity fbe ? fbe : null;
            }
        }
        // Reached limit — return whatever is at the bottom of our scan
        BlockEntity be = level.getBlockEntity(cursor);
        return be instanceof FractionalDistillerBlockEntity fbe ? fbe : null;
    }

    /**
     * Scans upward from this (master) block and returns all consecutive
     * {@link FractionalDistillerBlockEntity} segments in bottom-to-top order.
     * Must be called on the bottom block.
     */
    public List<FractionalDistillerBlockEntity> getStructure() {
        List<FractionalDistillerBlockEntity> result = new ArrayList<>();
        if (level == null) return result;
        BlockPos cursor = worldPosition;
        for (int i = 0; i < MAX_HEIGHT; i++) {
            BlockEntity be = level.getBlockEntity(cursor);
            if (be instanceof FractionalDistillerBlockEntity fbe) {
                result.add(fbe);
                cursor = cursor.above();
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Returns the output fluid of the i-th segment in the structure,
     * as seen from this (master) block.  Safe to call from client code.
     */
    public FluidStack getStructureOutputFluid(int i) {
        List<FractionalDistillerBlockEntity> structure = getStructure();
        if (i < 0 || i >= structure.size()) return FluidStack.EMPTY;
        return structure.get(i).outputFluid;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  FractionalDistillerBlockEntity be) {

        // Only the master drives the multiblock; upper segments do nothing.
        if (!be.isBottomBlock()) return;

        boolean dirty = false;
        Direction facing = state.getValue(FractionalDistillerBlock.FACING);

        // 1. Pull input fluid from the front face
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var source = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (source != null) dirty |= FluidNetworkUtil.tryPullFluid(source, be.inputFluidHandler);
        }

        // 2. Scan the structure this tick
        List<FractionalDistillerBlockEntity> structure = be.getStructure();
        be.structureHeight = structure.size();

        // 3. Sync temperature from the most extreme adjacent conductor (any segment)
        float bestTemp = Float.NaN;
        for (FractionalDistillerBlockEntity seg : structure) {
            for (Direction scanDir : Direction.values()) {
                BlockEntity nb = level.getBlockEntity(seg.worldPosition.relative(scanDir));
                if (nb instanceof ThermalConductorBlockEntity tc) {
                    float t = tc.getTemperature();
                    if (Float.isNaN(bestTemp)
                            || Math.abs(t - IThermalNode.AMBIENT_TEMP) > Math.abs(bestTemp - IThermalNode.AMBIENT_TEMP)) {
                        bestTemp = t;
                    }
                }
            }
        }
        if (!Float.isNaN(bestTemp)) {
            if (be.temperature != bestTemp) { be.temperature = bestTemp; dirty = true; }
        } else {
            // No conductor — drift back to ambient
            float diff = be.temperature - IThermalNode.AMBIENT_TEMP;
            if (Math.abs(diff) > AMBIENT_BLEED) {
                be.temperature -= Math.signum(diff) * AMBIENT_BLEED;
                dirty = true;
            } else if (diff != 0f) {
                be.temperature = IThermalNode.AMBIENT_TEMP;
                dirty = true;
            }
        }

        // 4. Recipe matching — input + temperature + output count must align
        Optional<FractionalDistillationRecipe> found =
                FractionalDistillationRecipeManager.findRecipe(
                        be.inputFluid, be.temperature, be.structureHeight);

        if (found.isPresent()) {
            FractionalDistillationRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe     = recipe;
                be.currentRecipeId   = recipe.getId();
                be.processTotalTime  = recipe.getProductionTime();
                be.processTimer      = 0;
                dirty = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe   = null;
                be.currentRecipeId = null;
                be.processTimer    = 0;
                dirty = true;
            }
        }

        // 5. Advance processing timer
        boolean canRun = be.currentRecipe != null && be.canProcess(be.currentRecipe, structure);
        if (canRun) {
            be.processTimer++;
            dirty = true;
            if (be.processTimer >= be.processTotalTime) {
                be.process(level, structure);
                dirty = true;
            }
        } else if (be.processTimer > 0) {
            be.processTimer = 0;
            dirty = true;
        }

        // 6. Update THERMAL_STATE blockstate for every segment in the structure
        ThermalState targetThermal = ThermalState.of((int) be.temperature);
        for (FractionalDistillerBlockEntity seg : structure) {
            BlockState segState = level.getBlockState(seg.worldPosition);
            if (segState.getBlock() instanceof FractionalDistillerBlock
                    && segState.getValue(FractionalDistillerBlock.THERMAL_STATE) != targetThermal) {
                level.setBlock(seg.worldPosition,
                        segState.setValue(FractionalDistillerBlock.THERMAL_STATE, targetThermal), 3);
                dirty = true;
            }
        }

        // 7. Push each segment's output to its back face
        Direction back = facing.getOpposite();
        for (FractionalDistillerBlockEntity seg : structure) {
            if (!seg.outputFluid.isEmpty()) {
                var neighbor = level.getCapability(Capabilities.Fluid.BLOCK,
                        seg.worldPosition.relative(back), back.getOpposite());
                if (neighbor != null) dirty |= FluidNetworkUtil.tryPushFluid(seg.outputFluidHandler, neighbor);
            }
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess(FractionalDistillationRecipe recipe,
                                List<FractionalDistillerBlockEntity> structure) {
        if (!recipe.matchesInput(inputFluid))   return false;
        if (!recipe.temperatureMet(temperature)) return false;

        // Verify there is enough space in each segment's output tank
        for (int i = 0; i < recipe.getOutputCount() && i < structure.size(); i++) {
            FluidStack out      = recipe.getOutputStack(i);
            FluidStack existing = structure.get(i).outputFluid;
            if (existing.isEmpty()) continue;
            if (!existing.is(out.getFluid())) return false;
            if (OUTPUT_TANK_CAPACITY - existing.getAmount() < out.getAmount()) return false;
        }
        return true;
    }

    private void process(Level level, List<FractionalDistillerBlockEntity> structure) {
        if (currentRecipe == null) return;

        int machineTemp = (int) temperature;

        // Consume input
        inputFluid.shrink(currentRecipe.getInputAmount());
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        // Distribute outputs to each segment's tank with temperature and pressure stamped
        for (int i = 0; i < currentRecipe.getOutputCount() && i < structure.size(); i++) {
            FluidStack out = currentRecipe.getOutputStack(i);
            FractionalDistillerBlockEntity seg = structure.get(i);

            int outTemp     = currentRecipe.hasOutputTemp(i)     ? currentRecipe.getOutputTemp(i)     : machineTemp;
            int outPressure = currentRecipe.hasOutputPressure(i) ? currentRecipe.getOutputPressure(i) : 101;

            FluidStack produced = out.copy();
            if (outTemp != 20) produced.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), outTemp);
            else               produced.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            if (outPressure != 101) produced.set(OmniTechDataComponents.FLUID_PRESSURE.get(), outPressure);
            else                    produced.remove(OmniTechDataComponents.FLUID_PRESSURE.get());

            if (seg.outputFluid.isEmpty()) {
                seg.outputFluid = produced;
            } else if (seg.outputFluid.is(out.getFluid())) {
                int existAmt = seg.outputFluid.getAmount();
                Integer existTBox = seg.outputFluid.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
                int existTemp = existTBox != null ? existTBox : 20;
                Integer existPBox = seg.outputFluid.get(OmniTechDataComponents.FLUID_PRESSURE.get());
                int existPressure = existPBox != null ? existPBox : 101;

                int blendedTemp     = (existTemp     * existAmt + outTemp     * out.getAmount()) / (existAmt + out.getAmount());
                int blendedPressure = (existPressure * existAmt + outPressure * out.getAmount()) / (existAmt + out.getAmount());

                seg.outputFluid.grow(out.getAmount());
                if (blendedTemp != 20) seg.outputFluid.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), blendedTemp);
                else                   seg.outputFluid.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
                if (blendedPressure != 101) seg.outputFluid.set(OmniTechDataComponents.FLUID_PRESSURE.get(), blendedPressure);
                else                        seg.outputFluid.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
            }

            seg.setChanged();
            level.sendBlockUpdated(seg.worldPosition,
                    level.getBlockState(seg.worldPosition),
                    level.getBlockState(seg.worldPosition), 3);
        }

        processTimer = 0;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getInputFluid()   { return inputFluid; }
    public FluidStack getOutputFluid()  { return outputFluid; }
    public float      getTemperature()  { return temperature; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Sync & persistence ────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER);
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        outputFluid      = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        inputFluid       = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        temperature      = input.getFloatOr("Temperature",     IThermalNode.AMBIENT_TEMP);
        processTimer     = input.getIntOr("ProcessTimer",      0);
        processTotalTime = input.getIntOr("ProcessTotalTime",  20);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.putFloat("Temperature",       temperature);
        output.putInt("ProcessTimer",        processTimer);
        output.putInt("ProcessTotalTime",    processTotalTime);
    }

    // ── Inner tank handlers ───────────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()         { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size()                             { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid);
        }

        @Override public long getAmountAsLong(int i)                      { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource res) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource res)        { return true; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty()) return 0;
            if (!inputFluid.isEmpty() && !resource.is(inputFluid.getFluid())) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, resource, toFill);
            return toFill;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // input-only
        }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()         { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size()                             { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid);
        }

        @Override public long getAmountAsLong(int i)                      { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource res) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource res)        { return false; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // extract-only
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }
}
