/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.assembler;

import com.dev1lroot.mcmods.omnitech.AssemblerLoader;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.gui.AssemblerMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.items.BlueprintItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
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
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class AssemblerBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, WorldlyContainer {

    public static final int STORAGE_SLOTS = 27;
    public static final int SLOT_BLUEPRINT = 27;
    public static final int SLOT_OUTPUT    = 28;
    public static final int SLOT_COUNT     = 29;

    // Automation can only inject into storage and extract from output; blueprint hidden.
    private static final int[] AUTOMATION_SLOTS;
    static {
        AUTOMATION_SLOTS = new int[STORAGE_SLOTS + 1];
        for (int i = 0; i < STORAGE_SLOTS; i++) AUTOMATION_SLOTS[i] = i;
        AUTOMATION_SLOTS[STORAGE_SLOTS] = SLOT_OUTPUT;
    }

    public static final float MAX_EU = 1000f;
    private static final float EU_PER_RECIPE = 1000f;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private float energyStored = 0f;
    private int craftProgress = 0;
    private int currentProcessingTime = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(energyStored * 10f);
                case 1 -> (int)(MAX_EU * 10f);
                case 2 -> craftProgress;
                case 3 -> currentProcessingTime;
                case 4 -> (int)(EU_PER_RECIPE * 10f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = value / 10f;
                case 2 -> craftProgress = value;
                case 3 -> currentProcessingTime = value;
            }
        }
        @Override public int getCount() { return 5; }
    };

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ASSEMBLER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.assembler");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new AssemblerMenu(containerId, inv, this, dataAccess);
    }

    // ── IElectricReceiver ─────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount) {
        float space = MAX_EU - energyStored;
        if (space <= 0f) return 0f;
        float accepted = Math.min(amount, space);
        energyStored += accepted;
        setChanged();
        return accepted;
    }

    // ── WorldlyContainer ──────────────────────────────────────────────────────

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return index < STORAGE_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == SLOT_OUTPUT) return false;
        if (index == SLOT_BLUEPRINT) return stack.getItem() instanceof BlueprintItem;
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AssemblerBlockEntity be) {
        boolean changed = false;

        Optional<AssemblerLoader.AssemblerRecipe> recipeOpt = be.findMatchingRecipe();

        if (recipeOpt.isPresent()) {
            AssemblerLoader.AssemblerRecipe recipe = recipeOpt.get();
            be.currentProcessingTime = recipe.processingTime();
            float euPerTick = EU_PER_RECIPE / recipe.processingTime();

            ItemStack output = recipe.createOutput();
            if (!output.isEmpty() && be.canAddOutput(output) && be.energyStored >= euPerTick) {
                be.energyStored -= euPerTick;
                if (be.energyStored < 0f) be.energyStored = 0f;
                be.craftProgress++;
                changed = true;

                if (be.craftProgress >= recipe.processingTime()) {
                    be.finishCraft(recipe, output);
                    be.craftProgress = 0;
                }
            }
        } else {
            if (be.craftProgress > 0 || be.currentProcessingTime > 0) {
                be.craftProgress = 0;
                be.currentProcessingTime = 0;
                changed = true;
            }
        }

        boolean isActive  = recipeOpt.isPresent() && be.craftProgress > 0;
        boolean wasActive = state.getValue(AssemblerBlock.LIT);
        if (wasActive != isActive) {
            level.setBlock(pos, state.setValue(AssemblerBlock.LIT, isActive), 3);
            changed = true;
        }

        if (changed) be.setChanged();
    }

    private Optional<AssemblerLoader.AssemblerRecipe> findMatchingRecipe() {
        ItemStack blueprintStack = items.get(SLOT_BLUEPRINT);
        if (blueprintStack.isEmpty() || !(blueprintStack.getItem() instanceof BlueprintItem)) {
            return Optional.empty();
        }

        String blueprintTag = blueprintStack.get(OmniTechDataComponents.RESEARCH_NAME.get());
        if (blueprintTag == null || blueprintTag.isEmpty()) return Optional.empty();

        Optional<AssemblerLoader.AssemblerRecipe> recipeOpt =
                AssemblerLoader.findByBlueprint(blueprintTag);

        if (recipeOpt.isEmpty()) return Optional.empty();

        AssemblerLoader.AssemblerRecipe recipe = recipeOpt.get();
        if (!hasComponents(recipe)) return Optional.empty();

        return recipeOpt;
    }

    private boolean hasComponents(AssemblerLoader.AssemblerRecipe recipe) {
        for (AssemblerLoader.Ingredient ingredient : recipe.components()) {
            int needed = ingredient.count();
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack s = items.get(i);
                if (!s.isEmpty() && ingredient.matches(s)) {
                    needed -= s.getCount();
                    if (needed <= 0) break;
                }
            }
            if (needed > 0) return false;
        }
        return true;
    }

    private boolean canAddOutput(ItemStack result) {
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void finishCraft(AssemblerLoader.AssemblerRecipe recipe, ItemStack result) {
        // Consume components from storage slots
        for (AssemblerLoader.Ingredient ingredient : recipe.components()) {
            int toConsume = ingredient.count();
            for (int i = 0; i < STORAGE_SLOTS && toConsume > 0; i++) {
                ItemStack s = items.get(i);
                if (!s.isEmpty() && ingredient.matches(s)) {
                    int take = Math.min(toConsume, s.getCount());
                    s.shrink(take);
                    toConsume -= take;
                }
            }
        }

        // Add to output slot
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, result.copy());
        } else {
            output.grow(result.getCount());
        }
    }

    public ContainerData getContainerData() { return dataAccess; }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getFloatOr("EnergyStored", 0f);
        craftProgress = input.getIntOr("CraftProgress", 0);
        currentProcessingTime = input.getIntOr("ProcessingTime", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("EnergyStored", energyStored);
        output.putInt("CraftProgress", craftProgress);
        output.putInt("ProcessingTime", currentProcessingTime);
    }
}
