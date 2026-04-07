package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
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

import java.util.Optional;

/**
 * Block entity for the Electric Furnace.
 *
 * <h3>Energy and cook model</h3>
 * <p>Each tick that a valid smelting recipe is present <em>and</em> the buffer
 * holds at least {@link #EU_PER_TICK} EU:
 * <ol>
 *   <li>Deduct {@link #EU_PER_TICK} (= {@link #EU_PER_RECIPE} / {@link #COOK_TIME}) from the buffer.</li>
 *   <li>Advance {@link #cookProgress} by 1.</li>
 *   <li>When {@code cookProgress} reaches {@link #COOK_TIME} the item is output
 *       and progress resets to 0.</li>
 * </ol>
 * <p>If the buffer is empty, progress <em>pauses</em> (holds its current value)
 * until EU arrives. If the input slot is cleared the progress resets to 0.
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – energyStored × 10 (fixed-point)</li>
 *   <li>1 – MAX_EU × 10 (bar scale)</li>
 *   <li>2 – cookProgress (0..COOK_TIME)</li>
 *   <li>3 – COOK_TIME</li>
 *   <li>4 – EU_PER_RECIPE × 10 (recipe cost for display)</li>
 * </ul>
 */
public class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    /** Total EU cost of one smelting recipe. */
    public static final float EU_PER_RECIPE = 100f;

    /** Maximum EU the internal buffer can hold. */
    public static final float MAX_EU = 800f;

    /** Ticks for one recipe to complete (5 s at 20 TPS). */
    public static final int COOK_TIME = 100;

    /** EU consumed per tick of cook progress (EU_PER_RECIPE / COOK_TIME). */
    private static final float EU_PER_TICK = EU_PER_RECIPE / COOK_TIME;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** EU in the buffer (0..MAX_EU). */
    private float energyStored = 0f;

    /** Cook progress (0..COOK_TIME). Pauses when energy is unavailable; resets when input is cleared. */
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

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricFurnaceBlockEntity be) {

        boolean changed = false;

        Optional<RecipeHolder<SmeltingRecipe>> recipeOpt =
                findRecipe(level, be.items.get(SLOT_INPUT));

        if (recipeOpt.isPresent() && be.canSmelt(recipeOpt.get().value())) {
            if (be.energyStored >= EU_PER_TICK) {
                // Consume 1 EU worth of progress
                be.energyStored -= EU_PER_TICK;
                if (be.energyStored < 0f) be.energyStored = 0f;
                be.cookProgress++;
                changed = true;

                if (be.cookProgress >= COOK_TIME) {
                    be.smelt(recipeOpt.get().value());
                    be.cookProgress = 0;
                }
            }
            // else: energy unavailable — progress pauses, nothing consumed
        } else {
            // No valid recipe — reset progress
            if (be.cookProgress > 0) {
                be.cookProgress = 0;
                changed = true;
            }
        }

        // LIT = actively cooking (progress > 0) or has energy and a smeltable item
        boolean hasRecipe = recipeOpt.isPresent();
        boolean isLit  = hasRecipe && (be.cookProgress > 0 || be.energyStored >= EU_PER_TICK);
        boolean wasLit = state.getValue(ElectricFurnaceBlock.LIT);

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.LIT, isLit), 3);
            changed = true;
        }

        if (changed) be.setChanged();
    }

    // ── Smelting helpers ──────────────────────────────────────────────────────

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

    // ── Persistence ───────────────────────────────────────────────────────────

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
