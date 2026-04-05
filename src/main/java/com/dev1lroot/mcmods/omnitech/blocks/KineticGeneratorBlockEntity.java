package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.util.KineticNetworkUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorMenu;
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

public class KineticGeneratorBlockEntity extends BaseContainerBlockEntity implements IKineticSupplier {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;
    /** Fixed-point KF units produced per tick (10 = 1 KF). */
    public static final int KF_SUPPLY = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int burnTime    = 0;
    private int maxBurnTime = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) { case 0 -> burnTime; case 1 -> maxBurnTime; default -> 0; };
        }
        @Override public void set(int index, int value) {
            switch (index) { case 0 -> burnTime = value; case 1 -> maxBurnTime = value; }
        }
        @Override public int getCount() { return 2; }
    };

    public KineticGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.KF_GENERATOR.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.kf_generator");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }
    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new KineticGeneratorMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            KineticGeneratorBlockEntity be) {
        boolean wasLit = be.isLit();

        // Consume fuel
        if (be.isLit()) {
            be.burnTime--;
        }

        // Try to ignite a new fuel item if not burning
        if (!be.isLit()) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            int duration = getBurnDuration(fuel);
            if (duration > 0) {
                be.maxBurnTime = duration;
                be.burnTime    = duration;
                fuel.shrink(1);
            }
        }

        // Sync LIT blockstate
        if (wasLit != be.isLit()) {
            level.setBlock(pos, state.setValue(KineticGeneratorBlock.LIT, be.isLit()), 3);
        }

        // Walk the pipe network via BFS and deliver KF to every reachable machine
        if (be.isLit()) {
            KineticNetworkUtil.propagateKineticForce(level, pos, KF_SUPPLY);
        }

        be.setChanged();
    }

    @Override
    public int getKfSupply() { return isLit() ? KF_SUPPLY : 0; }

    public boolean isLit()       { return burnTime > 0; }
    public int getBurnTime()     { return burnTime; }
    public int getMaxBurnTime()  { return maxBurnTime; }
    public ContainerData getContainerData() { return dataAccess; }

    /** Returns the burn duration for the given fuel stack, in ticks. */
    public static int getBurnDuration(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL))        return 1600;
        if (stack.is(Items.COAL_BLOCK))                               return 16000;
        if (stack.is(Items.LAVA_BUCKET))                              return 20000;
        if (stack.is(Items.BLAZE_ROD))                                return 2400;
        if (stack.is(Items.OAK_PLANKS)    || stack.is(Items.SPRUCE_PLANKS) ||
            stack.is(Items.BIRCH_PLANKS)  || stack.is(Items.JUNGLE_PLANKS) ||
            stack.is(Items.ACACIA_PLANKS) || stack.is(Items.DARK_OAK_PLANKS)) return 300;
        if (stack.is(Items.STICK))                                    return 100;
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burnTime    = input.getIntOr("BurnTime",    0);
        maxBurnTime = input.getIntOr("MaxBurnTime", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime",    burnTime);
        output.putInt("MaxBurnTime", maxBurnTime);
    }
}
