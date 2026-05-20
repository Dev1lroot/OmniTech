/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.FluidFillerMenu;
import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
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

/**
 * Block entity for the Fluid Filler machine.
 *
 * <h3>Slot layout</h3>
 * <ul>
 *   <li>{@link #SLOT_INPUT_CANISTER} (0) — insert canister here (top face / hopper)</li>
 *   <li>{@link #SLOT_OUTPUT_CANISTER} (1) — processed canister exits here (bottom face)</li>
 * </ul>
 *
 * <h3>Fluid I/O</h3>
 * <ul>
 *   <li>Front ({@code FACING}): pull input fluid from pipe/tank.</li>
 *   <li>Back  ({@code FACING.opposite}): push output fluid to pipe/tank.</li>
 * </ul>
 *
 * <h3>Processing logic</h3>
 * <ul>
 *   <li>Empty canister + input fluid present → fill canister → move to output slot.</li>
 *   <li>Filled canister + output tank has space → drain canister → move empty canister to output slot.</li>
 * </ul>
 *
 * <h3>ContainerData layout</h3>
 * 0 – inputFluid amount | 1 – INPUT_TANK_CAPACITY |
 * 2 – outputFluid amount | 3 – OUTPUT_TANK_CAPACITY |
 * 4 – processTimer | 5 – PROCESS_TIME
 */
