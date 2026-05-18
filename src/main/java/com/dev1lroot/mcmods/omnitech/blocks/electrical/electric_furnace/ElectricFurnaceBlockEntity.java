/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_furnace;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.gui.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, WorldlyContainer {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    private static final int[] SLOTS_FOR_UP = new int[]{SLOT_INPUT};
    private static final int[] SLOTS_FOR_SIDES = new int[]{SLOT_OUTPUT};

    public static final float EU_PER_RECIPE = 100f;
    public static final float MAX_EU = 800f;
    public static final int COOK_TIME = 100;
    private static final float EU_PER_TICK = EU_PER_RECIPE / COOK_TIME;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private float energyStored = 0f;
    private int cookProgress = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(energyStored * 10f);
                case 1 -> (int)(MAX_EU * 10f);
                case 2 -> cookProgress;
                case 3 -> COOK_TIME;
                case 4 -> (int)(EU_PER_RECIPE * 10f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = value / 10f;
                case 2 -> cookProgress = value;
            }
        }
        @Override public int getCount() { return 5; }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_FURNACE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_furnace");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricFurnaceMenu(containerId, inv, this, dataAccess);
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

    // ── WorldlyContainer (Sided Logic) ────────────────────────────────────────

    @Override
    public int[] getSlotsForFace(Direction direction) {
        // Получаем направление лицевой панели печи
        Direction facing = getBlockState().getValue(ElectricFurnaceBlock.FACING);

        // Если смотрят снизу или с лицевой стороны — даем доступ к выходу
        if (direction == Direction.DOWN || direction == facing) {
            return new int[]{SLOT_OUTPUT};
        }

        // В остальных случаях (верх и бока) — доступ к входу
        return new int[]{SLOT_INPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        // Разрешаем класть только во входной слот
        return index == SLOT_INPUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        // Запрещаем извлекать что-либо из входного слота
        if (index == SLOT_INPUT) {
            return false;
        }

        // Из выходного слота можно забирать только снизу или с лицевой стороны
        Direction facing = getBlockState().getValue(ElectricFurnaceBlock.FACING);
        return direction == Direction.DOWN || direction == facing;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index == SLOT_INPUT;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ElectricFurnaceBlockEntity be) {

        boolean changed = false;

        Optional<RecipeHolder<SmeltingRecipe>> recipeOpt =
                findRecipe(level, be.items.get(SLOT_INPUT));

        if (recipeOpt.isPresent() && be.canSmelt(recipeOpt.get().value())) {
            if (be.energyStored >= EU_PER_TICK) {
                be.energyStored -= EU_PER_TICK;
                if (be.energyStored < 0f) be.energyStored = 0f;
                be.cookProgress++;
                changed = true;

                if (be.cookProgress >= COOK_TIME) {
                    be.smelt(recipeOpt.get().value());
                    be.cookProgress = 0;
                }
            }
        } else {
            if (be.cookProgress > 0) {
                be.cookProgress = 0;
                changed = true;
            }
        }

        boolean hasRecipe = recipeOpt.isPresent();
        boolean isLit  = hasRecipe && (be.cookProgress > 0 || be.energyStored >= EU_PER_TICK);
        boolean wasLit = state.getValue(ElectricFurnaceBlock.LIT);

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.LIT, isLit), 3);
            changed = true;
        }

        if (changed) be.setChanged();
    }

    private static Optional<RecipeHolder<SmeltingRecipe>> findRecipe(Level level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        MinecraftServer server = level.getServer();
        if (server == null) return Optional.empty();
        return server.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    private boolean canSmelt(SmeltingRecipe recipe) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(items.get(SLOT_INPUT)));
        if (result.isEmpty()) return false;
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void smelt(SmeltingRecipe recipe) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(items.get(SLOT_INPUT)));
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        items.get(SLOT_INPUT).shrink(1);
    }

    public ContainerData getContainerData() { return dataAccess; }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getFloatOr("EnergyStored", 0f);
        cookProgress = input.getIntOr("CookProgress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("EnergyStored", energyStored);
        output.putInt("CookProgress",   cookProgress);
    }
}