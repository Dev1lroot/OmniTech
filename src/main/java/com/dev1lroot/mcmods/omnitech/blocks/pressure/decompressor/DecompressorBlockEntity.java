package com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.HeatExchangerBlockEntity;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.gui.DecompressorMenu;
import com.dev1lroot.mcmods.omnitech.recipes.DecompressorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.DecompressorRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import java.util.Optional;

/**
 * Decompressor block entity — the cold mirror of {@link HeatExchangerBlockEntity}.
 *
 * <p>Converts one fluid into another in timed batches.  Each completed batch
 * adds {@link DecompressorRecipe#getProductionCold()} °C of cold to this
 * machine's own {@link #storedCold}.  Processing is <em>blocked</em> while
 * {@code storedCold >= recipe.maxCold} — the machine must radiate its cold
 * to adjacent {@link IColdReceiver} blocks before the next batch can run.
 *
 * <p>Cold radiation mirrors the Heater / HeatExchanger pattern: each tick the
 * machine pushes {@code storedCold / 60} °C to every adjacent receiver, and
 * the absorbed amount is deducted from {@code storedCold}.
 */
public class DecompressorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_TANK_CAPACITY  = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;
    public static final int MAX_STORED_COLD = 1_000;
    public static final int PROCESS_TIME = 20;

    private FluidStack inputFluid  = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private static final int DECAY_INTERVAL = 20;

    private int storedCold   = 0;
    private int maxCold      = 0;
    private int processTimer = 0;
    private int decayTimer   = 0;

    private DecompressorRecipe currentRecipe   = null;
    private String             currentRecipeId = null;

    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // ContainerData: 0=storedCold  1=maxCold  2=processTimer
    //                3=inFluidAmt  4=inFluidCap  5=outFluidAmt  6=outFluidCap
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> storedCold;
                case 1 -> maxCold;
                case 2 -> processTimer;
                case 3 -> inputFluid.getAmount();
                case 4 -> INPUT_TANK_CAPACITY;
                case 5 -> outputFluid.getAmount();
                case 6 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> storedCold   = value;
                case 1 -> maxCold      = value;
                case 2 -> processTimer = value;
            }
        }
        @Override public int getCount() { return 7; }
    };

    public DecompressorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.DECOMPRESSOR.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.decompressor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new DecompressorMenu(containerId, inv, this, dataAccess);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            DecompressorBlockEntity be) {

        boolean changed = false;
        Direction facing = state.getValue(DecompressorBlock.FACING);

        // 1. Pull input fluid from the front-face network
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var source = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (source != null) {
                changed |= tryPullFluid(source, be.inputFluidHandler);
            }
        }

        // 2. Match recipe
        Optional<DecompressorRecipe> found =
                DecompressorRecipeManager.findRecipe(be.inputFluid);
        if (found.isPresent()) {
            DecompressorRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe   = recipe;
                be.currentRecipeId = recipe.getId();
                be.maxCold         = recipe.getMaxCold();
                be.processTimer    = 0;
                changed = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe   = null;
                be.currentRecipeId = null;
                be.maxCold         = 0;
                be.processTimer    = 0;
                changed = true;
            }
        }

        // 3. Advance process timer — only when not over-chilled and conditions met
        if (be.currentRecipe != null && be.storedCold < be.maxCold && be.canProcess()) {
            be.processTimer++;
            if (be.processTimer >= PROCESS_TIME) {
                be.process();
                changed = true;
            }
            changed = true;
        } else if (be.processTimer > 0) {
            be.processTimer = 0;
            changed = true;
        }

        // 4. Radiate stored cold to adjacent IColdReceiver blocks.
        //    The absorbed amount is deducted from storedCold so cold genuinely flows.
        if (be.storedCold > 0) {
            int transfer = be.storedCold / 60;
            if (transfer > 0) {
                for (Direction dir : Direction.values()) {
                    BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                    if (neighbor instanceof IColdReceiver receiver) {
                        int absorbed = receiver.addCold(transfer);
                        be.storedCold = Math.max(0, be.storedCold - absorbed);
                    }
                }
            }
            changed = true;
        }

        // 5. Ambient decay — storedCold drifts toward 0 (cold dissipates) every 20 ticks
        if (be.storedCold > 0) {
            be.decayTimer++;
            if (be.decayTimer >= DECAY_INTERVAL) {
                be.decayTimer = 0;
                be.storedCold--;
                changed = true;
            }
        } else {
            be.decayTimer = 0;
        }

        // 6. Update LIT state
        boolean shouldBeLit = be.currentRecipe != null
                && be.storedCold > 0 && be.storedCold < be.maxCold;
        if (state.getValue(DecompressorBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(DecompressorBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 7. Push output fluid to back-face network
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(back), back.getOpposite());
            if (neighbor != null) {
                changed |= tryPushFluid(be.outputFluidHandler, neighbor);
            }
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (inputFluid.isEmpty() || !inputFluid.is(currentRecipe.getInputFluid().getFluid())) return false;
        if (inputFluid.getAmount() < currentRecipe.getInputFluidAmount()) return false;

        FluidStack out = currentRecipe.getOutputFluid();
        if (outputFluid.isEmpty()) return true;
        if (!outputFluid.is(out.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= out.getAmount();
    }

    private void process() {
        int toConsume = currentRecipe.getInputFluidAmount();
        inputFluid.shrink(toConsume);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack out = currentRecipe.getOutputFluid();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = out.copy();
            } else {
                outputFluid.grow(out.getAmount());
            }
        }

        storedCold = Math.min(MAX_STORED_COLD, storedCold + currentRecipe.getProductionCold());
        processTimer = 0;
        setChanged();
    }

    // ── Fluid transfer helpers ────────────────────────────────────────────────

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty()) {
                    int available = Math.min(1000, (int) from.getAmountAsLong(i));
                    int accepted  = to.insert(res, available, tx);
                    if (accepted > 0) {
                        from.extract(res, accepted, tx);
                        tx.commit();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
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

    // ── Tank handlers ─────────────────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()         { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size()                             { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid);
        }

        @Override public long getAmountAsLong(int index)                       { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res)  { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource)    { return true; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || (!inputFluid.isEmpty() && !resource.matches(inputFluid))) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = inputFluid.isEmpty() ? resource.toStack(toFill)
                    : inputFluid.copyWithAmount(inputFluid.getAmount() + toFill);
            return toFill;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0;
        }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()         { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size()                             { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid);
        }

        @Override public long getAmountAsLong(int index)                       { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res)  { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource)    { return false; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0;
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

    public FluidStack getInputFluid()  { return inputFluid; }
    public FluidStack getOutputFluid() { return outputFluid; }
    public int getStoredCold()         { return storedCold; }
    public int getMaxCold()            { return maxCold; }
    public int getProcessTimer()       { return processTimer; }

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
        inputFluid   = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid  = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        storedCold   = input.getIntOr("StoredCold",   0);
        maxCold      = input.getIntOr("MaxCold",       0);
        processTimer = input.getIntOr("ProcessTimer",  0);
        decayTimer   = input.getIntOr("DecayTimer",    0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putInt("StoredCold",   storedCold);
        output.putInt("MaxCold",      maxCold);
        output.putInt("ProcessTimer", processTimer);
        output.putInt("DecayTimer",   decayTimer);
    }
}
