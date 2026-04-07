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
 * <ol>
 *   <li>EU arrives from the network into {@link #energyStored} (float, 0..EU_PER_RECIPE).</li>
 *   <li>When a recipe is present and {@code energyStored >= EU_PER_RECIPE}, deduct
 *       {@value #EU_PER_RECIPE} EU and start the cook timer ({@link #cookProgress}).</li>
 *   <li>The cook timer advances every tick regardless of energy. The buffer may
 *       refill during cooking — it will be ready for the next recipe immediately.</li>
 *   <li>When {@code cookProgress >= COOK_TIME} ({@value #COOK_TIME} ticks = 5 s),
 *       output the item and reset progress. If energy is already full, the next
 *       recipe starts in the same tick.</li>
 * </ol>
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – energyStored × 10 (fixed-point; divide by 10 on client)</li>
 *   <li>1 – EU_PER_RECIPE × 10</li>
 *   <li>2 – cookProgress (0..COOK_TIME)</li>
 *   <li>3 – COOK_TIME</li>
 * </ul>
 */
public class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    /** EU required to start one smelting recipe. */
    public static final float EU_PER_RECIPE = 100f;

    /** Maximum EU the internal buffer can hold. */
    public static final float MAX_EU = 800f;

    /** Ticks for one recipe to complete (5 seconds at 20 TPS). */
    public static final int COOK_TIME = 100;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** EU accumulated in the buffer (0..MAX_EU). Charges independently from cooking. */
    private float energyStored = 0f;

    /**
     * Cook timer (0..COOK_TIME). 0 = not cooking.
     * Once started (EU deducted), advances every tick until COOK_TIME.
     */
    private int cookProgress = 0;

    /** True while the cook timer is running. */
    private boolean isCooking = false;

    /**
     * ContainerData layout:
     * <ul>
     *   <li>0 – energyStored × 10</li>
     *   <li>1 – MAX_EU × 10 (bar scale)</li>
     *   <li>2 – cookProgress (0..COOK_TIME)</li>
     *   <li>3 – COOK_TIME</li>
     *   <li>4 – EU_PER_RECIPE × 10 (recipe cost for display)</li>
     * </ul>
     */
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

    /**
     * Accepts EU into the internal buffer (capped at {@link #EU_PER_RECIPE}).
     * The furnace never wastes EU — it only absorbs what fits.
     */
    @Override
    public boolean addElectricity(float amount) {
        float space = MAX_EU - energyStored;
        if (space <= 0f) return false;
        energyStored += Math.min(amount, space);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricFurnaceBlockEntity be) {

        boolean changed = false;

        // ── Start a new cook if buffer is full and not already cooking ─────────
        if (!be.isCooking && be.energyStored >= EU_PER_RECIPE) {
            Optional<RecipeHolder<SmeltingRecipe>> recipe =
                    findRecipe(level, be.items.get(SLOT_INPUT));

            if (recipe.isPresent() && be.canSmelt(recipe.get().value())) {
                be.energyStored -= EU_PER_RECIPE;
                if (be.energyStored < 0f) be.energyStored = 0f;
                be.isCooking    = true;
                be.cookProgress = 0;
                changed = true;
            }
        }

        // ── Advance cook timer ────────────────────────────────────────────────
        if (be.isCooking) {
            be.cookProgress++;
            changed = true;

            if (be.cookProgress >= COOK_TIME) {
                // Recipe complete — output item
                Optional<RecipeHolder<SmeltingRecipe>> recipe =
                        findRecipe(level, be.items.get(SLOT_INPUT));

                if (recipe.isPresent() && be.canSmelt(recipe.get().value())) {
                    be.smelt(recipe.get().value());
                }

                be.cookProgress = 0;
                be.isCooking    = false;

                // If buffer already has enough energy and item is still present, chain immediately
                if (be.energyStored >= EU_PER_RECIPE) {
                    Optional<RecipeHolder<SmeltingRecipe>> next =
                            findRecipe(level, be.items.get(SLOT_INPUT));
                    if (next.isPresent() && be.canSmelt(next.get().value())) {
                        be.energyStored -= EU_PER_RECIPE;
                        if (be.energyStored < 0f) be.energyStored = 0f;
                        be.isCooking    = true;
                        be.cookProgress = 0;
                    }
                }
            }
        }

        // ── LIT = buffer has energy OR is actively cooking ────────────────────
        boolean hasRecipe = !be.items.get(SLOT_INPUT).isEmpty()
                && findRecipe(level, be.items.get(SLOT_INPUT)).isPresent();
        boolean isLit  = hasRecipe && (be.energyStored > 0f || be.isCooking);
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
        isCooking    = input.getBooleanOr("IsCooking", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("EnergyStored", energyStored);
        output.putInt("CookProgress",   cookProgress);
        output.putBoolean("IsCooking",  isCooking);
    }
}
