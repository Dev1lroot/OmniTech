package com.dev1lroot.mcmods.omnitech.blocks.thermal.alloy_furnace;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceMenu;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AlloyFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_INPUT_1 = 0;
    public static final int SLOT_INPUT_2 = 1;
    public static final int SLOT_INPUT_3 = 2;
    public static final int SLOT_FUEL = 3;
    public static final int SLOT_OUTPUT_1 = 4;
    public static final int SLOT_OUTPUT_2 = 5;
    public static final int SLOT_COUNT = 6;

    private static final int[] OUTPUT_SLOTS   = {SLOT_OUTPUT_1, SLOT_OUTPUT_2};
    private static final int[] INPUT_SLOTS    = {SLOT_INPUT_1, SLOT_INPUT_2, SLOT_INPUT_3};
    private static final int[] INPUT_FUEL     = {SLOT_INPUT_1, SLOT_INPUT_2, SLOT_INPUT_3, SLOT_FUEL};

    private static final int DEFAULT_COOK_TIME = 200;
    private static final int MAX_TEMPERATURE = 2000;
    private static final int TEMPERATURE_CHANGE_INTERVAL = 4; // ticks

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private AlloyFurnaceRecipe currentRecipe = null;

    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int cookProgress = 0;
    private int cookTotalTime = DEFAULT_COOK_TIME;
    private int temperature = 0;
    private int requiredTemperature = 0;
    private int temperatureTick = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> cookProgress;
                case 3 -> cookTotalTime;
                case 4 -> temperature;
                case 5 -> requiredTemperature;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> maxBurnTime = value;
                case 2 -> cookProgress = value;
                case 3 -> cookTotalTime = value;
                case 4 -> temperature = value;
                case 5 -> requiredTemperature = value;
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public AlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ALLOY_FURNACE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.alloy_furnace");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new AlloyFurnaceMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    // ── WorldlyContainer — side-aware slot access ────────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) return OUTPUT_SLOTS;
        if (side == Direction.UP)   return INPUT_SLOTS;
        Direction facing = getBlockState().getValue(AlloyFurnaceBlock.FACING);
        if (side == facing.getOpposite()) return OUTPUT_SLOTS;
        return INPUT_FUEL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT_1 || slot == SLOT_OUTPUT_2;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_OUTPUT_1 || slot == SLOT_OUTPUT_2) return false;
        if (slot == SLOT_FUEL) return getBurnDuration(stack) > 0;
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlloyFurnaceBlockEntity blockEntity) {
        boolean wasLit = blockEntity.isLit();
        boolean changed = false;

        // Burn fuel
        if (blockEntity.isLit()) {
            blockEntity.burnTime--;
        }

        // Temperature change every 4 ticks
        blockEntity.temperatureTick++;
        if (blockEntity.temperatureTick >= TEMPERATURE_CHANGE_INTERVAL) {
            blockEntity.temperatureTick = 0;

            if (blockEntity.isLit()) {
                // Increase temperature while burning
                if (blockEntity.temperature < MAX_TEMPERATURE) {
                    blockEntity.temperature++;
                    changed = true;
                }
            } else {
                // Decrease temperature when not burning
                if (blockEntity.temperature > 0) {
                    blockEntity.temperature--;
                    changed = true;
                }
            }
        }

        // Check for overheat - replace with lava
        if (blockEntity.temperature >= MAX_TEMPERATURE) {
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
            return; // Block is destroyed, stop processing
        }

        boolean hasRecipe = blockEntity.hasValidRecipe();

        // Update required temperature for GUI
        int newRequiredTemp = blockEntity.getRecipeRequiredTemperature();
        if (blockEntity.requiredTemperature != newRequiredTemp) {
            blockEntity.requiredTemperature = newRequiredTemp;
            changed = true;
        }

        if (hasRecipe) {
            // Try to consume fuel if not burning and we have a valid recipe
            if (!blockEntity.isLit()) {
                blockEntity.burnTime = blockEntity.getBurnDuration(blockEntity.items.get(SLOT_FUEL));
                blockEntity.maxBurnTime = blockEntity.burnTime;

                if (blockEntity.isLit()) {
                    changed = true;
                    ItemStack fuelStack = blockEntity.items.get(SLOT_FUEL);
                    if (!fuelStack.isEmpty()) {
                        fuelStack.shrink(1);
                    }
                }
            }

            // Check if we can smelt (includes temperature check)
            if (blockEntity.canSmelt()) {
                blockEntity.cookProgress++;

                if (blockEntity.cookProgress >= blockEntity.cookTotalTime) {
                    blockEntity.smelt();
                    blockEntity.cookProgress = 0;
                    changed = true;
                }
            } else {
                // Reset progress if temperature dropped or can't smelt
                if (blockEntity.cookProgress > 0) {
                    blockEntity.cookProgress = 0;
                    changed = true;
                }
            }
        } else {
            if (blockEntity.cookProgress > 0) {
                blockEntity.cookProgress = 0;
                changed = true;
            }
        }

        if (wasLit != blockEntity.isLit()) {
            changed = true;
            level.setBlock(pos, state.setValue(AlloyFurnaceBlock.LIT, blockEntity.isLit()), 3);
        }

        if (changed) {
            blockEntity.setChanged();
        }
    }

    private int getRecipeRequiredTemperature() {
        if (currentRecipe != null) {
            return currentRecipe.getMinTemperature();
        }
        return 0;
    }

    private boolean hasValidRecipe() {
        List<ItemStack> inputs = getInputStacks();
        Optional<AlloyFurnaceRecipe> recipe = AlloyFurnaceRecipeManager.findRecipe(inputs);
        if (recipe.isPresent()) {
            currentRecipe = recipe.get();
            return true;
        }
        currentRecipe = null;
        return false;
    }

    private List<ItemStack> getInputStacks() {
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(items.get(SLOT_INPUT_1));
        inputs.add(items.get(SLOT_INPUT_2));
        inputs.add(items.get(SLOT_INPUT_3));
        return inputs;
    }

    private boolean canSmelt() {
        if (currentRecipe == null) return false;

        // Check temperature requirement
        int requiredTemp = getRecipeRequiredTemperature();
        if (temperature < requiredTemp) return false;

        // Check output space
        List<ItemStack> outputs = currentRecipe.getOutputStacks();
        if (outputs.isEmpty()) return false;

        ItemStack result = outputs.get(0);
        ItemStack output1 = items.get(SLOT_OUTPUT_1);

        if (output1.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output1, result)) return false;
        return output1.getCount() + result.getCount() <= output1.getMaxStackSize();
    }

    private void smelt() {
        if (currentRecipe == null) return;

        // Consume ingredients
        for (Item ingredient : currentRecipe.getIngredients()) {
            for (int i = 0; i < 3; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty() && stack.is(ingredient)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        // Add outputs
        List<ItemStack> outputs = currentRecipe.getOutputStacks();
        if (!outputs.isEmpty()) {
            ItemStack result = outputs.get(0);
            ItemStack output1 = items.get(SLOT_OUTPUT_1);
            if (output1.isEmpty()) {
                items.set(SLOT_OUTPUT_1, result.copy());
            } else {
                output1.grow(result.getCount());
            }

            // Second output if present
            if (outputs.size() > 1) {
                ItemStack result2 = outputs.get(1);
                ItemStack output2 = items.get(SLOT_OUTPUT_2);
                if (output2.isEmpty()) {
                    items.set(SLOT_OUTPUT_2, result2.copy());
                } else if (ItemStack.isSameItemSameComponents(output2, result2)) {
                    output2.grow(result2.getCount());
                }
            }
        }
    }

    private boolean isLit() {
        return burnTime > 0;
    }

    private int getBurnDuration(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        // Simple fuel check - coal burns for 1600 ticks
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 1600;
        if (stack.is(Items.COAL_BLOCK)) return 16000;
        if (stack.is(Items.LAVA_BUCKET)) return 20000;
        if (stack.is(Items.BLAZE_ROD)) return 2400;
        // Wood items
        if (stack.is(Items.OAK_PLANKS) || stack.is(Items.SPRUCE_PLANKS) ||
            stack.is(Items.BIRCH_PLANKS) || stack.is(Items.JUNGLE_PLANKS) ||
            stack.is(Items.ACACIA_PLANKS) || stack.is(Items.DARK_OAK_PLANKS)) return 300;
        if (stack.is(Items.STICK)) return 100;
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.burnTime = input.getIntOr("BurnTime", 0);
        this.maxBurnTime = input.getIntOr("MaxBurnTime", 0);
        this.cookProgress = input.getIntOr("CookProgress", 0);
        this.cookTotalTime = input.getIntOr("CookTotalTime", DEFAULT_COOK_TIME);
        this.temperature = input.getIntOr("Temperature", 0);
        this.temperatureTick = input.getIntOr("TemperatureTick", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("MaxBurnTime", this.maxBurnTime);
        output.putInt("CookProgress", this.cookProgress);
        output.putInt("CookTotalTime", this.cookTotalTime);
        output.putInt("Temperature", this.temperature);
        output.putInt("TemperatureTick", this.temperatureTick);
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }
}
