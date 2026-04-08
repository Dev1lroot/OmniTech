package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipeManager;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Optional;

public class FluidCollectorBlockEntity extends BlockEntity {

    /** Output tank capacity in millibuckets. */
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    /**
     * Millibuckets generated per tick when the back face is touching a fluid
     * source block or air. Recipe-based outputs use the amount from their JSON.
     */
    public static final int FLUID_RATE = 100;

    private FluidStack outputFluid = FluidStack.EMPTY;

    /** Exposed capability — output-only handler for the front (FACING) face. */
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // Cached omnitech:air fluid — resolved once on first use.
    private static Fluid cachedAirFluid = null;

    public FluidCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FLUID_COLLECTOR.get(), pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            FluidCollectorBlockEntity be) {

        Direction facing  = state.getValue(FluidCollectorBlock.FACING);
        Direction backDir = facing.getOpposite();
        BlockPos  backPos = pos.relative(backDir);

        // ── 1. Determine fluid to produce ─────────────────────────────────────

        FluidStack toGenerate = resolveProduction(level, backPos);

        // ── 2. Fill output tank ───────────────────────────────────────────────

        boolean changed = false;
        if (!toGenerate.isEmpty()) {
            if (be.outputFluid.isEmpty()) {
                be.outputFluid = toGenerate.copyWithAmount(Math.min(toGenerate.getAmount(), OUTPUT_TANK_CAPACITY));
                changed = true;
            } else if (be.outputFluid.is(toGenerate.getFluid())) {
                int space = OUTPUT_TANK_CAPACITY - be.outputFluid.getAmount();
                if (space > 0) {
                    be.outputFluid.grow(Math.min(toGenerate.getAmount(), space));
                    changed = true;
                }
            } else {
                // Back-face source changed — flush the old fluid and start fresh next tick
                be.outputFluid = FluidStack.EMPTY;
                changed = true;
            }
        } else if (!be.outputFluid.isEmpty()) {
            // No recognized source on the back face — drain the tank over time
            be.outputFluid = FluidStack.EMPTY;
            changed = true;
        }

        // ── 3. Push output into the front-face pipe/tank network ─────────────
        // BFS through the whole network so fluid propagates past full pipe
        // segments — identical approach to how the Pump pushes its output.

        if (!be.outputFluid.isEmpty()) {
            FluidResource outRes = FluidResource.of(be.outputFluid);
            ResourceHandler<FluidResource> target =
                    FluidNetworkUtil.findOutputTarget(level, pos.relative(facing), outRes);
            if (target != null) {
                changed |= tryPushFluid(be.outputFluidHandler, target);
            }
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Determines the fluid (and amount) this collector should produce based on
     * the block touching its back face this tick.
     */
    private static FluidStack resolveProduction(Level level, BlockPos backPos) {
        BlockState backState = level.getBlockState(backPos);
        FluidState backFluid = level.getFluidState(backPos);

        // ── Fluid source block (water, lava, any modded fluid) ────────────────
        if (!backFluid.isEmpty() && backFluid.isSource()) {
            Fluid fluid = backFluid.getType();
            if (fluid != Fluids.EMPTY) {
                return new FluidStack(fluid, FLUID_RATE);
            }
        }

        // ── Air → omnitech:air ────────────────────────────────────────────────
        if (backState.isAir()) {
            Fluid airFluid = getOmnitechAir();
            if (airFluid != null && airFluid != Fluids.EMPTY) {
                return new FluidStack(airFluid, FLUID_RATE);
            }
            return FluidStack.EMPTY;
        }

        // ── Solid block — look up a recipe ────────────────────────────────────
        Optional<FluidCollectorRecipe> recipe =
                FluidCollectorRecipeManager.findRecipe(backState.getBlock());
        return recipe.map(FluidCollectorRecipe::getOutputFluid).orElse(FluidStack.EMPTY);
    }

    private static Fluid getOmnitechAir() {
        if (cachedAirFluid == null) {
            cachedAirFluid = BuiltInRegistries.FLUID.getValue(
                    Identifier.fromNamespaceAndPath("omnitech", "air"));
        }
        return cachedAirFluid;
    }

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to) {
        try (Transaction tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (res.isEmpty()) return false;
            int available = Math.min(1000, (int) from.getAmountAsLong(0));
            int accepted  = to.insert(res, available, tx);
            if (accepted > 0) {
                from.extract(res, accepted, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

    // ── Output tank handler ───────────────────────────────────────────────────

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()           { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s)   { outputFluid = s; }
        @Override public int size()                               { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid);
        }

        @Override public long getAmountAsLong(int index)                        { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res)   { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource)      { return false; } // insert not allowed

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // external insert not allowed — collector only generates fluid internally
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getOutputFluid() { return outputFluid; }

    // ── Sync & persistence ────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(),
                com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
    }
}
