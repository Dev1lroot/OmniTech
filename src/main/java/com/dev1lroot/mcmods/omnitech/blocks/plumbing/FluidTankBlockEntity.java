/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.FluidTankMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;

/**
 * Block entity for {@link FluidTankBlock}.
 *
 * <p>Passive storage: holds up to {@link #CAPACITY} mb of a single fluid.
 * Fluid movement is driven by adjacent {@link PumpBlockEntity pumps}.
 * All six faces are equally accessible.
 *
 * <p>Exposes a {@link ResourceHandler}{@code <FluidResource>} via the
 * {@code Capabilities.Fluid.BLOCK} capability for pumps, buckets, and any
 * other NeoForge-compatible machinery.
 *
 * <p>Also has a GUI with two bucket slots: {@link #SLOT_BUCKET_IN} accepts
 * any filled bucket and drains it into the tank; {@link #SLOT_BUCKET_OUT}
 * receives the resulting empty bucket.
 */
public class FluidTankBlockEntity extends BaseContainerBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SLOT_BUCKET_IN  = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int SLOT_COUNT      = 2;

    /** Maximum fluid capacity (16 000 mb = 16 buckets). */
    public static final int CAPACITY = 16_000;

    FluidStack fluid = FluidStack.EMPTY;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** Capability-exposed handler with snapshot/rollback transaction support. */
    public final ResourceHandler<FluidResource> fluidHandler = new TankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> fluid.getAmount();
                case 1 -> CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}  // server→client only
        @Override public int getCount() { return 2; }
    };

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FLUID_TANK.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ───────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.fluid_tank");
    }

    @Override
    protected NonNullList<ItemStack> getItems()           { return items; }
    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override
    public int getContainerSize()                         { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new FluidTankMenu(containerId, inv, this, dataAccess);
    }

    // ── Fluid access for renderer ──────────────────────────────────────────────

    public FluidStack getFluid() { return fluid; }
    public void setFluid(FluidStack stack) { this.fluid = stack; }

    // ── Server tick — bucket → tank, gravity flow ─────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidTankBlockEntity be)
    {
        boolean dirty = false;

        // 1. Логика ведер (Оставляем как есть)
        ItemStack inStack = be.items.get(SLOT_BUCKET_IN);
        if (inStack.getItem() instanceof BucketItem bucketItem) {
            Fluid bucketFluid = bucketItem.getContent();
            if (bucketFluid != Fluids.EMPTY) {
                boolean compatible = be.fluid.isEmpty() || be.fluid.is(bucketFluid);
                int space = CAPACITY - be.fluid.getAmount();
                ItemStack outStack = be.items.get(SLOT_BUCKET_OUT);
                boolean canOutput = outStack.isEmpty() || (outStack.is(Items.BUCKET) && outStack.getCount() < outStack.getMaxStackSize());

                if (compatible && space >= 1000 && canOutput) {
                    if (be.fluid.isEmpty()) be.fluid = new FluidStack(bucketFluid, 1000);
                    else be.fluid.grow(1000);

                    inStack.shrink(1);
                    if (outStack.isEmpty()) be.items.set(SLOT_BUCKET_OUT, new ItemStack(Items.BUCKET));
                    else outStack.grow(1);
                    dirty = true;
                }
            }
        }

        // 2. Сетевая синхронизация (Теперь включает и гравитацию, и выравнивание)
        if (level.getGameTime() % 2 == 0) {
            FluidNetworkUtil.syncNetwork(level, pos);
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Inner ResourceHandler with SnapshotJournal ────────────────────────────

    private class TankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override
        protected FluidStack createSnapshot() {
            return fluid; // FluidStack is immutable — safe reference
        }

        @Override
        protected void onRootCommit(FluidStack originalState) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                // Update neighbors and send the sync packet to clients
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        protected void revertToSnapshot(FluidStack snapshot) {
            fluid = snapshot;
        }

        @Override
        public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return index == 0 && !fluid.isEmpty() ? FluidResource.of(fluid) : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return index == 0 ? fluid.getAmount() : 0L;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return index == 0 ? CAPACITY : 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0) return false;
            return fluid.isEmpty() || resource.matches(fluid);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || resource.isEmpty() || amount <= 0) return 0;
            if (!fluid.isEmpty() && !resource.matches(fluid)) return 0;
            int space = CAPACITY - fluid.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;

            // Weighted temperature blend before committing
            int existingAmount = fluid.getAmount();
            int existingTemp   = fluidTemp(fluid);
            int incomingTemp   = fluidTemp(resource.toStack(1));
            int blendedTemp    = existingAmount > 0
                    ? (existingTemp * existingAmount + incomingTemp * toInsert) / (existingAmount + toInsert)
                    : incomingTemp;

            updateSnapshots(tx);
            fluid = fluid.isEmpty() ? resource.toStack(toInsert)
                    : fluid.copyWithAmount(fluid.getAmount() + toInsert);

            if (blendedTemp != 20) fluid.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), blendedTemp);
            else                   fluid.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());

            return toInsert;
        }

        private static int fluidTemp(FluidStack stack) {
            if (stack.isEmpty()) return 20;
            Integer t = stack.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            return t != null ? t : 20;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || fluid.isEmpty() || amount <= 0) return 0;
            if (!resource.matches(fluid)) return 0;
            int toExtract = Math.min(amount, fluid.getAmount());
            if (toExtract <= 0) return 0;
            updateSnapshots(tx);
            fluid = fluid.copyWithAmount(fluid.getAmount() - toExtract);
            if (fluid.getAmount() <= 0) fluid = FluidStack.EMPTY;
            return toExtract;
        }
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.store("Fluid", FluidStack.OPTIONAL_CODEC, fluid);
            return output.buildResult();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items    = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        fluid = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, fluid);
    }
}
