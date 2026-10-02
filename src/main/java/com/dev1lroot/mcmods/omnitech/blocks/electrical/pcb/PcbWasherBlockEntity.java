/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.OmniTechSounds;
import com.dev1lroot.mcmods.omnitech.gui.PcbWasherMenu;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * PCB Washer: an acid bath. An exposed board from the PCB Burner soaks in an etchant from
 * {@code #omnitech:pcb_etchant} until the copper the developed resist left bare has
 * dissolved, leaving the printed circuit. Uses {@link #ACID_BASE} mB plus one per
 * {@link #CELLS_PER_ACID} board cells; needs no power.
 */
public class PcbWasherBlockEntity extends BaseContainerBlockEntity
        implements PcbStationBlock.Station, WorldlyContainer, com.dev1lroot.mcmods.omnitech.io.FluidFlushable {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    public static final int TANK_CAPACITY  = 8000;
    public static final int ACID_BASE      = 20;
    public static final int CELLS_PER_ACID = 4;
    public static final int ETCH_TICKS     = 140;

    public static final TagKey<Fluid> ETCHANT =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(OmniTech.MODID, "pcb_etchant"));

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack acid = FluidStack.EMPTY;
    private int progress;

    public final ResourceHandler<FluidResource> fluidHandler = new AcidTank();

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> progress;
                case 1 -> ETCH_TICKS;
                case 2 -> acid.getAmount();
                case 3 -> TANK_CAPACITY;
                case 4 -> acidPerBoard();
                case 5 -> acid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(acid.getFluid());
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 6; }
    };

    public PcbWasherBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PCB_WASHER.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("container.omnitech.pcb_washer"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new PcbWasherMenu(id, inv, this, data);
    }

    @Override
    public void writeOpenData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    public static boolean isEtchant(Fluid fluid) { return fluid.is(ETCHANT); }

    public static int acidPerBoard(PcbDesign d) {
        return ACID_BASE + (d.boardCells() + CELLS_PER_ACID - 1) / CELLS_PER_ACID;
    }

    private @Nullable PcbDesign design() {
        ItemStack in = items.get(SLOT_INPUT);
        return in.is(OmniTechItems.EXPOSED_CIRCUIT_BOARD.get()) ? in.get(OmniTechDataComponents.PCB_DESIGN.get()) : null;
    }

    public int acidPerBoard() {
        PcbDesign d = design();
        return d == null ? 0 : acidPerBoard(d);
    }

    // ── Process ───────────────────────────────────────────────────────────────

    @Override
    public void serverTick() {
        PcbDesign d = design();
        ItemStack result = d == null ? ItemStack.EMPTY : etched(items.get(SLOT_INPUT));
        if (d == null || !canOutput(result)) {
            if (progress != 0) { progress = 0; setChanged(); }
            return;
        }
        // the bath waits (keeping its progress) until there is enough acid
        if (acid.getAmount() < acidPerBoard(d)) return;
        if (++progress >= ETCH_TICKS) {
            progress = 0;
            acid.shrink(acidPerBoard(d));
            if (acid.isEmpty()) acid = FluidStack.EMPTY;
            items.get(SLOT_INPUT).shrink(1);
            ItemStack out = items.get(SLOT_OUTPUT);
            if (out.isEmpty()) items.set(SLOT_OUTPUT, result);
            else out.grow(1);
            if (level != null) level.playSound(null, worldPosition, OmniTechSounds.PCB_WASHER_FLUSH.get(), SoundSource.BLOCKS, 1f, 1f);
        }
        setChanged();
    }

    /** The etched board keeps the exposed one's drawing and reference placement. */
    private static ItemStack etched(ItemStack exposed) {
        ItemStack s = new ItemStack(OmniTechItems.PRINTED_CIRCUIT_BOARD.get());
        s.set(OmniTechDataComponents.PCB_DESIGN.get(), exposed.get(OmniTechDataComponents.PCB_DESIGN.get()));
        var parts = exposed.get(OmniTechDataComponents.PCB_PARTS.get());
        if (parts != null) s.set(OmniTechDataComponents.PCB_PARTS.get(), parts);
        return s;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() < out.getMaxStackSize();
    }

    /** One etchant at a time; a different acid only goes in once the tank is empty (or flushed). */
    private class AcidTank extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()           { return acid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)   { acid = s; }
        @Override public int size()                                { return 1; }
        @Override public FluidResource getResource(int i)         { return acid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(acid); }
        @Override public long getAmountAsLong(int i)               { return acid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)   { return isEtchant(r.getFluid()); }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || !isEtchant(res.getFluid())) return 0;
            if (!acid.isEmpty() && !res.matches(acid)) return 0;
            int fill = Math.min(amt, TANK_CAPACITY - acid.getAmount());
            if (fill <= 0) return 0;
            updateSnapshots(tx);
            if (acid.isEmpty()) acid = res.toStack(fill);
            else acid.grow(fill);
            setChanged();
            return fill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }

    // ── Slots ─────────────────────────────────────────────────────────────────

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index == SLOT_INPUT && stack.is(OmniTechItems.EXPOSED_CIRCUIT_BOARD.get());
    }

    private static final int[] SIDE_SLOTS = {SLOT_INPUT, SLOT_OUTPUT};

    @Override public int[] getSlotsForFace(Direction side) { return SIDE_SLOTS; }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return index == SLOT_OUTPUT;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        acid = input.read("Acid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        progress = input.getIntOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Acid", FluidStack.OPTIONAL_CODEC, acid);
        output.putInt("Progress", progress);
    }

    // ── Flush (GUI button) ────────────────────────────────────────────────────

    @Override
    public boolean flushTank(String tank) {
        boolean had = switch (tank) {
            case "acid" -> { boolean h = !acid.isEmpty(); acid = FluidStack.EMPTY; yield h; }
            default -> false;
        };
        if (had) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return had;
    }
}
