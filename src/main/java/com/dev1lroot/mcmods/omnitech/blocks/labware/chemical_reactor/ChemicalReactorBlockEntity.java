/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.gui.ChemicalReactorMenu;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalReactorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalReactorRecipeManager;
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
 * Block entity for the Chemical Reactor.
 *
 * <h3>Fluid I/O</h3>
 * <ul>
 *   <li>Front  (FACING):                      pulls input fluid 1 or 2.</li>
 *   <li>Left   (FACING.getCounterClockWise()): pulls input fluid 1 or 2.</li>
 *   <li>Right  (FACING.getClockWise()):        pulls input fluid 1 or 2.</li>
 *   <li>Back   (FACING.getOpposite()):         pushes output fluid.</li>
 * </ul>
 *
 * <h3>Catalyst</h3>
 * SLOT_CATALYST (slot 0) holds the catalyst item. If a recipe requires a catalyst
 * it must be present; each completed cycle deals {@code catalystDamage} damage to it.
 *
 * <h3>Temperature</h3>
 * {@code storedHeat} is managed by {@link IHeatReceiver}. Recipes specify a minimum
 * required temperature; heat decays toward ambient (15°C) over time.
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – storedHeat</li>
 *   <li>1 – requiredTemperature (from current recipe, or 0)</li>
 *   <li>2 – processTimer</li>
 *   <li>3 – processTotalTime</li>
 *   <li>4 – inputFluid1 amount</li>
 *   <li>5 – INPUT_TANK_CAPACITY</li>
 *   <li>6 – inputFluid2 amount</li>
 *   <li>7 – INPUT_TANK_CAPACITY</li>
 *   <li>8 – outputFluid amount</li>
 *   <li>9 – OUTPUT_TANK_CAPACITY</li>
 * </ul>
 */
