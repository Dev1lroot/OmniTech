/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.FilterPressMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.items.MixtureDustItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.recipes.FilterPressRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FilterPressRecipeManager;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;


/**
 * Filter Press — the inverse of the {@link SolvationMachineBlockEntity Solvation Machine}: pulls
 * in a fluid that still carries undissolved solids and, once given enough kinetic force, strains
 * it back apart into the clean solution and the solid residue caught by the filter.
 *
 * <p>A plain fluid is pressed by its {@link FilterPressRecipe}. A <em>mixture</em> needs no recipe:
 * every undissolved solid in it is caught and pressed into Mixture Dust (to be separated in a
 * Manual Centrifuge), and the dissolved rest runs on as filtrate.
 *
 * <p>Orientation matches Solvation: {@code FACING} is the fluid-input face, the opposite face is
 * fluid-output. The filtered-out item collects in {@link #SLOT_OUTPUT_ITEM} for the player (or a
 * hopper on any side) to pull out.
 */
public class FilterPressBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, WorldlyContainer {

    public static final int SLOT_OUTPUT_ITEM = 0;
    public static final int SLOT_COUNT = 1;
    public static final int INPUT_TANK_CAPACITY = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack inputFluid = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce = 0f;
    private float requiredKineticForce = 0f;
    private FilterPressRecipe currentRecipe = null;
    private String currentRecipeId = null;

    /** Kinetic force per batch when straining a mixture (no recipe involved). */
    public static final float STRAIN_KINETIC_FORCE = 10f;
    /** mB of mixture one straining pass handles. */
    public static final int STRAIN_BATCH = 250;
    private static final String STRAIN_ID = "#strain";
    /** True while the input is a mixture, which is strained generically instead of by recipe. */
    private boolean straining = false;
    /** Solids caught from mixtures, waiting to fill a whole Mixture Dust. */
    private Solution dustBuffer = Solution.EMPTY;

