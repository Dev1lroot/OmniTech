package com.dev1lroot.mcmods.omnitech.blocks.labware.extractor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ExtractorMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.ExtractorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ExtractorRecipeManager;
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
 * Extractor block entity.
 *
 * <p>Slots: 0 = input item, 1 = residue output item.
 * Output fluid is pushed to the FACING face.
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – kineticForce × 100</li>
 *   <li>1 – requiredKineticForce × 100</li>
 *   <li>2 – outputFluid amount</li>
 *   <li>3 – OUTPUT_TANK_CAPACITY</li>
 * </ul>
 */
public class ExtractorBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, WorldlyContainer {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_RESIDUE = 1;
    public static final int SLOT_COUNT  = 2;

    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    public FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce         = 0f;
    private float requiredKineticForce = 0f;

    private ExtractorRecipe currentRecipe   = null;
    private String          currentRecipeId = null;

    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(kineticForce * 100f);
                case 1 -> (int)(requiredKineticForce * 100f);
                case 2 -> outputFluid.getAmount();
                case 3 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce         = value / 100f;
                case 1 -> requiredKineticForce  = value / 100f;
            }
        }
        @Override public int getCount() { return 4; }
    };

    public ExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.EXTRACTOR.get(), pos, state);
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.extractor");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ExtractorMenu(containerId, inv, this, dataAccess);
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

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ExtractorBlockEntity be) {
        boolean dirty = false;
        Direction facing = state.getValue(ExtractorBlock.FACING);

        // 1. Recipe matching
        Optional<ExtractorRecipe> found = ExtractorRecipeManager.findRecipe(be.items.get(SLOT_INPUT));
        if (found.isPresent()) {
            ExtractorRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe        = recipe;
                be.currentRecipeId      = recipe.getId();
                be.kineticForce         = 0f;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                dirty = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe        = null;
                be.currentRecipeId      = null;
                be.kineticForce         = 0f;
                be.requiredKineticForce = 0f;
                dirty = true;
            }
        }

        // 2. LIT state
        boolean shouldLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(ExtractorBlock.LIT) != shouldLit) {
            level.setBlock(pos, state.setValue(ExtractorBlock.LIT, shouldLit), 3);
            dirty = true;
        }

        // 3. Push output fluid to the front (FACING) face
        if (!be.outputFluid.isEmpty()) {
            var nb = level.getCapability(Capabilities.Fluid.BLOCK,
                    pos.relative(facing), facing.getOpposite());
            if (nb != null) dirty |= tryPushFluid(be.outputFluidHandler, nb);
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (!currentRecipe.matches(items.get(SLOT_INPUT))) return false;

        // Output fluid tank must have space
        FluidStack outFluid = currentRecipe.getOutputFluid();
        if (!outFluid.isEmpty()) {
            if (!outputFluid.isEmpty() && !outputFluid.is(outFluid.getFluid())) return false;
            if ((OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) < outFluid.getAmount()) return false;
        }

        // Residue slot must have space (if recipe produces residue)
        ItemStack residue = currentRecipe.getResidueItem();
        if (!residue.isEmpty()) {
            ItemStack slot = items.get(SLOT_RESIDUE);
            if (slot.isEmpty()) return true;
            if (!ItemStack.isSameItemSameComponents(slot, residue)) return false;
            return slot.getCount() + residue.getCount() <= slot.getMaxStackSize();
        }
        return true;
    }

    private void process() {
        if (currentRecipe == null) return;

        // Consume input
        items.get(SLOT_INPUT).shrink(currentRecipe.getInputItemAmount());

        // Add output fluid
        FluidStack outFluid = currentRecipe.getOutputFluid();
        if (!outFluid.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = outFluid.copy();
            } else {
                outputFluid.grow(outFluid.getAmount());
            }
        }

        // Add residue item
        ItemStack residue = currentRecipe.getResidueItem();
        if (!residue.isEmpty()) {
            ItemStack slot = items.get(SLOT_RESIDUE);
            if (slot.isEmpty()) {
                items.set(SLOT_RESIDUE, residue.copy());
            } else {
                slot.grow(residue.getCount());
            }
        }

        kineticForce = 0f;
        setChanged();
    }

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
            ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (res.isEmpty()) return false;
            int avail    = Math.min(1000, (int) from.getAmountAsLong(0));
            int accepted = to.insert(res, avail, tx);
            if (accepted > 0) {
                from.extract(res, accepted, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

    // ── Fluid accessor ────────────────────────────────────────────────────────

    public FluidStack getOutputFluid() { return outputFluid; }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    private static final int[] ALL_SLOTS = { SLOT_INPUT, SLOT_RESIDUE };

    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return index == SLOT_INPUT; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_INPUT; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return index == SLOT_RESIDUE; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        outputFluid         = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce        = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce",        kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
    }

    // ── Inner fluid handler ───────────────────────────────────────────────────

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot()         { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int i) {
            return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid);
        }
        @Override public long getAmountAsLong(int i)                  { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r){ return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int i, FluidResource r)      { return false; }
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
}
