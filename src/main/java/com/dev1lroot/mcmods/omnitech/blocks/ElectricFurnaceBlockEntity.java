package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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
 * <p>Implements {@link IElectricReceiver} to consume EU from the electric
 * network. Smelts items using the standard {@code minecraft:smelting} recipe
 * type — any recipe valid in a vanilla Furnace also works here.
 *
 * <p>Power model: the furnace requires {@link #EU_PER_TICK} EU/tick to
 * progress. When EU is delivered via {@link #addElectricity} a powered timer
 * resets; smelting progress advances only while the timer is positive.
 */
public class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver {

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    /** EU/tick consumed while smelting. */
    public static final int EU_PER_TICK = 10;
    /** Default smelt time in ticks (same as vanilla furnace). */
    private static final int DEFAULT_SMELT_TIME = 200;
    /** Ticks before the furnace powers down without fresh EU. */
    private static final int POWERED_DECAY_TICKS = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int cookProgress  = 0;
    private int cookTotalTime = DEFAULT_SMELT_TIME;
    private int poweredTimer  = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> cookProgress;
                case 1 -> cookTotalTime;
                case 2 -> poweredTimer > 0 ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> cookProgress  = value;
                case 1 -> cookTotalTime = value;
            }
        }
        @Override public int getCount() { return 3; }
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
    public boolean addElectricity(int amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricFurnaceBlockEntity be) {

        boolean wasLit = state.getValue(ElectricFurnaceBlock.LIT);

        if (be.poweredTimer > 0) be.poweredTimer--;

        boolean isPowered = be.poweredTimer > 0;

        Optional<RecipeHolder<SmeltingRecipe>> recipe = findRecipe(level, be.items.get(SLOT_INPUT));

        if (isPowered && recipe.isPresent() && be.canSmelt(recipe.get().value())) {
            be.cookProgress++;
            if (be.cookProgress >= be.cookTotalTime) {
                be.smelt(recipe.get().value());
                be.cookProgress = 0;
            }
        } else {
            if (be.cookProgress > 0) be.cookProgress--;
        }

        boolean isLit = isPowered && recipe.isPresent();
        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.LIT, isLit), 3);
        }

        be.setChanged();
    }

    // ── Smelting logic ────────────────────────────────────────────────────────

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
        cookProgress  = input.getIntOr("CookProgress",  0);
        cookTotalTime = input.getIntOr("CookTotalTime", DEFAULT_SMELT_TIME);
        poweredTimer  = input.getIntOr("PoweredTimer",  0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("CookProgress",  cookProgress);
        output.putInt("CookTotalTime", cookTotalTime);
        output.putInt("PoweredTimer",  poweredTimer);
    }
}
