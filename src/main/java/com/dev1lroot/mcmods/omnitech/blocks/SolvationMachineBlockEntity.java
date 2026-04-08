package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.SolvationMachineMenu;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipeManager;
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

import java.util.Optional;

/**
 * Block entity for the Solvation Machine.
 *
 * <h3>Processing model</h3>
 * <ol>
 *   <li>Find a recipe that matches the item in slot 0 and the input fluid tank.</li>
 *   <li>Accept KF via {@link IKineticReceiver}; accumulate until
 *       {@code requiredKineticForce} is reached.</li>
 *   <li>On completion: consume one item + required input fluid, produce output
 *       fluid into the output tank.</li>
 * </ol>
 *
 * <h3>Fluid connections</h3>
 * <ul>
 *   <li>Front face ({@code FACING}): fluid input (insert-capable).</li>
 *   <li>Back face ({@code FACING.getOpposite()}): fluid output (extract-capable).</li>
 * </ul>
 *
 * <h3>ContainerData layout (6 slots)</h3>
 * <ul>
 *   <li>0 – kineticForce × 100 (fixed-point)</li>
 *   <li>1 – requiredKineticForce × 100</li>
 *   <li>2 – inputFluid amount (mB)</li>
 *   <li>3 – INPUT_TANK_CAPACITY (constant)</li>
 *   <li>4 – outputFluid amount (mB)</li>
 *   <li>5 – OUTPUT_TANK_CAPACITY (constant)</li>
 * </ul>
 */
public class SolvationMachineBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, WorldlyContainer {

    public static final int SLOT_INPUT    = 0;
    public static final int SLOT_COUNT    = 1;

    public static final int INPUT_TANK_CAPACITY  = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private FluidStack inputFluid  = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce         = 0f;
    private float requiredKineticForce = 0f;
    private SolvationRecipe currentRecipe    = null;
    private String          currentRecipeId  = null;

    // ── Capability-exposed fluid handlers ─────────────────────────────────────

    /** Front face: insert-only handler for the input fluid tank. */
    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();

