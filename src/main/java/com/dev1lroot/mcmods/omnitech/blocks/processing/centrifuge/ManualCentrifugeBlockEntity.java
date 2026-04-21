package com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ManualCentrifugeMenu;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
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

import java.util.List;

public class ManualCentrifugeBlockEntity extends BaseContainerBlockEntity implements IKineticReceiver, WorldlyContainer {
    public static final int SLOT_INPUT    = 0;
    public static final int SLOT_OUTPUT_1 = 1;
    public static final int SLOT_OUTPUT_2 = 2;
    public static final int SLOT_OUTPUT_3 = 3;
    public static final int SLOT_OUTPUT_4 = 4;
    public static final int SLOT_OUTPUT_5 = 5;
    public static final int SLOT_OUTPUT_6 = 6;
    public static final int SLOT_OUTPUT_7 = 7;
    public static final int SLOT_OUTPUT_8 = 8;
    public static final int SLOT_OUTPUT_9 = 9;
    public static final int SLOT_COUNT    = 10;

    private static final int[] OUTPUT_SLOTS = {
            SLOT_OUTPUT_1, SLOT_OUTPUT_2, SLOT_OUTPUT_3,
            SLOT_OUTPUT_4, SLOT_OUTPUT_5, SLOT_OUTPUT_6,
            SLOT_OUTPUT_7, SLOT_OUTPUT_8, SLOT_OUTPUT_9
    };

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /** Ticks before LIT turns off after the last kinetic-force pulse. 10 ticks = 500 ms. */
    public static final int SPIN_DECAY_TICKS = 20;

    private int kineticForce = 0;
    private int requiredKineticForce = 0;
    private int spinningTimer = 0;
    private ManualCentrifugeRecipe currentRecipe = null;
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

    public ManualCentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.MANUAL_CENTRIFUGE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.manual_centrifuge");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new ManualCentrifugeMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ManualCentrifugeBlockEntity be) {
        if (be.spinningTimer > 0) be.spinningTimer--;

        ManualCentrifugeRecipeManager.findRecipe(be.items.get(SLOT_INPUT)).ifPresentOrElse(
                recipe -> {
                    if (!recipe.getId().equals(be.currentRecipeId)) {
                        be.currentRecipe = recipe;
                        be.currentRecipeId = recipe.getId();
                        be.kineticForce = 0;
                        be.requiredKineticForce = recipe.getRequiredKineticForce();
                        be.setChanged();
                    }
                },
                () -> {
                    if (be.currentRecipe != null) {
                        be.currentRecipe = null;
                        be.currentRecipeId = null;
                        be.kineticForce = 0;
                        be.requiredKineticForce = 0;
                        be.setChanged();
                    }
                }
        );

        if (!be.hasOutputSpace() && (be.kineticForce != 0 || be.spinningTimer != 0)) {
            be.kineticForce = 0;
            be.spinningTimer = 0;
            be.setChanged();
        }

        boolean shouldBeLit = be.spinningTimer > 0;
        if (state.getValue(ManualCentrifugeBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(ManualCentrifugeBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);
        }
    }

    /** Only draw from the KF network when there is something to process. */
    @Override
    public float getKfDemand() { return currentRecipe != null ? 0.1F : 0; }

    @Override
    public boolean addKineticForce(float amount) {
        if (currentRecipe == null) return false;
        if (!hasOutputSpace()) return false;

        spinningTimer = SPIN_DECAY_TICKS;
        kineticForce += amount;
        setChanged();

        if (kineticForce >= requiredKineticForce) {
            return process();
        }
        return true;
    }

    private boolean hasOutputSpace() {
        for (int slot : OUTPUT_SLOTS) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private boolean process() {
        if (currentRecipe == null) return false;

        RandomSource random = level != null ? level.getRandom() : RandomSource.create();
        List<ItemStack> rolled = currentRecipe.rollOutputs(random);

        if (!canFitOutputs(rolled))
        {
            kineticForce = 0;
            return false;
        }

        items.get(SLOT_INPUT).shrink(1);

        int slotIdx = 0;
        for (ItemStack result : rolled) {
            while (slotIdx < OUTPUT_SLOTS.length) {
                ItemStack slotStack = items.get(OUTPUT_SLOTS[slotIdx]);
                if (slotStack.isEmpty()) {
                    items.set(OUTPUT_SLOTS[slotIdx], result.copy());
                    slotIdx++;
                    break;
                } else if (ItemStack.isSameItemSameComponents(slotStack, result)
                        && slotStack.getCount() + result.getCount() <= slotStack.getMaxStackSize()) {
                    slotStack.grow(result.getCount());
                    slotIdx++;
                    break;
                }
                slotIdx++;
            }
        }

        kineticForce = 0;
        setChanged();
        return true;
    }

    private boolean canFitOutputs(List<ItemStack> rolled) {
        ItemStack[] snapshot = new ItemStack[OUTPUT_SLOTS.length];
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            snapshot[i] = items.get(OUTPUT_SLOTS[i]).copy();
        }

        for (ItemStack result : rolled) {
            boolean placed = false;
            for (int i = 0; i < snapshot.length; i++) {
                if (snapshot[i].isEmpty()) {
                    snapshot[i] = result.copy();
                    placed = true;
                    break;
                } else if (ItemStack.isSameItemSameComponents(snapshot[i], result)
                        && snapshot[i].getCount() + result.getCount() <= snapshot[i].getMaxStackSize()) {
                    snapshot[i].grow(result.getCount());
                    placed = true;
                    break;
                }
            }
            if (!placed) return false;
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
        this.spinningTimer = input.getIntOr("SpinningTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("KineticForce", this.kineticForce);
        output.putInt("RequiredKineticForce", this.requiredKineticForce);
        output.putInt("SpinningTimer", this.spinningTimer);
    }

    /** Output slots are extraction-only; input slot accepts items. */
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index == SLOT_INPUT;
    }

    // Logic for Sides (WorldlyContainer)

    @Override
    public int[] getSlotsForFace(Direction direction) {
        // Assume the block has a FACING property like ManualMacerator
        Direction facing = getBlockState().getValue(ManualCentrifugeBlock.FACING);

        if (direction == Direction.DOWN || direction == facing) {
            return OUTPUT_SLOTS;
        }

        return new int[]{SLOT_INPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return index == SLOT_INPUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index == SLOT_INPUT) {
            return false;
        }

        Direction facing = getBlockState().getValue(ManualCentrifugeBlock.FACING);
        return direction == Direction.DOWN || direction == facing;
    }
}