package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.KineticNetworkUtil;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineMenu;
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

/**
 * Block entity for the {@link StirlingEngineBlock}.
 *
 * <p>Receives heat via {@link IHeatReceiver#addHeat} from the adjacent
 * {@link HeaterBlock} (or any other heat source).  Once the internal heat
 * exceeds {@link #MIN_RUNNING_HEAT} and the water tank is non-empty, the
 * engine runs: it propagates Kinetic Force through the pipe network (same BFS
 * as {@link KineticGeneratorBlockEntity}) and consumes
 * {@link #WATER_CONSUMPTION_PER_TICK} mb of water per tick.
 *
 * <p>Water is loaded from buckets placed in {@link #SLOT_WATER_IN};
 * the resulting empty bucket is moved to {@link #SLOT_EMPTY_OUT}.
 */
public class StirlingEngineBlockEntity extends BaseContainerBlockEntity
        implements IHeatReceiver {

    public static final int SLOT_WATER_IN  = 0;
    public static final int SLOT_EMPTY_OUT = 1;
    public static final int SLOT_COUNT     = 2;

    public static final int MAX_HEAT                  = 300;   // °C ceiling
    public static final int MIN_RUNNING_HEAT          = 100;   // °C threshold to start
    public static final int MAX_WATER                 = 10_000; // mb capacity
    public static final int WATER_CONSUMPTION_PER_TICK = 1;   // mb per running tick
    public static final int HEAT_LOSS_INTERVAL        = 10;   // ticks between −1 °C drops
    public static final int KF_PER_TICK               = 1;    // KF delivered per receiver per tick

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int storedHeat    = 0;
    private int storedWater   = 0;   // mb
    private int heatLossTimer = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> storedHeat;
                case 1 -> MAX_HEAT;
                case 2 -> storedWater;
                case 3 -> MAX_WATER;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> storedHeat  = value;
                case 2 -> storedWater = value;
            }
        }
        @Override public int getCount() { return 4; }
    };

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.STIRLING_ENGINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.stirling_engine");
    }

    @Override
    protected NonNullList<ItemStack> getItems()              { return items; }
    @Override
    protected void setItems(NonNullList<ItemStack> items)    { this.items = items; }
    @Override
    public int getContainerSize()                            { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new StirlingEngineMenu(containerId, inv, this, dataAccess);
    }

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public boolean addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return false;
        storedHeat = Math.min(MAX_HEAT, storedHeat + celsius);
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            StirlingEngineBlockEntity be) {
        boolean wasRunning = be.isRunning();

        // ── Fill tank from water bucket in input slot ──────────────────────────
        ItemStack waterBucket = be.items.get(SLOT_WATER_IN);
        if (!waterBucket.isEmpty() && waterBucket.is(Items.WATER_BUCKET)
                && be.storedWater <= MAX_WATER - 1000) {
            ItemStack output = be.items.get(SLOT_EMPTY_OUT);
            boolean canOutput = output.isEmpty()
                    || (output.is(Items.BUCKET) && output.getCount() < output.getMaxStackSize());
            if (canOutput) {
                be.storedWater += 1000;
                be.items.set(SLOT_WATER_IN, ItemStack.EMPTY);
                if (output.isEmpty()) {
                    be.items.set(SLOT_EMPTY_OUT, new ItemStack(Items.BUCKET));
                } else {
                    output.grow(1);
                }
            }
        }

        // ── Natural heat dissipation ───────────────────────────────────────────
        if (be.storedHeat > 0) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                be.storedHeat--;
            }
        } else {
            be.heatLossTimer = 0;
        }

        // ── Run the engine ─────────────────────────────────────────────────────
        if (be.isRunning()) {
            // Produce KF via the same BFS used by the KineticGenerator
            KineticNetworkUtil.propagateKineticForce(level, pos, KF_PER_TICK);
            // Consume water
            be.storedWater = Math.max(0, be.storedWater - WATER_CONSUMPTION_PER_TICK);
        }

        // ── Sync LIT blockstate ────────────────────────────────────────────────
        if (wasRunning != be.isRunning()) {
            level.setBlock(pos, state.setValue(StirlingEngineBlock.LIT, be.isRunning()), 3);
        }

        be.setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** @return true when the engine is actively producing KF this tick. */
    public boolean isRunning() {
        return storedHeat > MIN_RUNNING_HEAT && storedWater > 0;
    }

    public int getStoredHeat()    { return storedHeat; }
    public int getStoredWater()   { return storedWater; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        storedHeat  = input.getIntOr("StoredHeat",  0);
        storedWater = input.getIntOr("StoredWater", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("StoredHeat",  storedHeat);
        output.putInt("StoredWater", storedWater);
    }
}