    /** Back face: extract-only handler for the output fluid tank. */
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    // ── ContainerData ─────────────────────────────────────────────────────────

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(kineticForce * 100f);
                case 1 -> (int)(requiredKineticForce * 100f);
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
                case 2 -> {} // fluid amount managed server-side only
                case 4 -> {}
            }
        }
        @Override public int getCount() { return 6; }
    };

    // ── Construction ──────────────────────────────────────────────────────────

    public SolvationMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SOLVATION_MACHINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.solvation_machine");
    }

    @Override protected NonNullList<ItemStack> getItems()                { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)      { this.items = items; }
    @Override public    int  getContainerSize()                          { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new SolvationMachineMenu(containerId, inv, this, dataAccess);
    }

    // ── Network sync (for fluid rendering on client) ──────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(
            net.minecraft.core.HolderLookup.Provider registries) {
        var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(
                this.problemPath(),
                com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput
                    .createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public float getKfDemand() {
        return (currentRecipe != null && canProcess()) ? 0.1f : 0f;
    }

    @Override
    public boolean addKineticForce(float amount) {
        if (currentRecipe == null || !canProcess()) return false;

        kineticForce += amount;
        setChanged();

        if (kineticForce >= requiredKineticForce) {
            process();
        }
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SolvationMachineBlockEntity be) {

        boolean changed = false;

        // ── 1. Find / update recipe ───────────────────────────────────────────
        Optional<SolvationRecipe> found =
                SolvationRecipeManager.findRecipe(be.items.get(SLOT_INPUT), be.inputFluid);

        if (found.isPresent()) {
            SolvationRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe       = recipe;
                be.currentRecipeId     = recipe.getId();
                be.kineticForce        = 0f;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                changed = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe       = null;
                be.currentRecipeId     = null;
                be.kineticForce        = 0f;
                be.requiredKineticForce = 0f;
                changed = true;
            }
        }

        // ── 2. LIT blockstate ─────────────────────────────────────────────────
        boolean shouldBeLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(SolvationMachineBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(SolvationMachineBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // ── 3. Auto-push output fluid to the back neighbor ────────────────────
        if (!be.outputFluid.isEmpty()) {
            Direction back = state.getValue(SolvationMachineBlock.FACING).getOpposite();
            var neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.relative(back), back.getOpposite());
            if (neighbor != null) {
                changed |= tryPushFluid(be.outputFluidHandler, neighbor);
            }
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(currentRecipe.getInputFluid().getFluid())) return false;
        if (inputFluid.getAmount() < currentRecipe.getInputFluidAmount()) return false;
        ItemStack slot = items.get(SLOT_INPUT);
        if (slot.isEmpty() || !slot.is(currentRecipe.getInputItem())) return false;
        if (slot.getCount() < currentRecipe.getInputItemAmount()) return false;
        // Check output tank has space
        FluidStack out = currentRecipe.getOutputFluid();
        if (outputFluid.isEmpty()) return true;
        if (!outputFluid.is(out.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= out.getAmount();
    }

    private void process() {
        if (currentRecipe == null) return;

        // Consume item
        items.get(SLOT_INPUT).shrink(currentRecipe.getInputItemAmount());

        // Consume input fluid
        int toConsume = currentRecipe.getInputFluidAmount();
        if (inputFluid.getAmount() <= toConsume) {
            inputFluid = FluidStack.EMPTY;
        } else {
            inputFluid.shrink(toConsume);
        }

        // Add output fluid
        FluidStack out = currentRecipe.getOutputFluid();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = out.copy();
            } else {
                outputFluid.grow(out.getAmount());
            }
        }

        kineticForce = 0f;
        currentRecipe      = null;
        currentRecipeId    = null;
        requiredKineticForce = 0f;
        setChanged();
    }

    // ── Fluid push helper (mirrors SmelterBlockEntity) ────────────────────────

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to) {
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
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

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getInputFluid()   { return inputFluid; }
    public FluidStack getOutputFluid()  { return outputFluid; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── WorldlyContainer — items accepted from any side ───────────────────────

    private static final int[] ALL_SLOTS = { SLOT_INPUT };

    @Override public int[] getSlotsForFace(Direction side)                              { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack)                   { return index == SLOT_INPUT; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_INPUT; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir)            { return false; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid  = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce         = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce",         kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
    }

    // ── Inner fluid handlers ──────────────────────────────────────────────────

    /** Front face: pipes push fluid IN. Machine never pulls from it. */
    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()          { return inputFluid; }
        @Override protected void revertToSnapshot(FluidStack s)  { inputFluid = s; }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return (index == 0 && !inputFluid.isEmpty())
                    ? FluidResource.of(inputFluid) : FluidResource.EMPTY;
        }

        @Override public long getAmountAsLong(int index) { return index == 0 ? inputFluid.getAmount() : 0L; }

        @Override
        public long getCapacityAsLong(int index, FluidResource res) {
            return index == 0 ? INPUT_TANK_CAPACITY : 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) { return index == 0; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || resource.isEmpty()) return 0;
            // Only accept if tank is empty or holds the same fluid
            if (!inputFluid.isEmpty() && !resource.matches(inputFluid)) return 0;
            int space = INPUT_TANK_CAPACITY - inputFluid.getAmount();
            if (space <= 0) return 0;
            int toFill = Math.min(amount, space);
            updateSnapshots(tx);
            if (inputFluid.isEmpty()) {
                inputFluid = resource.toStack(toFill);
            } else {
                inputFluid.grow(toFill);
            }
            return toFill;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // Input-only: machine consumes internally
        }
    }

    /** Back face: pipes pull fluid OUT. Machine fills it internally. */
    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()          { return outputFluid; }
        @Override protected void revertToSnapshot(FluidStack s)  { outputFluid = s; }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return (index == 0 && !outputFluid.isEmpty())
                    ? FluidResource.of(outputFluid) : FluidResource.EMPTY;
        }

        @Override public long getAmountAsLong(int index) { return index == 0 ? outputFluid.getAmount() : 0L; }

        @Override
        public long getCapacityAsLong(int index, FluidResource res) {
            return index == 0 ? OUTPUT_TANK_CAPACITY : 0L;
        }

        @Override public boolean isValid(int index, FluidResource resource) { return false; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // Output-only: machine fills internally
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExtract = Math.min(amount, outputFluid.getAmount());
            if (toExtract <= 0) return 0;
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExtract);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExtract;
        }
    }
}
