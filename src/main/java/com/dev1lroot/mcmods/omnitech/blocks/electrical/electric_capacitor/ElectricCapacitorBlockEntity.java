package com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_capacitor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.gui.ElectricCapacitorMenu;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Block entity for the Electric Capacitor.
 *
 * <p>Implements both {@link IElectricReceiver} (charges from 5 non-front sides)
 * and {@link IElectricSupplier} (discharges from the front face).
 *
 * <p>Stored EU is a float for precise distribution when multiple capacitors are
 * connected to a single engine. ContainerData encodes it as an int (×1 for display,
 * clamped to MAX_EU which fits in a short).
 *
 * <p>Discharge propagation happens every {@value #CLOCK_INTERVAL} ticks to keep
 * BFS overhead low, delivering the accumulated EU burst at once.
 *
 * <p>Capacity: {@value #MAX_EU} EU. Discharge rate: {@value #DISCHARGE_RATE} EU/tick.
 */
public class ElectricCapacitorBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, IElectricSupplier {

    public static final int MAX_EU = 50_000;

    /** EU discharged into the network per tick (effective rate). */
    private static final float DISCHARGE_RATE = 40f;

    /** Propagate electricity every N ticks. */
    private static final int CLOCK_INTERVAL = 5;

    float storedEu = 0f;
    private int clockCounter = 0;

    /** NeoForge-compatible energy handler (transaction-aware). */
    public final CapacitorEnergyHandler energyHandler = new CapacitorEnergyHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) storedEu;
                case 1 -> MAX_EU;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) storedEu = value;
        }
        @Override public int getCount() { return 2; }
    };

    public ElectricCapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_CAPACITOR.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_capacitor");
    }

    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricCapacitorMenu(containerId, inv, this, dataAccess);
    }

    // ── IElectricReceiver ─────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount) {
        float accepted = Math.min(amount, MAX_EU - storedEu);
        if (accepted <= 0f) return 0f;
        storedEu += accepted;
        setChanged();
        return accepted;
    }

    // ── IElectricSupplier ─────────────────────────────────────────────────────

    @Override
    public float getEuSupply() {
        return Math.min(DISCHARGE_RATE, storedEu);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricCapacitorBlockEntity be) {

        boolean wasLit = state.getValue(ElectricCapacitorBlock.LIT);
        boolean isLit  = be.storedEu > MAX_EU / 10f;

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricCapacitorBlock.LIT, isLit), 3);
        }

        if (be.storedEu <= 0f) {
            be.clockCounter = 0;
            return;
        }

        // Discharge every CLOCK_INTERVAL ticks, deliver burst amount
        be.clockCounter++;
        if (be.clockCounter >= CLOCK_INTERVAL) {
            be.clockCounter = 0;
            Direction front = state.getValue(ElectricCapacitorBlock.FACING);
            float burstAmount = Math.min(DISCHARGE_RATE * CLOCK_INTERVAL, be.storedEu);
            float actualDelivered = ElectricNetworkUtil.propagateElectricity(
                    level, pos, burstAmount, new Direction[]{ front });
            if (actualDelivered > 0f) {
                be.storedEu -= actualDelivered;
                if (be.storedEu < 0f) be.storedEu = 0f;
                be.setChanged();
            }
        }
    }

    public ContainerData getContainerData() { return dataAccess; }
    public float getStoredEu()              { return storedEu; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedEu = input.getFloatOr("StoredEu", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("StoredEu", storedEu);
    }

    // ── NeoForge EnergyHandler (inner class) ──────────────────────────────────

    /**
     * Transaction-aware {@link EnergyHandler} backed by this capacitor's {@link #storedEu}.
     */
    public class CapacitorEnergyHandler extends SnapshotJournal<Float> implements EnergyHandler {

        @Override
        protected Float createSnapshot() { return storedEu; }

        @Override
        protected void revertToSnapshot(Float snapshot) { storedEu = snapshot; }

        @Override
        protected void onRootCommit(Float originalState) {
            setChanged();
        }

        @Override
        public long getAmountAsLong()   { return (long) storedEu; }

        @Override
        public long getCapacityAsLong() { return MAX_EU; }

        @Override
        public int insert(int amount, TransactionContext tx) {
            int accepted = (int) Math.min(amount, MAX_EU - storedEu);
            if (accepted > 0) {
                updateSnapshots(tx);
                storedEu += accepted;
            }
            return accepted;
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            int extracted = (int) Math.min(amount, storedEu);
            if (extracted > 0) {
                updateSnapshots(tx);
                storedEu -= extracted;
            }
            return extracted;
        }
    }
}