public class FluidFillerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    public static final int SLOT_INPUT_CANISTER  = 0;
    public static final int SLOT_OUTPUT_CANISTER = 1;
    public static final int SLOT_COUNT           = 2;

    public static final int INPUT_TANK_CAPACITY  = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;
    public static final int PROCESS_TIME         = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private FluidStack inputFluid  = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;
    private int processTimer = 0;

    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> inputFluid.getAmount();
                case 1 -> INPUT_TANK_CAPACITY;
                case 2 -> outputFluid.getAmount();
                case 3 -> OUTPUT_TANK_CAPACITY;
                case 4 -> processTimer;
                case 5 -> PROCESS_TIME;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 4) processTimer = value;
        }
        @Override public int getCount() { return 6; }
    };

    public FluidFillerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FLUID_FILLER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.fluid_filler");
    }

    @Override protected NonNullList<ItemStack> getItems()                        { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)              { this.items = items; }
    @Override public int getContainerSize()                                      { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new FluidFillerMenu(containerId, inv, this, dataAccess);
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

    // ── Fluid accessors (GUI) ─────────────────────────────────────────────────

    public FluidStack getInputFluid()  { return inputFluid;  }
    public FluidStack getOutputFluid() { return outputFluid; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            FluidFillerBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(FluidFillerBlock.FACING);

        // 1. Pull input fluid from front neighbour
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var src = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (src != null) changed |= FluidNetworkUtil.tryPullFluid(src, be.inputFluidHandler);
        }

        // 2. Process canister
        ItemStack inCanister  = be.items.get(SLOT_INPUT_CANISTER);
        ItemStack outCanister = be.items.get(SLOT_OUTPUT_CANISTER);

        if (!inCanister.isEmpty() && outCanister.isEmpty()) {
            boolean doFill  = FluidCanisterItem.isEmpty(inCanister)
                    && !be.inputFluid.isEmpty();
            boolean doDrain = !FluidCanisterItem.isEmpty(inCanister)
                    && canFitInOutput(be.outputFluid, FluidCanisterItem.getFluid(inCanister));

            if (doFill || doDrain) {
                be.processTimer++;
                changed = true;
                if (be.processTimer >= PROCESS_TIME) {
                    if (doFill) be.fill();
                    else        be.drain();
                    be.processTimer = 0;
                }
            } else {
                if (be.processTimer != 0) { be.processTimer = 0; changed = true; }
            }
        } else {
            if (be.processTimer != 0) { be.processTimer = 0; changed = true; }
        }

        // 3. LIT state
        boolean shouldBeLit = !be.items.get(SLOT_INPUT_CANISTER).isEmpty()
                && be.items.get(SLOT_OUTPUT_CANISTER).isEmpty()
                && be.processTimer > 0;
        if (state.getValue(FluidFillerBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(FluidFillerBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 4. Push output fluid to back neighbour
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var nb = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(back), back.getOpposite());
            if (nb != null) changed |= FluidNetworkUtil.tryPushFluid(be.outputFluidHandler, nb);
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing helpers ────────────────────────────────────────────────────

    /** Fills the input canister from the input fluid tank and moves it to the output slot. */
    private void fill() {
        ItemStack canister = items.get(SLOT_INPUT_CANISTER).copy();
        int fillAmount = Math.min(FluidCanisterItem.CAPACITY, inputFluid.getAmount());
        FluidCanisterItem.setFluid(canister, inputFluid.copyWithAmount(fillAmount));
        inputFluid.shrink(fillAmount);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;
        items.set(SLOT_INPUT_CANISTER, ItemStack.EMPTY);
        items.set(SLOT_OUTPUT_CANISTER, canister);
        setChanged();
    }

    /** Drains the canister's fluid into the output fluid tank and moves the empty canister to the output slot. */
    private void drain() {
        ItemStack canister = items.get(SLOT_INPUT_CANISTER).copy();
        FluidStack canisterFluid = FluidCanisterItem.getFluid(canister);
        int space = OUTPUT_TANK_CAPACITY - outputFluid.getAmount();
        int drained = Math.min(canisterFluid.getAmount(), space);
        if (outputFluid.isEmpty()) {
            outputFluid = canisterFluid.copyWithAmount(drained);
        } else {
            int existAmt = outputFluid.getAmount();
            int mixTemp  = (fluidTemp(outputFluid) * existAmt + fluidTemp(canisterFluid) * drained) / (existAmt + drained);
            int mixPres  = (fluidPressure(outputFluid) * existAmt + fluidPressure(canisterFluid) * drained) / (existAmt + drained);
            outputFluid.grow(drained);
            applyAttributes(outputFluid, mixTemp, mixPres);
        }
        FluidCanisterItem.setFluid(canister, FluidStack.EMPTY);
        items.set(SLOT_INPUT_CANISTER, ItemStack.EMPTY);
        items.set(SLOT_OUTPUT_CANISTER, canister);
        setChanged();
    }

    private static boolean canFitInOutput(FluidStack tank, FluidStack incoming) {
        if (incoming.isEmpty()) return false;
        if (tank.isEmpty()) return true;
        if (!tank.is(incoming.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - tank.getAmount()) >= incoming.getAmount();
    }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] SLOT_TOP    = { SLOT_INPUT_CANISTER };
    private static final int[] SLOT_BOTTOM = { SLOT_OUTPUT_CANISTER };
    private static final int[] SLOT_SIDES  = { SLOT_INPUT_CANISTER };

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP   -> SLOT_TOP;
            case DOWN -> SLOT_BOTTOM;
            default   -> SLOT_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) {
        return index == SLOT_INPUT_CANISTER && stack.getItem() instanceof FluidCanisterItem;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) {
        return index == SLOT_OUTPUT_CANISTER;
    }

    // ── Fluid tank inner classes ──────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()               { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)       { inputFluid = s; }
        @Override public int size()                                    { return 1; }
        @Override public FluidResource getResource(int i)             { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int i)                   { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)       { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || (!inputFluid.isEmpty() && !FluidStack.isSameFluid(inputFluid, res.toStack(1)))) return 0;
            int toFill = Math.min(amt, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, res, toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()               { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)       { outputFluid = s; }
        @Override public int size()                                    { return 1; }
        @Override public FluidResource getResource(int i)             { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int i)                   { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)       { return false; }
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

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid   = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid  = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        processTimer = input.getIntOr("ProcessTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putInt("ProcessTimer", processTimer);
    }
}
