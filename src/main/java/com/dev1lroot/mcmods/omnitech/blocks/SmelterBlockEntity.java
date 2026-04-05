package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.SmelterMenu;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SmelterBlockEntity extends BaseContainerBlockEntity implements IHeatReceiver {

    public static final int SLOT_COUNT = 9;
    public static final int OUTPUT_TANK_CAPACITY = 64_000;

    private static final int DEFAULT_PROCESS_TIME = 200;
    private static final int MAX_HEAT = 3000;
    private static final int HEAT_LOSS_INTERVAL = 20; // ticks between natural −1°C drops

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private SmelterRecipe currentRecipe = null;

    private int temperature = 0;
    private int requiredTemperature = 0;
    private int processProgress = 0;
    private int processTotalTime = DEFAULT_PROCESS_TIME;
    private int heatLossTimer = 0;

    private FluidStack outputFluid = FluidStack.EMPTY;

    /** Capability-exposed output-only fluid handler. */
    public final ResourceHandler<FluidResource> fluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> temperature;
                case 1 -> requiredTemperature;
                case 2 -> processProgress;
                case 3 -> processTotalTime;
                case 4 -> outputFluid.getAmount();
                case 5 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> temperature = value;
                case 1 -> requiredTemperature = value;
                case 2 -> processProgress = value;
                case 3 -> processTotalTime = value;
            }
        }

        @Override
        public int getCount() { return 6; }
    };

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SMELTER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.smelter");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new SmelterMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public boolean addHeat(int celsius) {
        if (temperature >= MAX_HEAT) return false;
        temperature = Math.min(MAX_HEAT, temperature + celsius);
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity be) {
        boolean changed = false;

        // Natural heat dissipation
        if (be.temperature > 0) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                be.temperature--;
                changed = true;
            }
        } else {
            be.heatLossTimer = 0;
        }

        // Find matching recipe
        boolean hasRecipe = be.findAndUpdateRecipe();
        int newRequiredTemp = (hasRecipe && be.currentRecipe != null)
                ? be.currentRecipe.getRequiredMinimalTemperature() : 0;
        if (be.requiredTemperature != newRequiredTemp) {
            be.requiredTemperature = newRequiredTemp;
            changed = true;
        }

        // Process if conditions met
        if (hasRecipe && be.canProcess()) {
            be.processProgress++;
            changed = true;

            if (be.processProgress >= be.processTotalTime) {
                be.process();
                be.processProgress = 0;
                changed = true;
            }
        } else if (be.processProgress > 0) {
            be.processProgress = 0;
            changed = true;
        }

        // Update LIT blockstate
        boolean shouldBeLit = hasRecipe && be.canProcess();
        if (state.getValue(SmelterBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(SmelterBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        if (changed) be.setChanged();
    }

    private boolean findAndUpdateRecipe() {
        Optional<SmelterRecipe> found = SmelterRecipeManager.findRecipe(getInputStacks());
        if (found.isPresent()) {
            currentRecipe = found.get();
            return true;
        }
        currentRecipe = null;
        return false;
    }

    private List<ItemStack> getInputStacks() {
        return new ArrayList<>(items.subList(0, SLOT_COUNT));
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (temperature < currentRecipe.getRequiredMinimalTemperature()) return false;
        FluidStack out = currentRecipe.getOutput();
        if (out.isEmpty()) return false;
        if (!outputFluid.isEmpty() && !outputFluid.is(out.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= out.getAmount();
    }

    private void process() {
        if (currentRecipe == null) return;

        // Consume one of each required ingredient
        for (Item ingredient : currentRecipe.getIngredients()) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty() && stack.is(ingredient)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        // Fill output fluid tank
        FluidStack out = currentRecipe.getOutput();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = out.copy();
            } else {
                outputFluid.grow(out.getAmount());
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getOutputFluid() { return outputFluid; }
    public int getTemperature() { return temperature; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        temperature = input.getIntOr("Temperature", 0);
        processProgress = input.getIntOr("ProcessProgress", 0);
        processTotalTime = input.getIntOr("ProcessTotalTime", DEFAULT_PROCESS_TIME);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Temperature", temperature);
        output.putInt("ProcessProgress", processProgress);
        output.putInt("ProcessTotalTime", processTotalTime);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
    }

    // ── Output fluid capability handler (extract-only) ────────────────────────

    private class OutputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot() { return outputFluid; }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return (index == 0 && !outputFluid.isEmpty()) ? FluidResource.of(outputFluid) : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) { return index == 0 ? outputFluid.getAmount() : 0L; }

        @Override
        public long getCapacityAsLong(int index, FluidResource res) {
            return index == 0 ? OUTPUT_TANK_CAPACITY : 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) { return false; }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // Output-only; smelter fills this internally
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