public class ChemicalReactorBlockEntity extends BaseContainerBlockEntity
        implements IHeatReceiver, WorldlyContainer {

    public static final int SLOT_CATALYST         = 0;
    public static final int SLOT_COUNT            = 1;

    public static final int   INPUT_TANK_CAPACITY  = 8_000;
    public static final int   OUTPUT_TANK_CAPACITY = 8_000;
    public static final int   MAX_HEAT             = 1000;
    private static final int  AMBIENT_TEMPERATURE  = 15;
    private static final int  DECAY_INTERVAL       = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    FluidStack inputFluid1  = FluidStack.EMPTY;
    FluidStack inputFluid2  = FluidStack.EMPTY;
    FluidStack outputFluid  = FluidStack.EMPTY;

    int storedHeat    = 0;
    private int decayTimer    = 0;
    private int processTimer  = 0;
    private int processTotalTime = 100;

    private ChemicalReactorRecipe currentRecipe   = null;
    private String                currentRecipeId = null;

    // ── Fluid handlers ────────────────────────────────────────────────────────

    public final ResourceHandler<FluidResource> inputFluid1Handler = new InputTank1Handler();
    public final ResourceHandler<FluidResource> inputFluid2Handler = new InputTank2Handler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    /**
     * Combined handler exposed on all three input faces.
     * Inserts try tank 1 first, then tank 2.
     */
    public final ResourceHandler<FluidResource> anyInputHandler = new ResourceHandler<>() {
        @Override public int size() { return 2; }
        @Override public FluidResource getResource(int i) {
            return i == 0 ? inputFluid1Handler.getResource(0) : inputFluid2Handler.getResource(0);
        }
        @Override public long getAmountAsLong(int i) {
            return i == 0 ? inputFluid1Handler.getAmountAsLong(0) : inputFluid2Handler.getAmountAsLong(0);
        }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r) { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (i == 0) return inputFluid1Handler.insert(0, res, amt, tx);
            return inputFluid2Handler.insert(0, res, amt, tx);
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    };

    // ── ContainerData ─────────────────────────────────────────────────────────

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> storedHeat;
                case 1 -> currentRecipe != null ? currentRecipe.getRequiredTemperature() : 0;
                case 2 -> processTimer;
                case 3 -> processTotalTime;
                case 4 -> inputFluid1.getAmount();
                case 5 -> INPUT_TANK_CAPACITY;
                case 6 -> inputFluid2.getAmount();
                case 7 -> INPUT_TANK_CAPACITY;
                case 8 -> outputFluid.getAmount();
                case 9 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> storedHeat      = value;
                case 2 -> processTimer    = value;
                case 3 -> processTotalTime = value;
            }
        }
        @Override public int getCount() { return 10; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CHEMICAL_REACTOR.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.chemical_reactor");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ChemicalReactorMenu(containerId, inv, this, dataAccess);
    }

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - storedHeat);
        storedHeat += absorbed;
        setChanged();
        return absorbed;
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

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

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ChemicalReactorBlockEntity be) {
        boolean dirty = false;
        Direction facing = state.getValue(ChemicalReactorBlock.FACING);

        // 1. Pull input fluids from front, left, and right neighbors
        Direction[] inputFaces = {
            facing,
            facing.getCounterClockWise(),
            facing.getClockWise()
        };
        for (Direction face : inputFaces) {
            if (be.inputFluid1.getAmount() < INPUT_TANK_CAPACITY
                    || be.inputFluid2.getAmount() < INPUT_TANK_CAPACITY) {
                var src = level.getCapability(Capabilities.Fluid.BLOCK,
                        pos.relative(face), face.getOpposite());
                if (src != null) dirty |= tryPullFluid(src, be.anyInputHandler);
            }
        }

        // 2. Recipe matching
        Optional<ChemicalReactorRecipe> found = ChemicalReactorRecipeManager.findRecipe(
                be.inputFluid1, be.inputFluid2, be.items.get(SLOT_CATALYST), be.storedHeat);
        if (found.isPresent()) {
            ChemicalReactorRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe      = recipe;
                be.currentRecipeId    = recipe.getId();
                be.processTotalTime   = recipe.getProductionTime();
                be.processTimer       = 0;
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

        // 3. Advance process timer
        boolean canRun = be.currentRecipe != null && be.canProcess();
        if (canRun) {
            be.processTimer++;
            dirty = true;
            if (be.processTimer >= be.processTotalTime) {
                be.process();
                dirty = true;
            }
        } else if (be.processTimer > 0) {
            be.processTimer = 0;
            dirty = true;
        }

        // 4. Heat decay toward ambient
        if (be.storedHeat != AMBIENT_TEMPERATURE) {
            be.decayTimer++;
            if (be.decayTimer >= DECAY_INTERVAL) {
                be.decayTimer = 0;
                if (be.storedHeat > AMBIENT_TEMPERATURE) be.storedHeat--;
                else be.storedHeat++;
                dirty = true;
            }
        } else {
            be.decayTimer = 0;
        }

        // 5. LIT blockstate
        if (state.getValue(ChemicalReactorBlock.LIT) != canRun) {
            level.setBlock(pos, state.setValue(ChemicalReactorBlock.LIT, canRun), 3);
            dirty = true;
        }

        // 6. Push output fluid to back neighbor
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var nb = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(back), back.getOpposite());
            if (nb != null) dirty |= tryPushFluid(be.outputFluidHandler, nb);
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (!currentRecipe.matches(inputFluid1, inputFluid2, items.get(SLOT_CATALYST), storedHeat))
            return false;
        return hasOutputSpace(outputFluid, currentRecipe.getOutput());
    }

    private static boolean hasOutputSpace(FluidStack tank, FluidStack output) {
        if (output.isEmpty()) return true;
        if (tank.isEmpty()) return true;
        if (!tank.is(output.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - tank.getAmount()) >= output.getAmount();
    }

    private void process() {
        if (currentRecipe == null) return;

        net.minecraft.world.level.material.Fluid f1 = currentRecipe.getInput1Fluid().getFluid();
        net.minecraft.world.level.material.Fluid f2 = currentRecipe.getInput2Fluid().getFluid();
        int amt1 = currentRecipe.getInput1Amount();
        int amt2 = currentRecipe.getInput2Amount();

        // Determine which tank holds which recipe fluid (order-independent)
        if (!inputFluid1.isEmpty() && inputFluid1.is(f1)) {
            shrinkTank1(amt1);
            shrinkTank2(amt2);
        } else {
            shrinkTank1(amt2);
            shrinkTank2(amt1);
        }

        // Add output
        FluidStack out = currentRecipe.getOutput();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) outputFluid = out.copy();
            else outputFluid.grow(out.getAmount());
        }

        // Damage catalyst
        damageItem(SLOT_CATALYST, currentRecipe.getCatalystDamage());

        processTimer = 0;
        setChanged();
    }

    private void shrinkTank1(int amount) {
        inputFluid1.shrink(amount);
        if (inputFluid1.getAmount() <= 0) inputFluid1 = FluidStack.EMPTY;
    }

    private void shrinkTank2(int amount) {
        inputFluid2.shrink(amount);
        if (inputFluid2.getAmount() <= 0) inputFluid2 = FluidStack.EMPTY;
    }

    private void damageItem(int slot, int damage) {
        if (damage <= 0) return;
        ItemStack stack = items.get(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        int newDmg = stack.getDamageValue() + damage;
        if (newDmg >= stack.getMaxDamage()) items.set(slot, ItemStack.EMPTY);
        else stack.setDamageValue(newDmg);
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

    public FluidStack getInputFluid1()  { return inputFluid1;  }
    public FluidStack getInputFluid2()  { return inputFluid2;  }
    public FluidStack getOutputFluid()  { return outputFluid;  }
    public int        getStoredHeat()   { return storedHeat;   }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] CATALYST_SLOT_ARRAY = { SLOT_CATALYST };

    @Override public int[] getSlotsForFace(Direction side) { return CATALYST_SLOT_ARRAY; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return index == SLOT_CATALYST; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_CATALYST; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return false; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid1      = input.read("InputFluid1",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        inputFluid2      = input.read("InputFluid2",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid      = input.read("OutputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        storedHeat       = input.getIntOr("StoredHeat",       0);
        decayTimer       = input.getIntOr("DecayTimer",       0);
        processTimer     = input.getIntOr("ProcessTimer",     0);
        processTotalTime = input.getIntOr("ProcessTotalTime", 100);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid1",  FluidStack.OPTIONAL_CODEC, inputFluid1);
        output.store("InputFluid2",  FluidStack.OPTIONAL_CODEC, inputFluid2);
        output.store("OutputFluid",  FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putInt("StoredHeat",       storedHeat);
        output.putInt("DecayTimer",       decayTimer);
        output.putInt("ProcessTimer",     processTimer);
        output.putInt("ProcessTotalTime", processTotalTime);
    }

    // ── Inner tank handlers ───────────────────────────────────────────────────

    private class InputTank1Handler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return inputFluid1.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { inputFluid1 = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return inputFluid1.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid1); }
        @Override public long getAmountAsLong(int i)                  { return inputFluid1.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || (!inputFluid1.isEmpty() && !res.matches(inputFluid1))) return 0;
            int toFill = Math.min(amt, INPUT_TANK_CAPACITY - inputFluid1.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid1 = inputFluid1.isEmpty()
                    ? res.toStack(toFill)
                    : inputFluid1.copyWithAmount(inputFluid1.getAmount() + toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    private class InputTank2Handler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return inputFluid2.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { inputFluid2 = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return inputFluid2.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid2); }
        @Override public long getAmountAsLong(int i)                  { return inputFluid2.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || (!inputFluid2.isEmpty() && !res.matches(inputFluid2))) return 0;
            int toFill = Math.min(amt, INPUT_TANK_CAPACITY - inputFluid2.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid2 = inputFluid2.isEmpty()
                    ? res.toStack(toFill)
                    : inputFluid2.copyWithAmount(inputFluid2.getAmount() + toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()              { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)      { outputFluid = s; }
        @Override public int size()                                   { return 1; }
        @Override public FluidResource getResource(int i)            { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int i)                  { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return false; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) {
            if (outputFluid.isEmpty() || !res.matches(outputFluid)) return 0;
            int toExt = Math.min(amt, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }
}
