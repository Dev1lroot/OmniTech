/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.processing.press;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.PressMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.PressRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.PressRecipeManager;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
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

import java.util.Objects;

/**
 * Press — squeezes one item at a time into a fluid plus a leftover item (apples → apple juice +
 * apple puree, grapes → grape juice + grape pomace), see {@link PressRecipe}. Runs on kinetic
 * force like the Manual Macerator; the juice collects in an output tank and is pushed out the
 * back, blending with whatever juice is already there.
 *
 * <p>Slots: {@link #SLOT_INPUT} (hoppers insert from the top and sides), {@link #SLOT_OUTPUT}
 * (hoppers extract from below).
 */
public class PressBlockEntity extends BaseContainerBlockEntity implements IKineticReceiver, WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce = 0f;
    private float requiredKineticForce = 0f;
    private PressRecipe currentRecipe = null;
    private String currentRecipeId = null;
    /** Last output fluid sent to clients; the GUI reads the fluid type from the synced entity. */
    private FluidStack syncedFluid = FluidStack.EMPTY;

    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) (kineticForce * 100f);
                case 1 -> (int) (requiredKineticForce * 100f);
                case 2 -> outputFluid.getAmount();
                case 3 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce = value / 100f;
                case 1 -> requiredKineticForce = value / 100f;
            }
        }
        @Override public int getCount() { return 4; }
    };

    public PressBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PRESS.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.press");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new PressMenu(containerId, inv, this, dataAccess);
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

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public float getKfDemand() {
        return canProcess() ? 0.1f : 0f;
    }

    @Override
    public boolean addKineticForce(float amount) {
        if (!canProcess()) return false;
        kineticForce += amount;
        setChanged();
        if (kineticForce >= requiredKineticForce) process();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PressBlockEntity be) {
        boolean changed = false;

        PressRecipe recipe = PressRecipeManager.findRecipe(be.items.get(SLOT_INPUT)).orElse(null);
        String recipeId = recipe != null ? recipe.getId() : null;
        if (!Objects.equals(recipeId, be.currentRecipeId)) {
            be.currentRecipe = recipe;
            be.currentRecipeId = recipeId;
            be.kineticForce = 0f;
            be.requiredKineticForce = recipe != null ? recipe.getRequiredKineticForce() : 0f;
            changed = true;
        }

        boolean shouldBeLit = be.canProcess();
        if (state.getValue(PressBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(PressBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // Push the juice out the back
        if (!be.outputFluid.isEmpty()) {
            Direction back = state.getValue(PressBlock.FACING).getOpposite();
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(back), back.getOpposite());
            if (neighbor != null) changed |= FluidNetworkUtil.tryPushFluid(be.outputFluidHandler, neighbor);
        }

        if (!FluidStack.isSameFluidSameComponents(be.outputFluid, be.syncedFluid)) {
            be.syncedFluid = be.outputFluid.copy();
            changed = true;
        }

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** A recipe for the input, room for all its juice, and room for its pulp at full count. */
    private boolean canProcess() {
        if (currentRecipe == null || !currentRecipe.matches(items.get(SLOT_INPUT))) return false;

        FluidStack juice = currentRecipe.getOutputFluid();
        if (!juice.isEmpty() && OUTPUT_TANK_CAPACITY - outputFluid.getAmount() < juice.getAmount()) return false;

        ItemStack pulp = currentRecipe.getOutputItem();
        if (pulp.isEmpty()) return true;
        ItemStack slot = items.get(SLOT_OUTPUT);
        if (slot.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(slot, pulp) && slot.getCount() + pulp.getCount() <= slot.getMaxStackSize();
    }

    private void process() {
        if (level == null) return;
        items.get(SLOT_INPUT).shrink(1);

        FluidStack juice = currentRecipe.getOutputFluid();
        if (!juice.isEmpty()) {
            outputFluid = FluidNetworkUtil.blendInto(outputFluid, FluidResource.of(juice), juice.getAmount());
        }

        ItemStack pulp = currentRecipe.rollOutputItem(level.getRandom());
        if (!pulp.isEmpty()) {
            ItemStack slot = items.get(SLOT_OUTPUT);
            if (slot.isEmpty()) items.set(SLOT_OUTPUT, pulp);
            else slot.grow(pulp.getCount());
        }

        kineticForce = 0f;
        setChanged();
    }

    // ── Output tank ───────────────────────────────────────────────────────────

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

    public FluidStack getOutputFluid() { return outputFluid; }

    // ── WorldlyContainer — fruit in from the top/sides, pulp out the bottom ────

    private static final int[] INPUT_SLOTS = { SLOT_INPUT };
    private static final int[] OUTPUT_SLOTS = { SLOT_OUTPUT };

    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS; }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return index == SLOT_INPUT; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return index == SLOT_INPUT; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return index == SLOT_OUTPUT; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce = input.getFloatOr("KineticForce", 0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce", kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
    }
}
