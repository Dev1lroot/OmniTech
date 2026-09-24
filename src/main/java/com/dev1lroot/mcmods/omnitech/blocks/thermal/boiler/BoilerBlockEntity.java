/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.blocks.ThermalState;
import com.dev1lroot.mcmods.omnitech.gui.BoilerMenu;
import com.dev1lroot.mcmods.omnitech.items.MixtureDustItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.recipes.BoilingRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.BoilingRecipeManager;
import com.dev1lroot.mcmods.omnitech.util.FluidMixing;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import com.mojang.serialization.Codec;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A pressure-less still. Fluid comes in from below and the sides; every dissolved component of it
 * that is past its boiling point at the tank's pressure boils off and leaves through the top —
 * a mixture ({@code omnitech:solution}) boils component by component, so a solution gives off only
 * what is actually hot enough and keeps the rest. What a boiled-off fluid leaves behind (or turns
 * into) is data-driven, see {@link BoilingRecipe}; solids land in the single output slot.
 */
public class BoilerBlockEntity extends BaseContainerBlockEntity
        implements IHeatReceiver, IColdReceiver, WorldlyContainer {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_FLUID     = 8000;
    public static final int TRANSFER_RATE = 100;
    public static final int MIN_BOIL_HEAT = 100;
    public static final int MAX_HEAT      =  500;
    public static final int MIN_HEAT      = -500;

    /** One output slot for the solids a boil leaves behind. */
    public static final int SLOT_COUNT  = 1;
    public static final int SLOT_OUTPUT = 0;

    private static final int AMBIENT_TEMPERATURE = 15;
    private static final int DECAY_INTERVAL      = 20;

    private static final Codec<Map<String, Integer>> BOIL_PROGRESS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    // ── State ─────────────────────────────────────────────────────────────────

    private int storedHeat = 0;
    private int decayTimer = 0;
    private FluidStack fluidTank = FluidStack.EMPTY;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** mB boiled away so far towards the next cycle of each {@link BoilingRecipe}, by recipe id. */
    private final Map<String, Integer> boilProgress = new HashMap<>();

    /** Suspended solids already dried out of the tank, waiting to be pressed into mixture dust. */
    private Solution dustBuffer = Solution.EMPTY;

    // ── Fluid capability ──────────────────────────────────────────────────────

    public final ResourceHandler<FluidResource> fluidHandler = new InternalTank();

    // ── ContainerData (synced to GUI) ─────────────────────────────────────────
    // Index 0 = storedHeat, 1 = fluidAmount

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> storedHeat;
                case 1 -> fluidTank.getAmount();
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {
            if (i == 0) storedHeat = value;
        }
        @Override public int getCount() { return 2; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.BOILER.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.boiler");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new BoilerMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── WorldlyContainer: hoppers and pipes may only take the output ─────────

    private static final int[] SLOTS = { SLOT_OUTPUT };

    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return false; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return false; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return true; }

    // ── IHeatReceiver / IColdReceiver ─────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - storedHeat);
        storedHeat += absorbed;
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (storedHeat <= MIN_HEAT) return 0;
        int absorbed = Math.min(celsius, storedHeat - MIN_HEAT);
        storedHeat -= absorbed;
        return absorbed;
    }

    // ── Network sync ──────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            this.saveAdditional(output);
            return output.buildResult();
        }
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        boolean dirty = false;

        // 1. Pull fluid from below and sides only — never from above (would suck back vapour)
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue;
            if (be.fluidTank.getAmount() >= MAX_FLUID) break;
            ResourceHandler<FluidResource> neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());
            if (neighbor != null) {
                dirty |= FluidNetworkUtil.tryPullFluid(neighbor, be.fluidHandler,
                        be.fluidTank.isEmpty() ? null : be.fluidTank.getFluid(), TRANSFER_RATE);
            }
        }

        // 2. Stamp the boiler's heat onto the fluid's temperature component
        if (!be.fluidTank.isEmpty()) {
            Integer existing = be.fluidTank.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            if (existing == null || existing != be.storedHeat) {
                be.fluidTank.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), be.storedHeat);
                dirty = true;
            }
        }

        // 3. Boil off whatever is hot enough, out through the top; hand solids to the output slot
        dirty |= be.tickBoiling(level, pos);

        // 4. Ambient decay — storedHeat drifts toward AMBIENT_TEMPERATURE every DECAY_INTERVAL ticks
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

        // 5. Update LIT and THERMAL blockstates
        boolean isLit = !be.fluidTank.isEmpty() && be.storedHeat > MIN_BOIL_HEAT;
        ThermalState thermal = ThermalState.of(be.storedHeat);
        if (state.getValue(BoilerBlock.LIT) != isLit
                || state.getValue(BoilerBlock.THERMAL) != thermal) {
            level.setBlock(pos, state
                    .setValue(BoilerBlock.LIT, isLit)
                    .setValue(BoilerBlock.THERMAL, thermal), 3);
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Boiling ───────────────────────────────────────────────────────────────

    /**
     * Boils every dissolved component whose boiling point at the tank's pressure is at or below the
     * boiler's temperature — at most {@value #TRANSFER_RATE} mB per tick over all of them, in
     * proportion to what there is. A component only counts if its phase diagram says it is
     * gas-like; suspended solids and anything still cooler than its boiling point stay behind.
     * The vapour leaves upward as one fluid (or, with several components, as a mixture) and only as
     * much as the block above accepts leaves the tank.
     */
    private boolean tickBoiling(Level level, BlockPos pos) {
        boolean changed = flushResidue(level.getRandom());
        if (fluidTank.isEmpty()) return changed;

        int pressure = FluidNetworkUtil.fluidPressure(fluidTank);
        Solution mix = SolutionFluids.toSolution(fluidTank);

        // Suspended solids only leave the tank as dust; while the slot can't take it, stop.
        Solution solids = undissolvedOf(mix);
        boolean dustBlocked = dustBuffer.totalAmount() >= MixtureDustItem.UNIT;
        if (dustBlocked && !solids.isEmpty()) return changed;

        // Nothing liquid left to carry them: whatever is suspended is now a dry powder.
        if (SolutionFluids.isMixture(fluidTank) && !solids.isEmpty() && solids.totalAmount() == mix.totalAmount()) {
            dryOut(mix, solids.scaledTo(TRANSFER_RATE), pressure);
            flushResidue(level.getRandom());
            return true;
        }

        List<Solution.Part> boiling = new ArrayList<>();
        for (Solution.Part part : mix.components()) {
            if (!part.dissolved()) continue;
            var diagram = FluidPhysicsRegistry.get(part.fluid()).phaseDiagram();
            if (diagram == null) continue;
            FluidPhase phase = FluidPhaseUtil.getPhase(storedHeat, pressure, diagram);
            boolean gasLike = phase == FluidPhase.VAPOUR || phase == FluidPhase.GAS
                    || phase == FluidPhase.SUPERCRITICAL || phase == FluidPhase.PLASMA;
            if (!gasLike) continue;
            // Never boil something whose solids would have nowhere to go — they would be lost.
            BoilingRecipe recipe = BoilingRecipeManager.find(part.fluid());
            if (recipe != null && recipe.hasResult() && !canStore(recipe.resultStack())) continue;
            boiling.add(part);
        }
        if (boiling.isEmpty()) return changed;

        Solution boiled = new Solution(boiling).scaledTo(TRANSFER_RATE);

        // What actually leaves: the fluid itself, unless a recipe turns it into something else.
        Solution vapour = Solution.EMPTY;
        for (Solution.Part part : boiled.components()) {
            BoilingRecipe recipe = BoilingRecipeManager.find(part.fluid());
            if (recipe != null && recipe.convertsFluid()) {
                vapour = vapour.plus(recipe.getOutputFluid(), recipe.vapourFor(part.amount()), true);
            } else {
                vapour = vapour.plus(part.fluid(), part.amount(), true);
            }
        }

        Solution taken = boiled;
        if (!vapour.isEmpty()) {
            ResourceHandler<FluidResource> output = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.above(), Direction.DOWN);
            if (output == null) return changed;

            FluidStack stack = SolutionFluids.toStack(vapour, storedHeat, pressure);
            int accepted;
            try (var tx = Transaction.openRoot()) {
                accepted = output.insert(FluidResource.of(stack), stack.getAmount(), tx);
                if (accepted > 0) tx.commit();
            }
            if (accepted <= 0) return changed;
            if (accepted < stack.getAmount()) {
                taken = boiled.scaledTo(Math.max(1,
                        (int) ((long) boiled.totalAmount() * accepted / stack.getAmount())));
            }
        }
        // (a vapour that rounds to nothing — a sliver of a converted fluid — simply vanishes)

        if (SolutionFluids.isMixture(fluidTank)) {
            // The solids dry out in step with the liquid: boil off a share of it and the same
            // share of what was suspended in it is left behind as powder.
            int liquid = mix.totalAmount() - solids.totalAmount();
            int dried = liquid <= 0 ? 0 : (int) ((long) solids.totalAmount() * taken.totalAmount() / liquid);
            Solution dry = solids.isEmpty() ? Solution.EMPTY : solids.scaledTo(dried);
            fluidTank = SolutionFluids.toStack(mix.minus(taken).minus(dry), storedHeat, pressure);
            dustBuffer = dustBuffer.plus(dry);
        } else {
            int left = fluidTank.getAmount() - taken.totalAmount();
            fluidTank = left > 0 ? fluidTank.copyWithAmount(left) : FluidStack.EMPTY;
        }

        for (Solution.Part part : taken.components()) {
            BoilingRecipe recipe = BoilingRecipeManager.find(part.fluid());
            if (recipe != null && recipe.hasResult()) boilProgress.merge(recipe.getId(), part.amount(), Integer::sum);
        }
        flushResidue(level.getRandom());
        return true;
    }

    /** Turns completed boil cycles into items in the output slot; a cycle waits while it is full. */
    private boolean flushResidue(RandomSource random) {
        boolean changed = flushDust();
        Iterator<Map.Entry<String, Integer>> it = boilProgress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            BoilingRecipe recipe = BoilingRecipeManager.get(entry.getKey());
            if (recipe == null || !recipe.hasResult()) { it.remove(); changed = true; continue; }

            while (entry.getValue() >= recipe.getInputAmount() && canStore(recipe.resultStack())) {
                entry.setValue(entry.getValue() - recipe.getInputAmount());
                ItemStack rolled = recipe.rollResult(random);
                if (!rolled.isEmpty()) {
                    ItemStack slot = items.get(SLOT_OUTPUT);
                    if (slot.isEmpty()) items.set(SLOT_OUTPUT, rolled);
                    else slot.grow(rolled.getCount());
                }
                changed = true;
            }
        }
        return changed;
    }

    /** Moves {@code dry} out of the tank (the mixture {@code mix}) into the dust buffer. */
    private void dryOut(Solution mix, Solution dry, int pressure) {
        fluidTank  = SolutionFluids.toStack(mix.minus(dry), storedHeat, pressure);
        dustBuffer = dustBuffer.plus(dry);
    }

    /** The suspended (undissolved) components of {@code mix}. */
    private static Solution undissolvedOf(Solution mix) {
        List<Solution.Part> parts = new ArrayList<>();
        for (Solution.Part p : mix.components()) if (!p.dissolved()) parts.add(p);
        return new Solution(parts);
    }

    /**
     * Presses the dust buffer into mixture dust, {@value MixtureDustItem#UNIT} mB per item, plus a
     * final smaller batch once the tank has run dry. Waits while the output slot cannot take it.
     */
    private boolean flushDust() {
        boolean changed = false;
        while (!dustBuffer.isEmpty()) {
            boolean lastBatch = fluidTank.isEmpty() && dustBuffer.totalAmount() < MixtureDustItem.UNIT;
            if (dustBuffer.totalAmount() < MixtureDustItem.UNIT && !lastBatch) break;

            Solution portion = dustBuffer.scaledTo(MixtureDustItem.UNIT);
            ItemStack dust = MixtureDustItem.of(portion);
            ItemStack slot = items.get(SLOT_OUTPUT);
            if (!MixtureDustItem.canMerge(slot, dust)) break;

            // Piles of the same ingredients blend, so slightly different ratios don't jam the slot
            items.set(SLOT_OUTPUT, MixtureDustItem.merge(slot, dust));
            dustBuffer = dustBuffer.minus(portion);
            changed = true;
        }
        return changed;
    }

    /** True if {@code stack} fits in the output slot on top of what is already there. */
    private boolean canStore(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(slot, stack)
                && slot.getCount() + stack.getCount() <= slot.getMaxStackSize();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        storedHeat = input.getIntOr("StoredHeat", 0);
        decayTimer = input.getIntOr("DecayTimer", 0);
        boilProgress.clear();
        input.read("BoilProgress", BOIL_PROGRESS_CODEC).ifPresent(boilProgress::putAll);
        dustBuffer = input.read("DustBuffer", Solution.CODEC).orElse(Solution.EMPTY);
        fluidTank  = input.read("FluidTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("StoredHeat", storedHeat);
        output.putInt("DecayTimer", decayTimer);
        output.store("BoilProgress", BOIL_PROGRESS_CODEC, boilProgress);
        output.store("DustBuffer", Solution.CODEC, dustBuffer);
        output.store("FluidTank", FluidStack.OPTIONAL_CODEC, fluidTank);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getFluidTank()     { return fluidTank; }
    public int getStoredHeat()           { return storedHeat; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── InternalTank ──────────────────────────────────────────────────────────

    private class InternalTank extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()          { return fluidTank; }
        @Override protected void revertToSnapshot(FluidStack s)  { fluidTank = s; }

        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int i) {
            return fluidTank.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluidTank);
        }

        @Override public long getAmountAsLong(int i) { return fluidTank.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource res) { return MAX_FLUID; }
        @Override public boolean isValid(int i, FluidResource res) { return FluidMixing.canBlend(fluidTank, res); }

        @Override
        public int insert(int i, FluidResource res, int amount, TransactionContext tx) {
            if (res.isEmpty() || !FluidMixing.canBlend(fluidTank, res)) return 0;
            int space = MAX_FLUID - fluidTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            fluidTank = FluidNetworkUtil.blendInto(fluidTank, res, toInsert);
            return toInsert;
        }

        @Override
        public int extract(int i, FluidResource res, int amount, TransactionContext tx) {
            if (fluidTank.isEmpty() || !res.matches(fluidTank)) return 0;
            int toExt = Math.min(amount, fluidTank.getAmount());
            if (toExt <= 0) return 0;
            updateSnapshots(tx);
            fluidTank = fluidTank.copyWithAmount(fluidTank.getAmount() - toExt);
            if (fluidTank.getAmount() <= 0) fluidTank = FluidStack.EMPTY;
            return toExt;
        }
    }
}
