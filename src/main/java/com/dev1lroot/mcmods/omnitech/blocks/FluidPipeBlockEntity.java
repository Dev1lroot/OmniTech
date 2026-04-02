package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * Block entity for {@link FluidPipeBlock}.
 *
 * <p>Holds up to {@link #CAPACITY} mb of a single fluid type.
 * Every server tick, connected pipe neighbors exchange fluid until their
 * levels equalize. Equalization does NOT cross pumps or tanks.
 *
 * <p>Implements {@link ResourceHandler}{@code <FluidResource>} via a
 * {@link SnapshotJournal}-backed inner handler so pumps can access it through
 * the NeoForge capability system with proper transaction support.
 */
public class FluidPipeBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Maximum fluid the pipe can hold, in millibuckets. */
    public static final int CAPACITY = 1000;

    /** Internal fluid storage — manipulated directly during equalization. */
    FluidStack fluid = FluidStack.EMPTY;

    /** Exposed capability handler with transaction (snapshot/rollback) support. */
    public final ResourceHandler<FluidResource> fluidHandler = new FluidHandler();

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    // ── Server tick — equalization between adjacent pipes ─────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity be)
    {
        boolean dirty = false;

        for (Direction dir : Direction.values())
        {
            if (!state.getValue(FluidPipeBlock.propertyFor(dir))) continue;

            BlockPos neighborPos = pos.relative(dir);
            // Process each unordered pair once to prevent double-counting.
            if (!isPrimary(pos, neighborPos)) continue;

            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (!(neighbor instanceof FluidPipeBlockEntity pipe)) continue;

            // Skip incompatible fluid types
            if (!be.fluid.isEmpty() && !pipe.fluid.isEmpty()
                    && !FluidResource.of(be.fluid).equals(FluidResource.of(pipe.fluid))) continue;

            int myAmt       = be.fluid.getAmount();
            int neighborAmt = pipe.fluid.getAmount();
            if (myAmt == neighborAmt) continue;

            int diff     = myAmt - neighborAmt;
            int transfer = Math.abs(diff) / 2;
            if (transfer <= 0) continue;

            if (diff > 0) {
                // be → pipe
                int canFit = CAPACITY - neighborAmt;
                transfer = Math.min(transfer, canFit);
                if (transfer <= 0) continue;
                FluidStack moving = be.fluid.copyWithAmount(transfer);
                pipe.fluid = pipe.fluid.isEmpty() ? moving
                        : pipe.fluid.copyWithAmount(neighborAmt + transfer);
                be.fluid = be.fluid.copyWithAmount(myAmt - transfer);
                if (be.fluid.getAmount() <= 0) be.fluid = FluidStack.EMPTY;
                pipe.setChanged();
                level.sendBlockUpdated(neighborPos, pipe.getBlockState(), pipe.getBlockState(), 3);
            } else {
                // pipe → be
                int canFit = CAPACITY - myAmt;
                transfer = Math.min(transfer, canFit);
                if (transfer <= 0) continue;
                FluidStack moving = pipe.fluid.copyWithAmount(transfer);
                be.fluid = be.fluid.isEmpty() ? moving
                        : be.fluid.copyWithAmount(myAmt + transfer);
                pipe.fluid = pipe.fluid.copyWithAmount(neighborAmt - transfer);
                if (pipe.fluid.getAmount() <= 0) pipe.fluid = FluidStack.EMPTY;
                pipe.setChanged();
                level.sendBlockUpdated(neighborPos, pipe.getBlockState(), pipe.getBlockState(), 3);
            }
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static boolean isPrimary(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return a.getY() < b.getY();
        if (a.getX() != b.getX()) return a.getX() < b.getX();
        return a.getZ() < b.getZ();
    }

    // ── Inner ResourceHandler with SnapshotJournal transaction support ─────────

    private class FluidHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override
        protected FluidStack createSnapshot() {
            return fluid; // FluidStack is immutable — safe to store reference
        }

        @Override
        protected void revertToSnapshot(FluidStack snapshot) {
            fluid = snapshot;
        }

//        @Override
//        protected void onRootCommit() {
//            setChanged();
//            if (level != null && !level.isClientSide()) {
//                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
//            }
//        }

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
            updateSnapshots(tx);
            fluid = fluid.isEmpty() ? resource.toStack(toInsert)
                    : fluid.copyWithAmount(fluid.getAmount() + toInsert);
            return toInsert;
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
        fluid = input.read("Fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Fluid", FluidStack.OPTIONAL_CODEC, fluid);
    }
}
