/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.FermenterMenu;
import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipeManager.Dissolution;
import com.dev1lroot.mcmods.omnitech.recipes.FermentationRecipeManager.Microbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fermenter — a vessel holding one compound {@link Solution} (the same record a
 * {@link FlaskItem} carries) in which timed, data-driven reactions play out on their own.
 *
 * <h3>Inputs</h3>
 * <ul>
 *   <li>{@link #SLOT_INPUT}: an item listed in {@code machine_recipe/fermenter_dissolve/} is
 *       dissolved into the solution one at a time (flour → "flour" fluid, and so on); a
 *       filled {@link FlaskItem} / {@link PipetteItem} is poured in whole; and a bucket (or any
 *       fluid container) is emptied into it. Emptied containers land in {@link #SLOT_OUTPUT}.</li>
 *   <li>Fluids (a water bucket, a pipe, a pump) go in through {@link #fluidHandler} on any face and
 *       simply join the mixture.</li>
 * </ul>
 *
 * <h3>Reactions</h3>
 * Every tick each {@link FermentationRecipe} whose inputs and catalysts are all present in the
 * mixture advances its own timer; when the timer reaches the recipe's interval the reaction fires
 * (swapping a few mB of components for others) and the timer restarts. A recipe whose ingredients
 * run out has its timer reset. Several reactions therefore run side by side, at different speeds.
 *
 * <h3>Spontaneous life</h3>
 * While the mixture contains no live microbe, there's a small chance each tick that one of the
 * microbes listed in {@code machine_recipe/fermenter_microbes/} (weighted, and only if its
 * {@code requires} fluids are present) appears on its own. It may be the wanted yeast or a mold
 * that ruins the batch.
 *
 * <p>The fluid handler exposes the whole mixture as one resource (a {@code omnitech:solution} fluid
 * carrying every component's ratio and dissolved flag, or the plain fluid once only one dissolved
 * fluid is left), with the mixture's own temperature and pressure — so pipes, pumps, tanks and other
 * machines handle it like any fluid. Reaction clocks are not persisted; they restart after a reload.
 *
 * <h3>ContainerData layout</h3>
 * 0 – total mB, 1 – capacity, 2 – number of reactions currently running, 3 – mB of live microbes,
 * 4 – temperature (°C), 5 – pressure (kPa).
 */
public class FermenterBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    public static final int TANK_CAPACITY = 4_000;

    /** Ticks between dissolving one input item into the solution. */
    private static final int DISSOLVE_INTERVAL = 10;
    /** Each tick, a 1-in-N chance that a microbe appears while none is alive (≈ 20 s on average). */
    private static final int SPONTANEOUS_LIFE_ODDS = 400;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private Solution solution = Solution.EMPTY;
    /** Temperature (°C) and pressure (kPa) of the mixture as a whole. */
    private int temperature = SolutionFluids.AMBIENT_TEMP;
    private int pressure = SolutionFluids.AMBIENT_PRESSURE;
    private int coolingTimer = 0;

    // The mixture as a fluid resource, rebuilt only when something changed (pipes ask every tick).
    private Solution cachedFor = null;
    private int cachedTemp, cachedPressure;
    private FluidResource cachedResource = FluidResource.EMPTY;

    private final Map<String, Integer> reactionTimers = new HashMap<>();
    private int dissolveTimer = 0;
    private int activeReactions = 0;
    private int liveMicrobes = 0;

    public final ResourceHandler<FluidResource> fluidHandler = new SolutionHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> solution.totalAmount();
                case 1 -> TANK_CAPACITY;
                case 2 -> activeReactions;
                case 3 -> liveMicrobes;
                case 4 -> temperature;
                case 5 -> pressure;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 2 -> activeReactions = value;
                case 3 -> liveMicrobes = value;
                case 4 -> temperature = value;
                case 5 -> pressure = value;
            }
        }
        @Override public int getCount() { return 6; }
    };

    public FermenterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FERMENTER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.fermenter");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new FermenterMenu(containerId, inv, this, dataAccess);
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

    public Solution getSolution() { return solution; }
    public int getTemperature()   { return temperature; }
    public int getPressure()      { return pressure; }

    /**
     * Removes and returns a representative sample of up to {@code amount} mB, with every
     * component drawn in proportion to its share of the mixture (a 30 % sugar / 70 % ethanol
     * batch yields a 30/70 sample), so a flask or pipette captures what is really in the vessel.
     * Returns {@link Solution#EMPTY} if the vessel is empty or {@code canHold} rejects any of the
     * components that would be drawn — nothing is removed in that case.
     */
    public Solution drawSample(int amount) {
        if (amount <= 0 || solution.isEmpty()) return Solution.EMPTY;
        Solution wanted = solution.scaledTo(amount);

        Solution drawn = Solution.EMPTY;
        for (Solution.Part c : wanted.components()) {
            if (!FlaskItem.canHold(c.fluid())) return Solution.EMPTY;
            drawn = drawn.plus(c.fluid(), c.amount(), c.dissolved());
        }
        solution = solution.minus(drawn);
        if (!drawn.isEmpty()) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return drawn;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FermenterBlockEntity be) {
        Solution before = be.solution;
        int tempBefore = be.temperature, pressureBefore = be.pressure;
        ItemStack inputBefore = be.items.get(SLOT_INPUT).copy();
        ItemStack outputBefore = be.items.get(SLOT_OUTPUT).copy();

        be.tickInput();
        be.tickReactions();
        be.tickSpontaneousLife(level.getRandom());
        be.tickConditions();

        if (!be.solution.equals(before)
                || be.temperature != tempBefore || be.pressure != pressureBefore
                || !ItemStack.matches(inputBefore, be.items.get(SLOT_INPUT))
                || !ItemStack.matches(outputBefore, be.items.get(SLOT_OUTPUT))) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * The mixture slowly settles toward ambient temperature and pressure (1 unit per second), and an
     * empty vessel is simply ambient.
     */
    private void tickConditions() {
        if (solution.isEmpty()) {
            temperature = SolutionFluids.AMBIENT_TEMP;
            pressure = SolutionFluids.AMBIENT_PRESSURE;
            coolingTimer = 0;
            return;
        }
        if (temperature == SolutionFluids.AMBIENT_TEMP && pressure == SolutionFluids.AMBIENT_PRESSURE) {
            coolingTimer = 0;
            return;
        }
        if (++coolingTimer < 20) return;
        coolingTimer = 0;
        temperature += Integer.compare(SolutionFluids.AMBIENT_TEMP, temperature);
        pressure += Integer.compare(SolutionFluids.AMBIENT_PRESSURE, pressure);
    }

    /** Dissolves items / pours flasks from the input slot into the solution. */
    private void tickInput() {
        ItemStack in = items.get(SLOT_INPUT);
        if (in.isEmpty()) { dissolveTimer = 0; return; }
        if (++dissolveTimer < DISSOLVE_INTERVAL) return;
        dissolveTimer = 0;

        if (isSolutionContainer(in)) {
            // Flask and pipette share the same Solution component, so FlaskItem's accessors serve both.
            Solution carried = FlaskItem.getSolution(in);
            if (carried.isEmpty() || !items.get(SLOT_OUTPUT).isEmpty()) return;
            if (solution.totalAmount() + carried.totalAmount() > TANK_CAPACITY) return;
            temperature = SolutionFluids.blend(temperature, solution.totalAmount(),
                    SolutionFluids.temperatureOf(in), carried.totalAmount());
            pressure = SolutionFluids.blend(pressure, solution.totalAmount(),
                    SolutionFluids.pressureOf(in), carried.totalAmount());
            solution = solution.plus(carried);
            ItemStack empty = in.copy();
            FlaskItem.setSolution(empty, Solution.EMPTY);
            items.set(SLOT_INPUT, ItemStack.EMPTY);
            items.set(SLOT_OUTPUT, empty);
            return;
        }

        Dissolution rule = FermentationRecipeManager.findDissolution(in);
        if (rule != null) {
            Fluid fluid = rule.fluid().fluid();
            if (fluid == null || solution.totalAmount() + rule.fluid().amount() > TANK_CAPACITY) return;
            solution = solution.plus(fluid, rule.fluid().amount(), rule.fluid().dissolved());
            in.shrink(1);
            return;
        }

        unloadFluidContainer(in);
    }

    private static boolean isSolutionContainer(ItemStack stack) {
        return stack.getItem() instanceof FlaskItem || stack.getItem() instanceof PipetteItem;
    }

    /**
     * Empties a bucket (or any item exposing the fluid item capability) into the mixture and
     * returns the emptied container to the output slot. The item is emptied on a scratch one-slot
     * container inside a single transaction, so a full bucket becomes an empty bucket properly and
     * nothing happens at all unless the whole content fits and the emptied container has room in
     * {@link #SLOT_OUTPUT} — a bucket is all-or-nothing, so it waits for a full bucket's worth of room.
     */
    private void unloadFluidContainer(ItemStack in) {
        SimpleContainer scratch = new SimpleContainer(1);
        scratch.setItem(0, in.copyWithCount(1));
        var handler = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(scratch), 0)
                .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) return;

        try (var tx = Transaction.openRoot()) {
            boolean moved = false;
            for (int i = 0; i < handler.size(); i++) {
                FluidResource res = handler.getResource(i);
                int amount = handler.getAmountAsInt(i);
                if (res.isEmpty() || amount <= 0) continue;
                int inserted = fluidHandler.insert(res, amount, tx);
                if (inserted <= 0) continue;
                // A bucket can't be drained partially; if it refuses, leave the whole transfer uncommitted.
                if (handler.extract(i, res, inserted, tx) != inserted) return;
                moved = true;
            }
            if (!moved) return;

            ItemStack result = scratch.getItem(0);
            if (!canOutput(result)) return;
            tx.commit();

            in.shrink(1);
            ItemStack out = items.get(SLOT_OUTPUT);
            if (result.isEmpty()) return;
            if (out.isEmpty()) items.set(SLOT_OUTPUT, result);
            else out.grow(result.getCount());
        }
    }

    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) return true;
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** Advances every recipe whose ingredients are present; fires the ones whose clock ran out. */
    private void tickReactions() {
        int running = 0;
        if (!solution.isEmpty()) {
            for (FermentationRecipe recipe : FermentationRecipeManager.getAllRecipes()) {
                if (!recipe.matches(solution)) {
                    reactionTimers.remove(recipe.getId());
                    continue;
                }
                if (!hasRoomFor(recipe)) continue;   // ingredients are there, the vessel/slot is full: hold
                running++;
                int elapsed = reactionTimers.merge(recipe.getId(), 1, Integer::sum);
                if (elapsed >= recipe.getInterval()) {
                    react(recipe);
                    reactionTimers.remove(recipe.getId());
                }
            }
        } else {
            reactionTimers.clear();
        }
        activeReactions = running;
        liveMicrobes = countLiveMicrobes();
    }

    private boolean hasRoomFor(FermentationRecipe recipe) {
        if (solution.totalAmount() + recipe.netVolume() > TANK_CAPACITY) return false;
        ItemStack out = recipe.getOutputItemStack();
        if (out.isEmpty()) return true;
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(slot, out)
                && slot.getCount() + out.getCount() <= slot.getMaxStackSize();
    }

    private void react(FermentationRecipe recipe) {
        solution = recipe.apply(solution);
        ItemStack out = recipe.getOutputItemStack();
        if (out.isEmpty()) return;
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) items.set(SLOT_OUTPUT, out);
        else slot.grow(out.getCount());
    }

    private int countLiveMicrobes() {
        int live = 0;
        for (Solution.Part c : solution.components()) {
            if (FermentationRecipeManager.isLiveMicrobe(c.fluid())) live += c.amount();
        }
        return live;
    }

    /** With no live culture, occasionally lets a weighted-random eligible microbe appear on its own. */
    private void tickSpontaneousLife(RandomSource random) {
        if (solution.isEmpty() || liveMicrobes > 0) return;
        if (random.nextInt(SPONTANEOUS_LIFE_ODDS) != 0) return;

        List<Microbe> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (Microbe m : FermentationRecipeManager.getMicrobes()) {
            Fluid fluid = m.fluid().fluid();
            if (fluid == null || m.weight() <= 0) continue;
            if (solution.totalAmount() + m.fluid().amount() > TANK_CAPACITY) continue;
            if (!hasAll(m.requires())) continue;
            eligible.add(m);
            totalWeight += m.weight();
        }
        if (eligible.isEmpty()) return;

        int roll = random.nextInt(totalWeight);
        for (Microbe m : eligible) {
            roll -= m.weight();
            if (roll < 0) {
                solution = solution.plus(m.fluid().fluid(), m.fluid().amount(), m.fluid().dissolved());
                liveMicrobes = m.fluid().amount();
                return;
            }
        }
    }

    private boolean hasAll(List<Identifier> ids) {
        for (var id : ids) {
            Fluid f = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
            if (f == null || solution.amountOf(f) <= 0) return false;
        }
        return true;
    }

    // ── Fluid handler: the whole mixture is one resource ────────────────────────

    /** Everything the handler can roll back: contents plus the mixture's temperature and pressure. */
    private record Snapshot(Solution solution, int temperature, int pressure) {}

    /** The mixture as a fluid resource — a plain fluid if only one dissolved fluid is left, else a {@code solution}. */
    private FluidResource currentResource() {
        if (cachedFor != solution || cachedTemp != temperature || cachedPressure != pressure) {
            FluidStack stack = SolutionFluids.toStack(solution, temperature, pressure);
            cachedResource = stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
            cachedFor = solution;
            cachedTemp = temperature;
            cachedPressure = pressure;
        }
        return cachedResource;
    }

    /**
     * One slot holding the whole mixture, so a pipe, pump or machine pulls it out — and pours
     * it in — exactly as it would any fluid, composition and all. Pouring in merges every
     * component with its dissolved flag and blends temperature / pressure by volume; drawing out
     * takes every component in proportion to its share.
     */
    private class SolutionHandler extends SnapshotJournal<Snapshot> implements ResourceHandler<FluidResource> {
        @Override protected Snapshot createSnapshot() { return new Snapshot(solution, temperature, pressure); }
        @Override protected void revertToSnapshot(Snapshot s) {
            solution = s.solution(); temperature = s.temperature(); pressure = s.pressure();
        }

        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return index == 0 ? currentResource() : FluidResource.EMPTY; }
        @Override public long getAmountAsLong(int index) { return index == 0 ? solution.totalAmount() : 0; }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return index == 0 ? TANK_CAPACITY : 0; }
        @Override public boolean isValid(int index, FluidResource resource) { return index == 0; }

        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || resource.isEmpty() || amount <= 0) return 0;
            int current = solution.totalAmount();
            int toFill = Math.min(amount, TANK_CAPACITY - current);
            if (toFill <= 0) return 0;

            FluidStack incoming = resource.toStack(toFill);
            Solution add = SolutionFluids.toSolution(incoming);
            if (add.isEmpty()) return 0;
            updateSnapshots(tx);
            temperature = SolutionFluids.blend(temperature, current, SolutionFluids.temperatureOf(incoming), toFill);
            pressure = SolutionFluids.blend(pressure, current, SolutionFluids.pressureOf(incoming), toFill);
            solution = solution.plus(add);
            return toFill;
        }

        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || amount <= 0 || solution.isEmpty()) return 0;
            if (!resource.equals(currentResource())) return 0;
            Solution take = solution.scaledTo(Math.min(amount, solution.totalAmount()));
            if (take.isEmpty()) return 0;
            updateSnapshots(tx);
            solution = solution.minus(take);
            return take.totalAmount();
        }
    }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] ALL_SLOTS = { SLOT_INPUT, SLOT_OUTPUT };
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index != SLOT_INPUT) return false;
        if (stack.isEmpty()) return false;
        if (isSolutionContainer(stack)) return !FlaskItem.isEmpty(stack);
        if (FermentationRecipeManager.findDissolution(stack) != null) return true;
        // Buckets and other fluid containers that actually hold something.
        return !FluidUtil.getFirstStackContained(stack).isEmpty();
    }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) {
        return canPlaceItem(index, stack);
    }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) {
        return index == SLOT_OUTPUT;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        solution = input.read("Solution", Solution.CODEC).orElse(Solution.EMPTY);
        temperature = input.getIntOr("Temperature", SolutionFluids.AMBIENT_TEMP);
        pressure = input.getIntOr("Pressure", SolutionFluids.AMBIENT_PRESSURE);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Solution", Solution.CODEC, solution);
        output.putInt("Temperature", temperature);
        output.putInt("Pressure", pressure);
    }
}
