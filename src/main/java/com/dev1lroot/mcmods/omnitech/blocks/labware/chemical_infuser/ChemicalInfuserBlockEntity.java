package com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_infuser;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ChemicalInfuserMenu;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalInfuserRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalInfuserRecipeManager;
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

import java.util.Optional;

/**
 * Chemical Infuser block entity.
 *
 * <p>Slots: 0 = input item, 1 = output item.
 * Fluid tank: input fluid pulled from the FACING face.
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – kineticForce × 100 (fixed-point)</li>
 *   <li>1 – requiredKineticForce × 100</li>
 *   <li>2 – storedTemp (°C, signed)</li>
 *   <li>3 – requiredTemperature from recipe (or 0)</li>
 *   <li>4 – inputFluid amount</li>
 *   <li>5 – INPUT_TANK_CAPACITY</li>
 * </ul>
 */
public class ChemicalInfuserBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, IHeatReceiver, IColdReceiver, WorldlyContainer {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    public static final int INPUT_TANK_CAPACITY = 8_000;
    public static final int MAX_TEMP  = 1500;
    public static final int MIN_TEMP  = -200;
    private static final int AMBIENT  = 15;
    private static final int DECAY_INTERVAL = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    public FluidStack inputFluid = FluidStack.EMPTY;

    private float kineticForce         = 0f;
    private float requiredKineticForce = 0f;
    int storedTemp  = AMBIENT;
    private int decayTimer = 0;

    private ChemicalInfuserRecipe currentRecipe   = null;
    private String                currentRecipeId = null;

    public final ResourceHandler<FluidResource> inputFluidHandler = new InputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(kineticForce * 100f);
                case 1 -> (int)(requiredKineticForce * 100f);
                case 2 -> storedTemp;
                case 3 -> currentRecipe != null ? currentRecipe.getRequiredTemperature() : 0;
                case 4 -> inputFluid.getAmount();
                case 5 -> INPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce         = value / 100f;
                case 1 -> requiredKineticForce  = value / 100f;
                case 2 -> storedTemp            = value;
            }
        }
        @Override public int getCount() { return 6; }
    };

    public ChemicalInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CHEMICAL_INFUSER.get(), pos, state);
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.chemical_infuser");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ChemicalInfuserMenu(containerId, inv, this, dataAccess);
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

    // ── IHeatReceiver / IColdReceiver ─────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (storedTemp >= MAX_TEMP) return 0;
        int absorbed = Math.min(celsius, MAX_TEMP - storedTemp);
        storedTemp += absorbed;
        setChanged();
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (storedTemp <= MIN_TEMP) return 0;
        int absorbed = Math.min(celsius, storedTemp - MIN_TEMP);
        storedTemp -= absorbed;
        setChanged();
        return absorbed;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ChemicalInfuserBlockEntity be) {
        boolean dirty = false;
        Direction facing = state.getValue(ChemicalInfuserBlock.FACING);

        // 1. Pull input fluid from the front face
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var src = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (src != null) dirty |= tryPullFluid(src, be.inputFluidHandler);
        }

        // 2. Recipe matching
        Optional<ChemicalInfuserRecipe> found = ChemicalInfuserRecipeManager.findRecipe(
                be.inputFluid, be.items.get(SLOT_INPUT), be.storedTemp);
        if (found.isPresent()) {
            ChemicalInfuserRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe       = recipe;
                be.currentRecipeId     = recipe.getId();
                be.kineticForce        = 0f;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                dirty = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe       = null;
                be.currentRecipeId     = null;
                be.kineticForce        = 0f;
                be.requiredKineticForce = 0f;
                dirty = true;
            }
        }

        // 3. LIT state
        boolean shouldLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(ChemicalInfuserBlock.LIT) != shouldLit) {
            level.setBlock(pos, state.setValue(ChemicalInfuserBlock.LIT, shouldLit), 3);
            dirty = true;
        }

        // 4. Temperature decay toward ambient
        if (be.storedTemp != AMBIENT) {
            be.decayTimer++;
            if (be.decayTimer >= DECAY_INTERVAL) {
                be.decayTimer = 0;
                if (be.storedTemp > AMBIENT) be.storedTemp--;
                else be.storedTemp++;
                dirty = true;
            }
        } else {
            be.decayTimer = 0;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (!currentRecipe.matches(inputFluid, items.get(SLOT_INPUT), storedTemp)) return false;

        // Output slot must be empty or hold the same item with space
        ItemStack output = currentRecipe.getOutputItem();
        ItemStack outSlot = items.get(SLOT_OUTPUT);
        if (outSlot.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(outSlot, output)) return false;
        return outSlot.getCount() + output.getCount() <= outSlot.getMaxStackSize();
    }

    private void process() {
        if (currentRecipe == null) return;

        // Consume input fluid
        int toConsume = currentRecipe.getInputFluidAmount();
        inputFluid.shrink(toConsume);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        // Consume input item
        items.get(SLOT_INPUT).shrink(currentRecipe.getInputItemAmount());

        // Add output item
        ItemStack output = currentRecipe.getOutputItem();
        ItemStack outSlot = items.get(SLOT_OUTPUT);
        if (outSlot.isEmpty()) {
            items.set(SLOT_OUTPUT, output.copy());
        } else {
            outSlot.grow(output.getCount());
        }

        kineticForce = 0f;
        setChanged();
    }

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from,
            ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty()) {
                    int avail    = Math.min(1000, (int) from.getAmountAsLong(i));
                    int accepted = to.insert(res, avail, tx);
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

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid() { return inputFluid; }
    public int getStoredTemp()        { return storedTemp; }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] INPUT_SLOTS  = { SLOT_INPUT };
    private static final int[] OUTPUT_SLOTS = { SLOT_OUTPUT };
    private static final int[] ALL_SLOTS    = { SLOT_INPUT, SLOT_OUTPUT };

    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return index == SLOT_INPUT; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_INPUT; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return index == SLOT_OUTPUT; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid         = input.read("InputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce        = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
        storedTemp          = input.getIntOr("StoredTemp", AMBIENT);
        decayTimer          = input.getIntOr("DecayTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid", FluidStack.OPTIONAL_CODEC, inputFluid);
        output.putFloat("KineticForce",        kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
        output.putInt("StoredTemp",  storedTemp);
        output.putInt("DecayTimer",  decayTimer);
    }

    // ── Inner fluid handler ───────────────────────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()         { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int i) {
            return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid);
        }
        @Override public long getAmountAsLong(int i)                  { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return true; }
        @Override public int insert(int i, FluidResource res, int amt, TransactionContext tx) {
            if (res.isEmpty() || (!inputFluid.isEmpty() && !res.matches(inputFluid))) return 0;
            int toFill = Math.min(amt, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = inputFluid.isEmpty()
                    ? res.toStack(toFill)
                    : inputFluid.copyWithAmount(inputFluid.getAmount() + toFill);
            return toFill;
        }
        @Override public int extract(int i, FluidResource res, int amt, TransactionContext tx) { return 0; }
    }
}
