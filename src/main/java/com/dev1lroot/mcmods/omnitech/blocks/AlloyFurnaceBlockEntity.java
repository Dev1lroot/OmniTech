package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class AlloyFurnaceBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_INPUT_1 = 0;
    public static final int SLOT_INPUT_2 = 1;
    public static final int SLOT_INPUT_3 = 2;
    public static final int SLOT_FUEL = 3;
    public static final int SLOT_OUTPUT_1 = 4;
    public static final int SLOT_OUTPUT_2 = 5;
    public static final int SLOT_COUNT = 6;

    private static final int DEFAULT_COOK_TIME = 200;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int cookProgress = 0;
    private int cookTotalTime = DEFAULT_COOK_TIME;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> cookProgress;
                case 3 -> cookTotalTime;
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
            }
        }

        @Override
        public int getCount() {
            return 4;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlloyFurnaceBlockEntity blockEntity) {
        boolean wasLit = blockEntity.isLit();
        boolean changed = false;

        if (blockEntity.isLit()) {
            blockEntity.burnTime--;
        }

        boolean hasRecipe = blockEntity.hasValidRecipe();

        if (hasRecipe) {
            if (!blockEntity.isLit() && blockEntity.canSmelt()) {
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

            if (blockEntity.isLit() && blockEntity.canSmelt()) {
                blockEntity.cookProgress++;

                if (blockEntity.cookProgress >= blockEntity.cookTotalTime) {
                    blockEntity.smelt();
                    blockEntity.cookProgress = 0;
                    changed = true;
                }
            } else if (!blockEntity.canSmelt()) {
                blockEntity.cookProgress = 0;
            }
        } else {
            blockEntity.cookProgress = 0;
        }

        if (wasLit != blockEntity.isLit()) {
            changed = true;
            level.setBlock(pos, state.setValue(AlloyFurnaceBlock.LIT, blockEntity.isLit()), 3);
        }

        if (changed) {
            blockEntity.setChanged();
        }
    }

    private boolean hasValidRecipe() {
        // Check for iron ingot + coal = steel ingot recipe
        ItemStack input1 = items.get(SLOT_INPUT_1);
        ItemStack input2 = items.get(SLOT_INPUT_2);
        ItemStack input3 = items.get(SLOT_INPUT_3);

        boolean hasIron = input1.is(Items.IRON_INGOT) || input2.is(Items.IRON_INGOT) || input3.is(Items.IRON_INGOT);
        boolean hasCoal = input1.is(Items.COAL) || input2.is(Items.COAL) || input3.is(Items.COAL);

        return hasIron && hasCoal;
    }

    private boolean canSmelt() {
        if (!hasValidRecipe()) return false;

        ItemStack result = new ItemStack(OmniTechItems.STEEL_INGOT.get());
        ItemStack output1 = items.get(SLOT_OUTPUT_1);

        if (output1.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output1, result)) return false;
        return output1.getCount() + result.getCount() <= output1.getMaxStackSize();
    }

    private void smelt() {
        ItemStack result = new ItemStack(OmniTechItems.STEEL_INGOT.get());

        // Consume one iron ingot
        for (int i = 0; i < 3; i++) {
            if (items.get(i).is(Items.IRON_INGOT)) {
                items.get(i).shrink(1);
                break;
            }
        }

        // Consume one coal
        for (int i = 0; i < 3; i++) {
            if (items.get(i).is(Items.COAL)) {
                items.get(i).shrink(1);
                break;
            }
        }

        // Add result
        ItemStack output1 = items.get(SLOT_OUTPUT_1);
        if (output1.isEmpty()) {
            items.set(SLOT_OUTPUT_1, result.copy());
        } else {
            output1.grow(result.getCount());
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("BurnTime", this.burnTime);
        output.putInt("MaxBurnTime", this.maxBurnTime);
        output.putInt("CookProgress", this.cookProgress);
        output.putInt("CookTotalTime", this.cookTotalTime);
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }
}
