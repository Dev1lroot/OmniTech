package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class KineticGeneratorBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;
    /** Kinetic Force units pushed to each adjacent receiver per tick while burning. */
    public static final int KF_PER_TICK = 1;

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
            propagateKineticForce(level, pos);
        }

        be.setChanged();
    }

    /**
     * BFS from the generator through connected {@link KineticPipeBlock} chains,
     * delivering {@link #KF_PER_TICK} to every {@link IKineticReceiver} reachable
     * at the end of a pipe chain (or directly adjacent with no pipe in between).
     *
     * <p>Pipe traversal rules:
     * <ul>
     *   <li>A pipe can only be entered from a direction that matches its {@code AXIS}.
     *       (An X-axis pipe connects only on its east/west faces, etc.)</li>
     *   <li>Pipes are transparent to the BFS — power travels through any number of
     *       aligned pipes without limit.</li>
     *   <li>Non-pipe blocks terminate the BFS branch; if they implement
     *       {@link IKineticReceiver}, they receive KF.</li>
     * </ul>
     */
    private static void propagateKineticForce(Level level, BlockPos source) {
        record Step(BlockPos pos, Direction.Axis entryAxis) {}

        Set<BlockPos>      visited  = new HashSet<>();
        ArrayDeque<Step>   queue    = new ArrayDeque<>();

        visited.add(source);
        for (Direction dir : Direction.values()) {
            queue.add(new Step(source.relative(dir), dir.getAxis()));
        }

        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (visited.contains(step.pos())) continue;
            visited.add(step.pos());

            BlockState state = level.getBlockState(step.pos());

            if (state.getBlock() instanceof KineticPipeBlock) {
                Direction.Axis pipeAxis = state.getValue(KineticPipeBlock.AXIS);

                // Pipes are directional — only enter from the matching axis face
                if (step.entryAxis() != pipeAxis) continue;

                // Mark the pipe as spinning
                BlockEntity pipeEntity = level.getBlockEntity(step.pos());
                if (pipeEntity instanceof KineticPipeBlockEntity pipe) {
                    pipe.refreshPoweredTimer(level, step.pos(), state);
                }

                // Continue BFS through both ends of this pipe's axis
                for (Direction dir : Direction.values()) {
                    if (dir.getAxis() != pipeAxis) continue;
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, pipeAxis));
                    }
                }
            } else if (state.getBlock() instanceof KineticReductorBlock) {
                // Reductor is an omnidirectional junction — no axis constraint.
                // Mark it as powered (activates the animated texture).
                BlockEntity reductorBe = level.getBlockEntity(step.pos());
                if (reductorBe instanceof KineticReductorBlockEntity reductor) {
                    reductor.refreshPoweredTimer(level, step.pos(), state);
                }
                // Continue BFS in all six directions so KF can exit on any face.
                for (Direction dir : Direction.values()) {
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, dir.getAxis()));
                    }
                }
            } else {
                // Terminal node — deliver KF if it accepts it
                BlockEntity be = level.getBlockEntity(step.pos());
                if (be instanceof IKineticReceiver receiver) {
                    receiver.addKineticForce(KF_PER_TICK);
                }
            }
        }
    }

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
