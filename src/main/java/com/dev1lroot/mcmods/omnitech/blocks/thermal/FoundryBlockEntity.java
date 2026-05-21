/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.FoundryMenu;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipeManager;
import com.mojang.logging.LogUtils;
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
import net.minecraft.world.item.Item;
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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;

public class FoundryBlockEntity extends BaseContainerBlockEntity implements IHeatReceiver, IColdReceiver, WorldlyContainer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Slot that holds the casting template — not consumed, hopper-proof. */
    public static final int TEMPLATE_SLOT = 0;
    /** Slot that receives the finished product. */
    public static final int OUTPUT_SLOT   = 1;
    public static final int SLOT_COUNT    = 2;

    private static final int[] SLOTS_FOR_EXTRACTION = { OUTPUT_SLOT };
    private static final int[] SLOTS_EMPTY = { };

    public static final int INPUT_TANK_CAPACITY = 16_000;
    private static final int DEFAULT_PROCESS_TIME = 200;
    private static final int MAX_HEAT             = 3000;
    private static final int AMBIENT_TEMPERATURE  = 15;
    private static final int HEAT_LOSS_INTERVAL   = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack inputFluid = FluidStack.EMPTY;

    private FoundryRecipe currentRecipe = null;
    private int temperature     = 0;
    private int requiredTemp    = 0;
    private int processProgress = 0;
    private int processTotalTime = DEFAULT_PROCESS_TIME;
    private int heatLossTimer   = 0;
    /** Game-time tick when the last craft completed; used by the client renderer for the 200 ms output flash. */
    private long lastCraftGameTime = Long.MIN_VALUE / 2;

    /** Capability-exposed insert-only fluid handler. */
    public final ResourceHandler<FluidResource> fluidHandler = new InputTankHandler();

    @Override
    public int @NonNull [] getSlotsForFace(Direction side) {
        if (side.getAxis().isHorizontal()) {
            return SLOTS_FOR_EXTRACTION;
        }
        return SLOTS_EMPTY;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        // Strictly block all item insertion via automation.
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index != OUTPUT_SLOT) return false;
        return direction.getAxis().isHorizontal();
    }

    // ── Hopper protection (Overrides from BaseContainer) ──────────────────────

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        // Manual insertion logic for the GUI is handled by the Menu/Slot.
        // This method returning false helps prevent generic automation.
        return false;
    }

    @Override
    public boolean canTakeItem(net.minecraft.world.Container target, int index, ItemStack stack) {
        // Prevent hoppers from ever touching the template slot (0).
        return index == OUTPUT_SLOT;
    }

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> temperature;
                case 1 -> requiredTemp;
                case 2 -> processProgress;
                case 3 -> processTotalTime;
                case 4 -> inputFluid.getAmount();
                case 5 -> INPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> temperature     = value;
                case 1 -> requiredTemp    = value;
                case 2 -> processProgress = value;
                case 3 -> processTotalTime = value;
            }
        }
        @Override
        public int getCount() { return 6; }
    };

    public FoundryBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FOUNDRY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.foundry");
    }

    @Override
    protected NonNullList<ItemStack> getItems()              { return items; }
    @Override
    protected void setItems(NonNullList<ItemStack> items)    { this.items = items; }
    @Override
    public int getContainerSize()                            { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new FoundryMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── Hopper protection for the template slot ───────────────────────────────

    /**
     * Block all automation access to the template slot.
     * Players still interact via the custom TemplateSlot in the menu.
     * The output slot is output-only, so block insertion there too.
     */

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (temperature >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - temperature);
        temperature += absorbed;
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (temperature <= 0) return 0;
        int absorbed = Math.min(celsius, temperature);
        temperature -= absorbed;
        return absorbed;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FoundryBlockEntity be) {
        boolean changed = false;

        // Natural ambient drift — temperature moves 1°C toward 15 every 20 ticks
        if (be.temperature != AMBIENT_TEMPERATURE) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                if (be.temperature > AMBIENT_TEMPERATURE) be.temperature--;
                else be.temperature++;
                changed = true;
            }
        } else {
            be.heatLossTimer = 0;
        }

        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var topPos = pos.above();
            var fluidSource = level.getCapability(Capabilities.Fluid.BLOCK, topPos, Direction.DOWN);
            if (fluidSource != null) {
                changed |= FluidNetworkUtil.tryPullFluid(fluidSource, be.fluidHandler);
            }
        }

        // Recipe lookup
        boolean hasRecipe = be.findAndUpdateRecipe();
        int newRequiredTemp = (hasRecipe && be.currentRecipe != null)
                ? be.currentRecipe.getRequiredMinimalTemperature() : 0;
        if (be.requiredTemp != newRequiredTemp) {
            be.requiredTemp = newRequiredTemp;
            changed = true;
        }

        // Processing
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
        if (state.getValue(FoundryBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(FoundryBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean findAndUpdateRecipe() {
        Optional<FoundryRecipe> found = FoundryRecipeManager.findRecipe(
                items.get(TEMPLATE_SLOT), inputFluid);
        if (found.isPresent()) {
            currentRecipe = found.get();
            return true;
        }
        currentRecipe = null;
        return false;
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (!currentRecipe.matchesTemplate(items.get(TEMPLATE_SLOT))) return false;
        if (temperature < currentRecipe.getRequiredMinimalTemperature()) return false;
        if (inputFluid.isEmpty() || !currentRecipe.matchesFluid(inputFluid)) return false;
        if (inputFluid.getAmount() < currentRecipe.getInputAmount()) return false;

        // Check output slot capacity
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        Item outItem = currentRecipe.getOutputItem();
        return output.is(outItem) && output.getCount() < output.getMaxStackSize();
    }

    private void process() {
        if (currentRecipe == null) return;

        // Consume fluid (template is NOT consumed)
        int toConsume = currentRecipe.getInputAmount();
        inputFluid = inputFluid.copyWithAmount(inputFluid.getAmount() - toConsume);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        // Produce output item
        Item outItem = currentRecipe.getOutputItem();
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.set(OUTPUT_SLOT, new ItemStack(outItem, 1));
        } else {
            output.grow(1);
        }

        // Record completion time so the client renderer can show the 200 ms output flash
        if (level != null) lastCraftGameTime = level.getGameTime();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getInputFluid()       { return inputFluid; }
    public int getTemperature()             { return temperature; }
    public ContainerData getContainerData() { return dataAccess; }
    public long getLastCraftGameTime()      { return lastCraftGameTime; }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        // Вместо super или ручного создания, используем системный сборщик
        try (net.minecraft.util.ProblemReporter.ScopedCollector reporter =
                     new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {

            // Создаем чистый выход
            net.minecraft.world.level.storage.TagValueOutput output =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);

            // ВАЖНО: Вызываем ТВОЙ метод, который сохраняет предметы, температуру и жидкость!
            // Это гарантирует, что в пакете будет ВЕСЬ инвентарь (через ContainerHelper)
            this.saveAdditional(output);

            return output.buildResult();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        temperature       = input.getIntOr("Temperature", 0);
        processProgress   = input.getIntOr("ProcessProgress", 0);
        processTotalTime  = input.getIntOr("ProcessTotalTime", DEFAULT_PROCESS_TIME);
        lastCraftGameTime = input.getLongOr("LastCraftGameTime", Long.MIN_VALUE / 2);
        inputFluid = input.read("InputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Temperature", temperature);
        output.putInt("ProcessProgress", processProgress);
        output.putInt("ProcessTotalTime", processTotalTime);
        output.putLong("LastCraftGameTime", lastCraftGameTime);
        output.store("InputFluid", FluidStack.OPTIONAL_CODEC, inputFluid);
    }

    // ── Insert-only fluid capability handler ──────────────────────────────────

    private class InputTankHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()         { return inputFluid; }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }

        @Override
        protected void onRootCommit(FluidStack originalState) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            return (index == 0 && !inputFluid.isEmpty()) ? FluidResource.of(inputFluid) : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) { return index == 0 ? inputFluid.getAmount() : 0L; }

        @Override
        public long getCapacityAsLong(int index, FluidResource res) {
            return index == 0 ? INPUT_TANK_CAPACITY : 0L;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0) return false;
            return inputFluid.isEmpty() || resource.is(inputFluid.getFluid());
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || resource.isEmpty() || amount <= 0) return 0;
            if (!inputFluid.isEmpty() && !resource.is(inputFluid.getFluid())) return 0;
            int space = INPUT_TANK_CAPACITY - inputFluid.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, resource, toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return 0; // Input-only; machine drains fluid internally during processing
        }
    }
}