    public final ResourceHandler<FluidResource> inputFluidHandler = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) (kineticForce * 100f);
                case 1 -> (int) (requiredKineticForce * 100f);
                case 2 -> inputFluid.getAmount();
                case 3 -> INPUT_TANK_CAPACITY;
                case 4 -> outputFluid.getAmount();
                case 5 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce = value / 100f;
                case 1 -> requiredKineticForce = value / 100f;
            }
        }
        @Override public int getCount() { return 6; }
    };

    public FilterPressBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FILTER_PRESS.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.filter_press");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new FilterPressMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    /** True while there's something to press: a plain-fluid recipe, or any mixture to strain. */
    private boolean hasWork() {
        return (currentRecipe != null || straining) && canProcess();
    }

    @Override
    public float getKfDemand() {
        return hasWork() ? 0.1f : 0f;
    }

    @Override
    public boolean addKineticForce(float amount) {
        if (!hasWork()) return false;
        kineticForce += amount;
        setChanged();
        if (kineticForce >= requiredKineticForce) {
            process();
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FilterPressBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(FilterPressBlock.FACING);

        // 1. Pull the unfiltered solution in
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var inputSource = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(facing), facing.getOpposite());
            if (inputSource != null) {
                changed |= FluidNetworkUtil.tryPullFluid(inputSource, be.inputFluidHandler);
            }
        }

        // 2. Recipe tracking: a mixture is always strained; a plain fluid needs a recipe
        boolean strain = SolutionFluids.isMixture(be.inputFluid);
        FilterPressRecipe recipe = strain ? null : FilterPressRecipeManager.findRecipe(be.inputFluid).orElse(null);
        String recipeId = strain ? STRAIN_ID : recipe != null ? recipe.getId() : null;
        if (!java.util.Objects.equals(recipeId, be.currentRecipeId)) {
            be.straining = strain;
            be.currentRecipe = recipe;
            be.currentRecipeId = recipeId;
            be.kineticForce = 0f;
            be.requiredKineticForce = strain ? STRAIN_KINETIC_FORCE : recipe != null ? recipe.getRequiredKineticForce() : 0f;
            changed = true;
        }

        // 3. Press any caught solids that are waiting for room in the output slot
        changed |= be.flushDust();

        // 4. LIT state
        boolean shouldBeLit = be.hasWork();
        if (state.getValue(FilterPressBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(FilterPressBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 5. Push the clean filtrate out
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(back), back.getOpposite());
            if (neighbor != null) {
                changed |= FluidNetworkUtil.tryPushFluid(be.outputFluidHandler, neighbor);
            }
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean canProcess() {
        if (straining) {
            if (!SolutionFluids.isMixture(inputFluid)) return false;
            // Caught solids must have somewhere to go before more are caught
            if (dustBuffer.totalAmount() >= MixtureDustItem.UNIT) return false;
            int filtrate = liquidPart(strainBatch()).totalAmount();
            return filtrate == 0 || (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= filtrate;
        }

        if (currentRecipe == null || !currentRecipe.matches(inputFluid)) return false;

        FluidStack outFluid = currentRecipe.getOutputFluid();
        if (!outputFluid.isEmpty() && !outFluid.isEmpty()) {
            if (!outputFluid.is(outFluid.getFluid())) return false;
            if ((OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) < outFluid.getAmount()) return false;
        }

        ItemStack outItem = currentRecipe.getOutputItemStack();
        if (outItem.isEmpty()) return false;
        ItemStack slot = items.get(SLOT_OUTPUT_ITEM);
        if (slot.isEmpty()) return true;
        if (!slot.is(outItem.getItem())) return false;
        return slot.getCount() + outItem.getCount() <= slot.getMaxStackSize();
    }

    private void process() {
        if (straining) strain();
        else if (currentRecipe != null) pressRecipe();
        kineticForce = 0f;
        setChanged();
    }

    /**
     * One batch of a mixture through the filter: every undissolved solid in it is caught (and
     * pressed into Mixture Dust, {@value MixtureDustItem#UNIT} mB per item, once enough has
     * collected); everything dissolved runs through into the output tank as filtrate.
     */
    private void strain() {
        int temp = fluidTemp(inputFluid);
        int pressure = fluidPressure(inputFluid);
        Solution batch = strainBatch();
        Solution liquid = liquidPart(batch);

        inputFluid = SolutionFluids.toStack(SolutionFluids.toSolution(inputFluid).minus(batch), temp, pressure);
        dustBuffer = dustBuffer.plus(batch.minus(liquid));
        if (!liquid.isEmpty()) {
            FluidStack out = SolutionFluids.toStack(liquid, temp, pressure);
            outputFluid = FluidNetworkUtil.blendInto(outputFluid, FluidResource.of(out), out.getAmount());
        }
        flushDust();
    }

    /** The slice of the input mixture that one pass of the press handles. */
    private Solution strainBatch() {
        return SolutionFluids.toSolution(inputFluid).scaledTo(STRAIN_BATCH);
    }

    /** The dissolved components of {@code batch} — what passes through the filter. */
    private static Solution liquidPart(Solution batch) {
        Solution liquid = Solution.EMPTY;
        for (Solution.Part p : batch.components()) {
            if (p.dissolved()) liquid = liquid.plus(p.fluid(), p.amount(), true);
        }
        return liquid;
    }

    /**
     * Presses whole {@value MixtureDustItem#UNIT} mB portions of the caught solids into Mixture
     * Dust while the output slot takes them; a smaller remainder waits for the next batch.
     */
    private boolean flushDust() {
        boolean changed = false;
        while (dustBuffer.totalAmount() >= MixtureDustItem.UNIT) {
            Solution portion = dustBuffer.scaledTo(MixtureDustItem.UNIT);
            ItemStack dust = MixtureDustItem.of(portion);
            ItemStack slot = items.get(SLOT_OUTPUT_ITEM);
            if (!MixtureDustItem.canMerge(slot, dust)) break;
            items.set(SLOT_OUTPUT_ITEM, MixtureDustItem.merge(slot, dust));
            dustBuffer = dustBuffer.minus(portion);
            changed = true;
        }
        return changed;
    }

    private void pressRecipe() {
        inputFluid.shrink(currentRecipe.getInputFluidAmount());
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack outFluid = currentRecipe.getOutputFluid();
        if (!outFluid.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = outFluid.copy();
            } else {
                int existAmt = outputFluid.getAmount();
                int newAmt   = outFluid.getAmount();
                int mixTemp  = (fluidTemp(outputFluid) * existAmt + fluidTemp(outFluid) * newAmt) / (existAmt + newAmt);
                int mixPres  = (fluidPressure(outputFluid) * existAmt + fluidPressure(outFluid) * newAmt) / (existAmt + newAmt);
                outputFluid.grow(newAmt);
                applyAttributes(outputFluid, mixTemp, mixPres);
            }
        }

        ItemStack outItem = currentRecipe.getOutputItemStack();
        ItemStack slot = items.get(SLOT_OUTPUT_ITEM);
        if (slot.isEmpty()) items.set(SLOT_OUTPUT_ITEM, outItem);
        else slot.grow(outItem.getCount());
    }

    // ── Fluid attribute helpers ───────────────────────────────────────────────

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

    private static void applyAttributes(FluidStack fs, int temp, int pressure) {
        if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        else            fs.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressure != 101) fs.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
        else                 fs.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    // ── Fluid tank inner classes ──────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int index) { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return true; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || (!inputFluid.isEmpty() && !resource.is(inputFluid.getFluid()))) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, resource, toFill);
            return toFill;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int index) { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return false; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    public FluidStack getInputFluid()  { return this.inputFluid; }
    public FluidStack getOutputFluid() { return this.outputFluid; }

    // ── WorldlyContainer — item slot is output-only ─────────────────────────────

    private static final int[] ALL_SLOTS = { SLOT_OUTPUT_ITEM };
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return false; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return false; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return index == SLOT_OUTPUT_ITEM; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid = input.read("InputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
        dustBuffer = input.read("DustBuffer", Solution.CODEC).orElse(Solution.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid", FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce", kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
        output.store("DustBuffer", Solution.CODEC, dustBuffer);
    }
}
