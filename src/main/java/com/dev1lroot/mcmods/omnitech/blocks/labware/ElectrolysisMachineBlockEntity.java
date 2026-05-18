/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectrolysisMachineMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
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
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Block entity for the Electrolysis Machine.
 *
 * <h3>Fluid I/O</h3>
 * <ul>
 *   <li>Front (FACING): pulls input fluid.</li>
 *   <li>Left (FACING.getCounterClockWise()): pushes anode output fluid.</li>
 *   <li>Right (FACING.getClockWise()): pushes cathode output fluid.</li>
 *   <li>Back (FACING.getOpposite()): pushes solution output fluid.</li>
 * </ul>
 *
 * <h3>Energy model</h3>
 * Each recipe tick requires {@code energyRequired / COOK_TIME} EU from the buffer.
 * Progress pauses when the buffer is empty; resets when inputs become invalid.
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – energyStored × 10</li>
 *   <li>1 – MAX_EU × 10</li>
 *   <li>2 – cookProgress (0..COOK_TIME)</li>
 *   <li>3 – COOK_TIME</li>
 *   <li>4 – currentRecipeEnergy × 10</li>
 *   <li>5 – inputFluid amount</li>
 *   <li>6 – INPUT_TANK_CAPACITY</li>
 *   <li>7 – anodeFluid amount</li>
 *   <li>8 – OUTPUT_TANK_CAPACITY</li>
 *   <li>9 – cathodeFluid amount</li>
 *   <li>10 – OUTPUT_TANK_CAPACITY</li>
 *   <li>11 – solutionFluid amount</li>
 *   <li>12 – OUTPUT_TANK_CAPACITY</li>
 * </ul>
 */
public class ElectrolysisMachineBlockEntity extends BaseContainerBlockEntity implements IElectricReceiver, IHeatReceiver, WorldlyContainer
{
    public static final int SLOT_ANODE   = 0;
    public static final int SLOT_CATHODE = 1;
    public static final int SLOT_COUNT   = 2;

    public static final int   INPUT_TANK_CAPACITY  = 8_000;
    public static final int   OUTPUT_TANK_CAPACITY = 8_000;
    public static final float MAX_EU               = 1600f;
    public static final int   COOK_TIME            = 100;
    public static final int   MAX_HEAT             = 3000;
    private static final int  AMBIENT_TEMPERATURE  = 20;
    private static final int  HEAT_LOSS_INTERVAL   = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private FluidStack inputFluid    = FluidStack.EMPTY;
    private FluidStack anodeFluid    = FluidStack.EMPTY;
    private FluidStack cathodeFluid  = FluidStack.EMPTY;
    private FluidStack solutionFluid = FluidStack.EMPTY;

    private float energyStored        = 0f;
    private int   cookProgress        = 0;
    private float currentRecipeEnergy = 0f;
    private int   temperature         = 0;
    private int   requiredTemperature = 0;
    private int   heatLossTimer       = 0;

    private ElectrolysisRecipe currentRecipe   = null;
    private String             currentRecipeId = null;

