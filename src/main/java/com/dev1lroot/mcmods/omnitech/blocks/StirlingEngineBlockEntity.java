package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.KineticNetworkUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Block entity for the {@link StirlingEngineBlock}.
 */
public class StirlingEngineBlockEntity extends BaseContainerBlockEntity
        implements IHeatReceiver {

    public static final int SLOT_WATER_IN  = 0;
    public static final int SLOT_EMPTY_OUT = 1;
    public static final int SLOT_COUNT     = 2;

    public static final int MAX_HEAT                  = 300;
    public static final int MIN_RUNNING_HEAT          = 100;
    public static final int MAX_WATER                 = 16_000;
    public static final int WATER_CONSUMPTION_PER_TICK = 1;
    public static final int HEAT_LOSS_INTERVAL        = 10;
    public static final int KF_PER_TICK               = 1;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int storedHeat    = 0;
    private int heatLossTimer = 0;

    // Unified fluid storage
    private FluidStack fluid = FluidStack.EMPTY;
    public final ResourceHandler<FluidResource> fluidHandler = new TankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> storedHeat;
                case 1 -> MAX_HEAT;
                case 2 -> fluid.getAmount();
                case 3 -> MAX_WATER;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> storedHeat = value;
                // Fluid amount shouldn't be set directly via ContainerData usually
            }
        }
        @Override public int getCount() { return 4; }
    };

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.STIRLING_ENGINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.stirling_engine");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new StirlingEngineMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public boolean addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return false;
        storedHeat = Math.min(MAX_HEAT, storedHeat + celsius);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingEngineBlockEntity be) {
        boolean dirty = false;
        boolean wasRunning = be.isRunning();

        // ── Process Water Bucket ──
        ItemStack waterBucket = be.items.get(SLOT_WATER_IN);
        if (!waterBucket.isEmpty() && waterBucket.is(Items.WATER_BUCKET)) {
            int space = MAX_WATER - be.fluid.getAmount();
            if (space >= 1000) {
                ItemStack output = be.items.get(SLOT_EMPTY_OUT);
                boolean canOutput = output.isEmpty() || (output.is(Items.BUCKET) && output.getCount() < output.getMaxStackSize());

                if (canOutput) {
                    FluidStack water = new FluidStack(Fluids.WATER, 1000);
                    if (be.fluid.isEmpty()) {
                        be.fluid = water;
                    } else if (be.fluid.is(Fluids.WATER)) {
                        be.fluid.grow(1000);
                    }

                    be.items.set(SLOT_WATER_IN, ItemStack.EMPTY);
                    if (output.isEmpty()) {
                        be.items.set(SLOT_EMPTY_OUT, new ItemStack(Items.BUCKET));
                    } else {
                        output.grow(1);
                    }
                    dirty = true;
                }
            }
        }

        // ── Natural heat dissipation ──
        if (be.storedHeat > 0) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                be.storedHeat--;
                dirty = true;
            }
        }

        // ── Run the engine ──
        if (be.isRunning()) {
            KineticNetworkUtil.propagateKineticForce(level, pos, KF_PER_TICK);
            be.fluid.shrink(WATER_CONSUMPTION_PER_TICK);
            if (be.fluid.isEmpty()) be.fluid = FluidStack.EMPTY;
            dirty = true;
        }

        if (wasRunning != be.isRunning()) {
            level.setBlock(pos, state.setValue(StirlingEngineBlock.LIT, be.isRunning()), 3);
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
        }
    }

    public boolean isRunning() {
        return storedHeat > MIN_RUNNING_HEAT && !fluid.isEmpty() && fluid.is(Fluids.WATER) && fluid.getAmount() > 0;
    }

    public FluidStack getFluid() { return fluid; }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), null)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        storedHeat = input.getIntOr("StoredHeat", 0);
        fluid = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("StoredHeat", storedHeat);
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, fluid);
    }

    private class TankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return fluid; }
        @Override protected void revertToSnapshot(FluidStack snapshot) { fluid = snapshot; }

        @Override
        protected void onRootCommit(FluidStack originalState) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                // 1. Sync the data to the client (for the GUI/Renderer)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

                // 2. Update the LIT state immediately if it changed
                boolean currentlyRunning = isRunning();
                if (getBlockState().getValue(StirlingEngineBlock.LIT) != currentlyRunning) {
                    level.setBlock(worldPosition, getBlockState().setValue(StirlingEngineBlock.LIT, currentlyRunning), 3);
                }
            }
        }

        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return index == 0 && !fluid.isEmpty() ? FluidResource.of(fluid) : FluidResource.EMPTY; }
        @Override public long getAmountAsLong(int index) { return index == 0 ? fluid.getAmount() : 0L; }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return index == 0 ? MAX_WATER : 0L; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return index == 0 && (fluid.isEmpty() || resource.matches(fluid)) && resource.is(Fluids.WATER);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || resource.isEmpty() || amount <= 0 || !resource.is(Fluids.WATER)) return 0;
            if (!fluid.isEmpty() && !resource.matches(fluid)) return 0;
            int space = MAX_WATER - fluid.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            fluid = fluid.isEmpty() ? resource.toStack(toInsert) : fluid.copyWithAmount(fluid.getAmount() + toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || fluid.isEmpty() || amount <= 0 || !resource.matches(fluid)) return 0;
            int toExtract = Math.min(amount, fluid.getAmount());
            updateSnapshots(tx);
            fluid = fluid.copyWithAmount(fluid.getAmount() - toExtract);
            if (fluid.getAmount() <= 0) fluid = FluidStack.EMPTY;
            return toExtract;
        }
    }
}