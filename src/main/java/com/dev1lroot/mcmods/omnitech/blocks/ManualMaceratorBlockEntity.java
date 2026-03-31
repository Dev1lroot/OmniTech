package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ManualMaceratorMenu;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.Optional;

public class ManualMaceratorBlockEntity extends BaseContainerBlockEntity implements IKineticReceiver {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT_1 = 1;
    public static final int SLOT_OUTPUT_2 = 2;
    public static final int SLOT_OUTPUT_3 = 3;
    public static final int SLOT_COUNT = 4;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int kineticForce = 0;
    private int requiredKineticForce = 0;
    private ManualMaceratorRecipe currentRecipe = null;
    private String currentRecipeId = null;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> kineticForce;
                case 1 -> requiredKineticForce;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce = value;
                case 1 -> requiredKineticForce = value;
            }
        }

        @Override
        public int getCount() { return 2; }
    };

    public ManualMaceratorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.MANUAL_MACERATOR.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.manual_macerator");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ManualMaceratorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManualMaceratorBlockEntity be) {
        Optional<ManualMaceratorRecipe> found = ManualMaceratorRecipeManager.findRecipe(be.items.get(SLOT_INPUT));
        if (found.isPresent()) {
            ManualMaceratorRecipe recipe = found.get();
            // Recipe changed — reset kinetic force
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe = recipe;
                be.currentRecipeId = recipe.getId();
                be.kineticForce = 0;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                be.setChanged();
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe = null;
                be.currentRecipeId = null;
                be.kineticForce = 0;
                be.requiredKineticForce = 0;
                be.setChanged();
            }
        }
    }

    /**
     * Called by the Crank block when the player turns the crank.
     * @return true if the force was accepted (recipe exists), false otherwise
     */
    public boolean addKineticForce(int amount) {
        if (currentRecipe == null) return false;

        kineticForce += amount;
        setChanged();

        if (kineticForce >= requiredKineticForce) {
            process();
        }
        return true;
    }

    private void process() {
        if (currentRecipe == null) return;
        if (!canOutput(currentRecipe)) return;

        // Consume input
        items.get(SLOT_INPUT).shrink(1);

        // Place outputs
        List<ItemStack> outputs = currentRecipe.getOutputStacks();
        int[] outputSlots = {SLOT_OUTPUT_1, SLOT_OUTPUT_2, SLOT_OUTPUT_3};
        for (int i = 0; i < outputs.size() && i < outputSlots.length; i++) {
            ItemStack result = outputs.get(i);
            ItemStack slotStack = items.get(outputSlots[i]);
            if (slotStack.isEmpty()) {
                items.set(outputSlots[i], result.copy());
            } else if (ItemStack.isSameItemSameComponents(slotStack, result)) {
                slotStack.grow(result.getCount());
            }
        }

        kineticForce = 0;
        setChanged();
    }

    private boolean canOutput(ManualMaceratorRecipe recipe) {
        List<ItemStack> outputs = recipe.getOutputStacks();
        int[] outputSlots = {SLOT_OUTPUT_1, SLOT_OUTPUT_2, SLOT_OUTPUT_3};
        for (int i = 0; i < outputs.size() && i < outputSlots.length; i++) {
            ItemStack result = outputs.get(i);
            ItemStack slotStack = items.get(outputSlots[i]);
            if (slotStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slotStack, result)) return false;
            if (slotStack.getCount() + result.getCount() > slotStack.getMaxStackSize()) return false;
        }
        return true;
    }

    public ContainerData getContainerData() { return dataAccess; }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.kineticForce = input.getIntOr("KineticForce", 0);
        this.requiredKineticForce = input.getIntOr("RequiredKineticForce", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("KineticForce", this.kineticForce);
        output.putInt("RequiredKineticForce", this.requiredKineticForce);
    }
}