    public final ResourceHandler<FluidResource> inputFluidHandler    = new InputTankHandler();
    public final ResourceHandler<FluidResource> anodeFluidHandler    = new AnodeOutputHandler();
    public final ResourceHandler<FluidResource> cathodeFluidHandler  = new CathodeOutputHandler();
    public final ResourceHandler<FluidResource> solutionFluidHandler = new SolutionOutputHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0  -> (int)(energyStored * 10f);
                case 1  -> (int)(MAX_EU * 10f);
                case 2  -> cookProgress;
                case 3  -> COOK_TIME;
                case 4  -> (int)(currentRecipeEnergy * 10f);
                case 5  -> inputFluid.getAmount();
                case 6  -> INPUT_TANK_CAPACITY;
                case 7  -> anodeFluid.getAmount();
                case 8  -> OUTPUT_TANK_CAPACITY;
                case 9  -> cathodeFluid.getAmount();
                case 10 -> OUTPUT_TANK_CAPACITY;
                case 11 -> solutionFluid.getAmount();
                case 12 -> OUTPUT_TANK_CAPACITY;
                case 13 -> temperature;
                case 14 -> requiredTemperature;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0  -> energyStored = value / 10f;
                case 2  -> cookProgress = value;
                case 13 -> temperature  = value;
                case 14 -> requiredTemperature = value;
            }
        }
        @Override public int getCount() { return 15; }
    };

    public ElectrolysisMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTROLYSIS_MACHINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electrolysis_machine");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectrolysisMachineMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── IElectricReceiver ─────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount) {
        float space = MAX_EU - energyStored;
        if (space <= 0f) return 0f;
        float accepted = Math.min(amount, space);
        energyStored += accepted;
        setChanged();
        return accepted;
    }

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (temperature >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - temperature);
        temperature += absorbed;
        setChanged();
        return absorbed;
    }

    public int getTemperature() { return temperature; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectrolysisMachineBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(ElectrolysisMachineBlock.FACING);

        // 0. Ambient temperature drift — 1°C toward ambient every 20 ticks
        if (be.temperature != AMBIENT_TEMPERATURE) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                if (be.temperature > AMBIENT_TEMPERATURE) be.temperature--;
                else be.temperature++;
                changed = true;
            }
        } else {
            be.heatLossTimer = 0;
        }

        // 1. Pull input fluid from front neighbor
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var src = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (src != null) changed |= tryPullFluid(src, be.inputFluidHandler);
        }

        // 2. Find / update recipe
        Optional<ElectrolysisRecipe> found = ElectrolysisRecipeManager.findRecipe(
                be.inputFluid, be.items.get(SLOT_ANODE), be.items.get(SLOT_CATHODE));
        if (found.isPresent()) {
            ElectrolysisRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe          = recipe;
                be.currentRecipeId        = recipe.getId();
                be.cookProgress           = 0;
                be.currentRecipeEnergy    = recipe.getEnergyRequired();
                be.requiredTemperature    = recipe.getRequiredTemperature();
                changed = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe          = null;
                be.currentRecipeId        = null;
                be.cookProgress           = 0;
                be.currentRecipeEnergy    = 0f;
                be.requiredTemperature    = 0;
                changed = true;
            }
        }

        // 3. Process: consume EU and advance progress
        if (be.currentRecipe != null && be.canProcess()) {
            float euPerTick = be.currentRecipeEnergy / COOK_TIME;
            if (be.energyStored >= euPerTick) {
                be.energyStored -= euPerTick;
                if (be.energyStored < 0f) be.energyStored = 0f;
                be.cookProgress++;
                changed = true;
                if (be.cookProgress >= COOK_TIME) {
                    be.process();
                    be.cookProgress = 0;
                }
            }
            // else: energy unavailable — progress pauses
        } else if (be.currentRecipe == null && be.cookProgress > 0) {
            be.cookProgress = 0;
            changed = true;
        }

        // 4. LIT state
        float euPerTick = be.currentRecipeEnergy > 0f ? be.currentRecipeEnergy / COOK_TIME : 1f;
        boolean shouldBeLit = be.currentRecipe != null && be.canProcess()
                && (be.cookProgress > 0 || be.energyStored >= euPerTick);
        if (state.getValue(ElectrolysisMachineBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(ElectrolysisMachineBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 5. Push output fluids to neighbors
        if (!be.anodeFluid.isEmpty()) {
            Direction dir = facing.getCounterClockWise();
            var nb = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(dir), dir.getOpposite());
            if (nb != null) changed |= tryPushFluid(be.anodeFluidHandler, nb);
        }
        if (!be.cathodeFluid.isEmpty()) {
            Direction dir = facing.getClockWise();
            var nb = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(dir), dir.getOpposite());
            if (nb != null) changed |= tryPushFluid(be.cathodeFluidHandler, nb);
        }
        if (!be.solutionFluid.isEmpty()) {
            Direction dir = facing.getOpposite();
            var nb = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(dir), dir.getOpposite());
            if (nb != null) changed |= tryPushFluid(be.solutionFluidHandler, nb);
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing helpers ────────────────────────────────────────────────────

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (!currentRecipe.matches(inputFluid, items.get(SLOT_ANODE), items.get(SLOT_CATHODE))) return false;
        if (currentRecipe.getRequiredTemperature() > 0 && temperature < currentRecipe.getRequiredTemperature()) return false;
        if (!hasOutputSpace(anodeFluid,    currentRecipe.getOutputAnode()))    return false;
        if (!hasOutputSpace(cathodeFluid,  currentRecipe.getOutputCathode()))  return false;
        if (!hasOutputSpace(solutionFluid, currentRecipe.getOutputSolution())) return false;
        return true;
    }

    private static boolean hasOutputSpace(FluidStack tank, FluidStack output) {
        if (output.isEmpty()) return true;
        if (tank.isEmpty())   return true;
        if (!tank.is(output.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - tank.getAmount()) >= output.getAmount();
    }

    private void process() {
        // Consume input fluid
        inputFluid.shrink(currentRecipe.getInputFluidAmount());
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        // Add anode output
        FluidStack outAnode = currentRecipe.getOutputAnode();
        if (!outAnode.isEmpty()) {
            if (anodeFluid.isEmpty()) anodeFluid = outAnode.copy();
            else anodeFluid.grow(outAnode.getAmount());
        }

        // Add cathode output
        FluidStack outCathode = currentRecipe.getOutputCathode();
        if (!outCathode.isEmpty()) {
            if (cathodeFluid.isEmpty()) cathodeFluid = outCathode.copy();
            else cathodeFluid.grow(outCathode.getAmount());
        }

        // Add solution output
        FluidStack outSolution = currentRecipe.getOutputSolution();
        if (!outSolution.isEmpty()) {
            if (solutionFluid.isEmpty()) solutionFluid = outSolution.copy();
            else solutionFluid.grow(outSolution.getAmount());
        }

        // Damage electrode items
        damageItem(SLOT_ANODE,   currentRecipe.getAnodeDamage());
        damageItem(SLOT_CATHODE, currentRecipe.getCathodeDamage());

        setChanged();
    }

    private void damageItem(int slot, int damage) {
        if (damage <= 0) return;
        ItemStack stack = items.get(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        int newDmg = stack.getDamageValue() + damage;
        if (newDmg >= stack.getMaxDamage()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            stack.setDamageValue(newDmg);
        }
    }

    // ── Fluid transfer utilities ──────────────────────────────────────────────

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from,
            ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty()) {
                    int avail    = Math.min(1000, (int) from.getAmountAsLong(i));
                    int accepted = to.insert(res, avail, tx);
                    if (accepted > 0) {
                        from.extract(res, accepted, tx);
                        tx.commit();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
            ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (res.isEmpty()) return false;
            int avail    = Math.min(1000, (int) from.getAmountAsLong(0));
            int accepted = to.insert(res, avail, tx);
            if (accepted > 0) {
                from.extract(res, accepted, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

    // ── Fluid accessors (for GUI) ─────────────────────────────────────────────

    public FluidStack getInputFluid()    { return inputFluid;    }
    public FluidStack getAnodeFluid()    { return anodeFluid;    }
    public FluidStack getCathodeFluid()  { return cathodeFluid;  }
    public FluidStack getSolutionFluid() { return solutionFluid; }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] ALL_SLOTS = { SLOT_ANODE, SLOT_CATHODE };

    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return true; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return true; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return false; }

    // ── Fluid tank inner classes ──────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { inputFluid = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int i)                  { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || (!inputFluid.isEmpty() && !res.matches(inputFluid))) return 0;
            int toFill = Math.min(amt, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = inputFluid.isEmpty()
                    ? res.toStack(toFill)
                    : inputFluid.copyWithAmount(inputFluid.getAmount() + toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    private class AnodeOutputHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return anodeFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { anodeFluid = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return anodeFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(anodeFluid); }
        @Override public long getAmountAsLong(int i)                  { return anodeFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return false; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) {
            if (anodeFluid.isEmpty() || !res.matches(anodeFluid)) return 0;
            int toExt = Math.min(amt, anodeFluid.getAmount());
            updateSnapshots(tx);
            anodeFluid = anodeFluid.copyWithAmount(anodeFluid.getAmount() - toExt);
            if (anodeFluid.getAmount() <= 0) anodeFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    private class CathodeOutputHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return cathodeFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { cathodeFluid = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return cathodeFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(cathodeFluid); }
        @Override public long getAmountAsLong(int i)                  { return cathodeFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return false; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) {
            if (cathodeFluid.isEmpty() || !res.matches(cathodeFluid)) return 0;
            int toExt = Math.min(amt, cathodeFluid.getAmount());
            updateSnapshots(tx);
            cathodeFluid = cathodeFluid.copyWithAmount(cathodeFluid.getAmount() - toExt);
            if (cathodeFluid.getAmount() <= 0) cathodeFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    private class SolutionOutputHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return solutionFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { solutionFluid = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return solutionFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(solutionFluid); }
        @Override public long getAmountAsLong(int i)                  { return solutionFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return false; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) {
            if (solutionFluid.isEmpty() || !res.matches(solutionFluid)) return 0;
            int toExt = Math.min(amt, solutionFluid.getAmount());
            updateSnapshots(tx);
            solutionFluid = solutionFluid.copyWithAmount(solutionFluid.getAmount() - toExt);
            if (solutionFluid.getAmount() <= 0) solutionFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid    = input.read("InputFluid",    FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        anodeFluid    = input.read("AnodeFluid",    FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        cathodeFluid  = input.read("CathodeFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        solutionFluid = input.read("SolutionFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        energyStored        = input.getFloatOr("EnergyStored",        0f);
        cookProgress        = input.getIntOr("CookProgress",           0);
        currentRecipeEnergy = input.getFloatOr("CurrentRecipeEnergy",  0f);
        temperature         = input.getIntOr("Temperature",            0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid",    FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("AnodeFluid",    FluidStack.OPTIONAL_CODEC, anodeFluid);
        output.store("CathodeFluid",  FluidStack.OPTIONAL_CODEC, cathodeFluid);
        output.store("SolutionFluid", FluidStack.OPTIONAL_CODEC, solutionFluid);
        output.putFloat("EnergyStored",        energyStored);
        output.putInt(  "CookProgress",        cookProgress);
        output.putFloat("CurrentRecipeEnergy", currentRecipeEnergy);
        output.putInt(  "Temperature",         temperature);
    }
}
