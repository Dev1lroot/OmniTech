/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.SolvationMachineMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
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
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class SolvationMachineBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_COUNT = 1;
    public static final int INPUT_TANK_CAPACITY = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack inputFluid = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce = 0f;
    private float requiredKineticForce = 0f;
    private SolvationRecipe currentRecipe = null;
    private String currentRecipeId = null;

    public final ResourceHandler<FluidResource> inputFluidHandler = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

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
            }
        }
        @Override public int getCount() { return 6; }
    };

    public SolvationMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SOLVATION_MACHINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.solvation_machine");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new SolvationMachineMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

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

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolvationMachineBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(SolvationMachineBlock.FACING);

        // 1. Втягивание жидкости (активное)
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var inputSource = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(facing), facing.getOpposite());
            if (inputSource != null) {
                changed |= FluidNetworkUtil.tryPullFluid(inputSource, be.inputFluidHandler);
            }
        }

        // 2. Рецепты
        Optional<SolvationRecipe> found = SolvationRecipeManager.findRecipe(be.items.get(SLOT_INPUT), be.inputFluid);
        if (found.isPresent()) {
            SolvationRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe = recipe;
                be.currentRecipeId = recipe.getId();
                be.kineticForce = 0f;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                changed = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe = null;
                be.currentRecipeId = null;
                be.kineticForce = 0f;
                be.requiredKineticForce = 0f;
                changed = true;
            }
        }

        // 3. Состояние LIT
        boolean shouldBeLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(SolvationMachineBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(SolvationMachineBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 4. Выталкивание жидкости (активное)
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(back), back.getOpposite());
            if (neighbor != null) {
                changed |= FluidNetworkUtil.tryPushFluid(be.outputFluidHandler, neighbor);
            }
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (inputFluid.isEmpty() || !inputFluid.is(currentRecipe.getInputFluid().getFluid())) return false;
        if (inputFluid.getAmount() < currentRecipe.getInputFluidAmount()) return false;
        ItemStack slot = items.get(SLOT_INPUT);
        if (slot.isEmpty() || !slot.is(currentRecipe.getInputItem())) return false;
        if (slot.getCount() < currentRecipe.getInputItemAmount()) return false;

        FluidStack out = currentRecipe.getOutputFluid();
        if (outputFluid.isEmpty()) return true;
        if (!outputFluid.is(out.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= out.getAmount();
    }

    private void process() {
        if (currentRecipe == null) return;
        items.get(SLOT_INPUT).shrink(currentRecipe.getInputItemAmount());
        int toConsume = currentRecipe.getInputFluidAmount();
        inputFluid.shrink(toConsume);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack out = currentRecipe.getOutputFluid();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = out.copy();
            } else {
                int existAmt = outputFluid.getAmount();
                int newAmt   = out.getAmount();
                int mixTemp  = (fluidTemp(outputFluid) * existAmt + fluidTemp(out) * newAmt) / (existAmt + newAmt);
                int mixPres  = (fluidPressure(outputFluid) * existAmt + fluidPressure(out) * newAmt) / (existAmt + newAmt);
                outputFluid.grow(newAmt);
                applyAttributes(outputFluid, mixTemp, mixPres);
            }
        }

        kineticForce = 0f;
        setChanged();
    }

    // ── Fluid attribute helpers ───────────────────────────────────────────────

    private static int fluidTemp(FluidStack fs) {
        if (fs.isEmpty()) return 20;
        Integer t = fs.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

    private static int fluidPressure(FluidStack fs) {
        if (fs.isEmpty()) return 101;
        Integer p = fs.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : 101;
    }

    private static void applyAttributes(FluidStack fs, int temp, int pressure) {
        if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        else            fs.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressure != 101) fs.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
        else                 fs.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    // ВАЖНО: Убедитесь, что вы зарегистрировали Capabilities в главном классе мода!
    // Без регистрации через NeoForge Capabilities система не увидит ваши Handler-ы.

    private class InputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int index) { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return true; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || (!inputFluid.isEmpty() && !resource.is(inputFluid.getFluid()))) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = FluidNetworkUtil.blendInto(inputFluid, resource, toFill);
            return toFill;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int index) { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return false; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    public FluidStack getInputFluid() {
        return this.inputFluid;
    }

    public FluidStack getOutputFluid() {
        return this.outputFluid;
    }

    private static final int[] ALL_SLOTS = { SLOT_INPUT };
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return index == SLOT_INPUT; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_INPUT; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return false; }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        inputFluid = input.read("InputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("InputFluid", FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce", kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
    }
}