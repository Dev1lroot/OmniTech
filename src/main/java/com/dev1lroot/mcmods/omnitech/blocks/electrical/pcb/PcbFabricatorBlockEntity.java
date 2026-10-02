/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.gui.PcbFabricatorMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * PCB Fabricator: mass-produces bare boards from a blueprint. Each board is a
 * copper-clad blank coated in photoresist, exposed through the blueprint (the
 * photomask), developed and etched, leaving only the drawn copper.
 *
 * <p>Copper and resist scale with board area: one copper plate per
 * {@link #CELLS_PER_PLATE} board cells, {@link #RESIST_BASE} + {@link #RESIST_PER_CELL}
 * mB of photoresist per cell.
 */
public class PcbFabricatorBlockEntity extends BaseContainerBlockEntity
        implements PcbStationBlock.Station, IElectricReceiver, WorldlyContainer, com.dev1lroot.mcmods.omnitech.io.FluidFlushable {

    public static final int SLOT_MASK       = 0;
    public static final int SLOT_COPPER     = 1;
    public static final int SLOT_FLUID_IN   = 2;
    public static final int SLOT_FLUID_OUT  = 3;
    public static final int SLOT_OUTPUT     = 4;
    public static final int SLOT_COUNT      = 5;

    public static final int TANK_CAPACITY   = 8000;
    public static final int CELLS_PER_PLATE = 96;
    public static final int RESIST_BASE     = 25;
    public static final int RESIST_PER_CELL = 2;
    public static final int PROCESS_TICKS   = 160;
    public static final float MAX_EU        = 1000f;
    public static final float EU_PER_BOARD  = 640f;

    private static final Identifier PHOTORESIST = Identifier.fromNamespaceAndPath("omnitech", "photoresist");
    private static final Identifier COPPER_PLATE = Identifier.fromNamespaceAndPath("omnitech", "copper_plate");

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack resist = FluidStack.EMPTY;
    private float energy;
    private int progress;
    private final PowerMeter inputMeter = new PowerMeter();
    private final PowerMeter loadMeter = new PowerMeter();

    public final ResourceHandler<FluidResource> fluidHandler = new ResistTank();

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> (int) (energy * 10f);
                case 1 -> (int) (MAX_EU * 10f);
                case 2 -> progress;
                case 3 -> PROCESS_TICKS;
                case 4 -> (int) (EU_PER_BOARD * 10f);
                case 5 -> inputMeter.syncWatts();
                case 6 -> inputMeter.syncDeciVolts();
                case 7 -> loadMeter.syncWatts();
                case 8 -> resist.getAmount();
                case 9 -> TANK_CAPACITY;
                case 10 -> copperPerBoard();
                case 11 -> resistPerBoard();
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 12; }
    };

    public PcbFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PCB_FABRICATOR.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("container.omnitech.pcb_fabricator"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new PcbFabricatorMenu(id, inv, this, data);
    }

    @Override
    public void writeOpenData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    public static Fluid photoresist() { return BuiltInRegistries.FLUID.getValue(PHOTORESIST); }

    private static Item copperPlate() { return BuiltInRegistries.ITEM.getValue(COPPER_PLATE); }

    private @Nullable PcbDesign mask() {
        return items.get(SLOT_MASK).get(OmniTechDataComponents.PCB_DESIGN.get());
    }

    public int copperPerBoard() {
        PcbDesign d = mask();
        return d == null ? 0 : Math.max(1, (d.boardCells() + CELLS_PER_PLATE - 1) / CELLS_PER_PLATE);
    }

    public int resistPerBoard() {
        PcbDesign d = mask();
        return d == null ? 0 : RESIST_BASE + RESIST_PER_CELL * d.boardCells();
    }

    // ── Electricity ───────────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount, float volts) {
        float accepted = Math.max(0f, Math.min(amount, MAX_EU - energy));
        inputMeter.add(accepted, volts);
        if (accepted <= 0f) return 0f;
        energy += accepted;
        setChanged();
        return accepted;
    }

    // ── Process ───────────────────────────────────────────────────────────────

    @Override
    public void serverTick() {
        inputMeter.tick();
        loadMeter.tick();
        if (!items.get(SLOT_FLUID_IN).isEmpty()) unloadFluidContainer(items.get(SLOT_FLUID_IN));

        PcbDesign d = mask();
        ItemStack result = d == null ? ItemStack.EMPTY : board(d, items.get(SLOT_MASK).get(OmniTechDataComponents.PCB_PARTS.get()));
        boolean ready = d != null && d.padCount() > 0
                && items.get(SLOT_COPPER).is(copperPlate())
                && items.get(SLOT_COPPER).getCount() >= copperPerBoard()
                && resist.getAmount() >= resistPerBoard()
                && canOutput(result);
        if (!ready) {
            if (progress != 0) { progress = 0; setChanged(); }
            return;
        }
        float perTick = EU_PER_BOARD / PROCESS_TICKS;
        if (energy < perTick) return;
        energy -= perTick;
        loadMeter.add(perTick);
        if (++progress >= PROCESS_TICKS) {
            progress = 0;
            items.get(SLOT_COPPER).shrink(copperPerBoard());
            resist.shrink(resistPerBoard());
            ItemStack out = items.get(SLOT_OUTPUT);
            if (out.isEmpty()) items.set(SLOT_OUTPUT, result);
            else out.grow(1);
        }
        setChanged();
    }

    /** A bare board; a ready-made blueprint's reference placement rides along to the soldering station. */
    private static ItemStack board(PcbDesign d, java.util.@Nullable List<com.dev1lroot.mcmods.omnitech.pcb.PlacedPart> reference) {
        ItemStack s = new ItemStack(OmniTechItems.PRINTED_CIRCUIT_BOARD.get());
        s.set(OmniTechDataComponents.PCB_DESIGN.get(), d);
        if (reference != null) s.set(OmniTechDataComponents.PCB_PARTS.get(), reference);
        return s;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() < out.getMaxStackSize();
    }

    /** Empties a bucket / canister of photoresist into the tank (all-or-nothing). */
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
                if (handler.extract(i, res, inserted, tx) != inserted) return;
                moved = true;
            }
            if (!moved) return;
            ItemStack emptied = scratch.getItem(0);
            ItemStack out = items.get(SLOT_FLUID_OUT);
            if (!emptied.isEmpty() && !out.isEmpty()
                    && (!ItemStack.isSameItemSameComponents(out, emptied) || out.getCount() >= out.getMaxStackSize())) return;
            tx.commit();
            in.shrink(1);
            if (emptied.isEmpty()) return;
            if (out.isEmpty()) items.set(SLOT_FLUID_OUT, emptied);
            else out.grow(1);
        }
    }

    private class ResistTank extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()           { return resist.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)   { resist = s; }
        @Override public int size()                                { return 1; }
        @Override public FluidResource getResource(int i)         { return resist.isEmpty() ? FluidResource.EMPTY : FluidResource.of(resist); }
        @Override public long getAmountAsLong(int i)               { return resist.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)   { return r.is(photoresist()); }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || !res.is(photoresist())) return 0;
            int fill = Math.min(amt, TANK_CAPACITY - resist.getAmount());
            if (fill <= 0) return 0;
            updateSnapshots(tx);
            if (resist.isEmpty()) resist = new FluidStack(photoresist(), fill);
            else resist.grow(fill);
            setChanged();
            return fill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    // ── Slots ─────────────────────────────────────────────────────────────────

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return switch (index) {
            case SLOT_MASK     -> stack.is(OmniTechItems.PCB_BLUEPRINT.get());
            case SLOT_COPPER   -> stack.is(copperPlate());
            case SLOT_FLUID_IN -> true;
            default -> false;
        };
    }

    private static final int[] SIDE_SLOTS = {SLOT_COPPER, SLOT_FLUID_IN, SLOT_FLUID_OUT, SLOT_OUTPUT};

    @Override public int[] getSlotsForFace(Direction side) { return SIDE_SLOTS; }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction side) {
        return (index == SLOT_COPPER || index == SLOT_FLUID_IN) && canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return index == SLOT_OUTPUT || index == SLOT_FLUID_OUT;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        resist = input.read("Resist", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        energy = input.getFloatOr("Energy", 0f);
        progress = input.getIntOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Resist", FluidStack.OPTIONAL_CODEC, resist);
        output.putFloat("Energy", energy);
        output.putInt("Progress", progress);
    }

    // ── Flush (GUI button) ────────────────────────────────────────────────────

    @Override
    public boolean flushTank(String tank) {
        boolean had = switch (tank) {
            case "resist" -> { boolean h = !resist.isEmpty(); resist = FluidStack.EMPTY; yield h; }
            default -> false;
        };
        if (had) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return had;
    }
}
