package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricEngineMenu;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * Block entity for the Electric Engine.
 *
 * <p>Implements {@link IKineticReceiver} to accept KF from the kinetic network,
 * and {@link IElectricSupplier} to declare its EU output to the electric network.
 *
 * <p>Energy is propagated every {@value #CLOCK_INTERVAL} ticks, delivering
 * {@code EU_PER_TICK * CLOCK_INTERVAL} EU per propagation event to maintain
 * the same effective per-tick rate.
 */
public class ElectricEngineBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, IElectricSupplier {

    /** EU produced per game tick when receiving KF. */
    public static final float EU_PER_TICK = 1f;

    /** Propagate electricity every N ticks to reduce BFS overhead. */
    private static final int CLOCK_INTERVAL = 5;

    /** Ticks before the engine powers down without fresh KF. */
    private static final int POWERED_DECAY_TICKS = 3;

    private int poweredTimer = 0;
    private int clockCounter = 0;

    /** KF units received last network clock (displayed in GUI). */
    private float lastKfAmount = 0f;

    /**
     * ContainerData indices:
     * <ul>
     *   <li>0 – powered (1) / idle (0)</li>
     *   <li>1 – KF received × 100 (fixed-point centi-KF for int transport)</li>
     *   <li>2 – EU/tick output × 10 (fixed-point for int transport)</li>
     * </ul>
     */
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> isPowered() ? 1 : 0;
                case 1 -> (int)(lastKfAmount * 100f);
                case 2 -> isPowered() ? (int)(EU_PER_TICK * 10f) : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 1) lastKfAmount = value / 100f;
        }
        @Override public int getCount() { return 3; }
    };

    public ElectricEngineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_ENGINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_engine");
    }

    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricEngineMenu(containerId, inv, this, dataAccess);
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public boolean addKineticForce(float amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        lastKfAmount = amount;
        setChanged();
        return true;
    }

    @Override
    public float getKfDemand() { return 1.0F; }

    // ── IElectricSupplier ─────────────────────────────────────────────────────

    @Override
    public float getEuSupply() { return isPowered() ? EU_PER_TICK : 0f; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricEngineBlockEntity be) {

        boolean wasLit = state.getValue(ElectricEngineBlock.LIT);

        if (be.poweredTimer > 0) {
            be.poweredTimer--;
        } else {
            if (be.lastKfAmount != 0f) {
                be.lastKfAmount = 0f;
                be.setChanged();
            }
        }

        boolean isLit = be.isPowered();

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricEngineBlock.LIT, isLit), 3);
        }

        if (!isLit) {
            be.clockCounter = 0;
            return;
        }

        // Propagate every CLOCK_INTERVAL ticks, deliver the accumulated EU amount
        be.clockCounter++;
        if (be.clockCounter >= CLOCK_INTERVAL) {
            be.clockCounter = 0;
            Direction front = state.getValue(ElectricEngineBlock.FACING);
            Direction[] outputDirs = outputFaces(front);
            float euBurst = EU_PER_TICK * CLOCK_INTERVAL;
            ElectricNetworkUtil.propagateElectricity(level, pos, euBurst, outputDirs);
        }
    }

    public boolean isPowered()              { return poweredTimer > 0; }
    public ContainerData getContainerData() { return dataAccess; }

    /** Returns the 5 faces that are NOT the KF input face. */
    private static Direction[] outputFaces(Direction front) {
        Direction[] dirs = new Direction[5];
        int i = 0;
        for (Direction d : Direction.values()) {
            if (d != front) dirs[i++] = d;
        }
        return dirs;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        poweredTimer = input.getIntOr("PoweredTimer", 0);
        lastKfAmount = input.getFloatOr("LastKfAmount", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer",  poweredTimer);
        output.putFloat("LastKfAmount", lastKfAmount);
    }
}
